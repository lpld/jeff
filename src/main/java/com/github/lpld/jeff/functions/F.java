package com.github.lpld.jeff.functions;

import com.github.lpld.jeff.WrappedError;

/**
 * @author leopold
 * @since 4/10/18
 */
@FunctionalInterface
public interface F<T, R> {

  R apply(T t) throws Throwable;

  default <R2> F<T, R2> andThen(F<R, R2> f2) {
    return t -> f2.apply(apply(t));
  }

  default R applyUnsafe(T t) {
    try {
      return apply(t);
    } catch (Throwable throwable) {
      return WrappedError.throwWrapped(throwable);
    }
  }
}
