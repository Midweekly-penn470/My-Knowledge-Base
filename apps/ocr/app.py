from __future__ import annotations

import io
import logging
from functools import lru_cache

import fitz
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from PIL import Image
from rapidocr_onnxruntime import RapidOCR

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("mykb-ocr")

app = FastAPI(title="mykb-ocr", version="0.1.0")

PDF_CONTENT_TYPES = {"application/pdf"}
IMAGE_CONTENT_TYPES = {
    "image/png",
    "image/jpeg",
    "image/jpg",
    "image/webp",
    "image/bmp",
    "image/tiff",
}


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "UP"}


@app.post("/api/v1/ocr/extract")
async def extract(file: UploadFile = File(...)) -> dict[str, str]:
    if not file.filename:
        raise HTTPException(status_code=400, detail="filename is required")

    content = await file.read()
    if not content:
        raise HTTPException(status_code=400, detail="file must not be empty")

    content_type = (file.content_type or "").lower().strip()
    logger.info(
        "OCR request received. filename=%s content_type=%s size=%s",
        file.filename,
        content_type,
        len(content),
    )

    try:
        if content_type in PDF_CONTENT_TYPES or file.filename.lower().endswith(".pdf"):
            text, engine = extract_from_pdf(content)
        elif content_type in IMAGE_CONTENT_TYPES:
            text, engine = extract_from_image(content)
        else:
            raise HTTPException(status_code=400, detail=f"unsupported content type: {content_type or 'unknown'}")
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        logger.exception("OCR extraction failed. filename=%s", file.filename)
        raise HTTPException(status_code=500, detail="ocr extraction failed") from exc

    normalized = normalize_text(text)
    if not normalized:
        raise HTTPException(status_code=422, detail="no text extracted from file")

    return {"text": normalized, "engine": engine}


@lru_cache(maxsize=1)
def get_engine() -> RapidOCR:
    return RapidOCR()


def extract_from_pdf(content: bytes) -> tuple[str, str]:
    document = fitz.open(stream=content, filetype="pdf")
    extracted_pages = [page.get_text("text") or "" for page in document]
    extracted_text = normalize_text("\n".join(extracted_pages))
    if extracted_text:
        return extracted_text, "pymupdf-text"

    ocr_pages: list[str] = []
    for page in document:
        pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
        image = Image.frombytes("RGB", [pixmap.width, pixmap.height], pixmap.samples)
        ocr_pages.append(run_rapidocr(image))
    return "\n".join(ocr_pages), "rapidocr-pdf"


def extract_from_image(content: bytes) -> tuple[str, str]:
    image = Image.open(io.BytesIO(content)).convert("RGB")
    return run_rapidocr(image), "rapidocr-image"


def run_rapidocr(image: Image.Image) -> str:
    result, _ = get_engine()(np.array(image))
    if not result:
        return ""
    lines = []
    for item in result:
        if len(item) < 2:
            continue
        text = str(item[1]).strip()
        if text:
            lines.append(text)
    return "\n".join(lines)


def normalize_text(text: str) -> str:
    return "\n".join(line.strip() for line in text.splitlines() if line.strip()).strip()
