package io.github.kurobako.agave;

public final class UncheckedExecutionException extends RuntimeException {

  UncheckedExecutionException(String message, Throwable cause) {
    super(message, cause);
  }

  UncheckedExecutionException(Throwable cause) {
    super(cause);
  }

  UncheckedExecutionException(String message) {
    super(message);
  }
}
