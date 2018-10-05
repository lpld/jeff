package com.github.lpld.jeff.functions;

import com.github.lpld.jeff.WrappedError;

/**
 * @author leopold
 * @since 4/10/18
 */
@FunctionalInterface
public interface F0<T> {

  T get() throws Throwable;

  default T getUnsafe() {
    try {
      return get();
    } catch (Throwable throwable) {
      return WrappedError.throwWrapped(throwable);
    }
  }
}
