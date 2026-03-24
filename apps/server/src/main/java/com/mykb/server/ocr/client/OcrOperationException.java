package com.mykb.server.ocr.client;

public class OcrOperationException extends RuntimeException {

  public OcrOperationException(String message) {
    super(message);
  }

  public OcrOperationException(String message, Throwable cause) {
    super(message, cause);
  }
}
