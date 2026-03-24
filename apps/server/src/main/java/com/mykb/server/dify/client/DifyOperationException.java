package com.mykb.server.dify.client;

public class DifyOperationException extends RuntimeException {

  private final String code;

  public DifyOperationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public DifyOperationException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
