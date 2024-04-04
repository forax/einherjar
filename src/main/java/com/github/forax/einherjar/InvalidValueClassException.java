package com.github.forax.einherjar;

public final class InvalidValueClassException extends RuntimeException {
  public InvalidValueClassException(String message) {
    super(message);
  }

  public InvalidValueClassException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidValueClassException(Throwable cause) {
    super(cause);
  }
}
