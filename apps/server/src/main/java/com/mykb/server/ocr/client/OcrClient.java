package com.mykb.server.ocr.client;

public interface OcrClient {

  OcrExtractResult extractText(String filename, String contentType, byte[] fileBytes);
}
