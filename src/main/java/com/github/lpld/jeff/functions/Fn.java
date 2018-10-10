package com.github.lpld.jeff.functions;

/**
 * @author leopold
 * @since 4/10/18
 */
@FunctionalInterface
public interface Fn<T, R> {

  R ap(T t) throws Throwable;

  default <R2> Fn<T, R2> andThen(Fn<R, R2> f2) {
    return t -> f2.ap(ap(t));
  }

  static <T> Fn<T, T> id() {
    return t -> t;
  }
}
