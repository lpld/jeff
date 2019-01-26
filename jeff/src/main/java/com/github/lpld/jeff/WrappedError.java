package com.github.lpld.jeff;

/**
 * @author leopold
 * @since 5/10/18
 */
public class WrappedError extends RuntimeException {

  private WrappedError(Throwable t) {
    super(t);
  }

  public static <T> T throwWrapped(Throwable t) {
    if (t instanceof RuntimeException) {
      throw ((RuntimeException) t);
    }

    if (t instanceof Error) {
      throw ((Error) t);
    }

    throw new WrappedError(t);
  }
}
