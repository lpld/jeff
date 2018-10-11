package com.github.lpld.jeff.functions;

/**
 * @author leopold
 * @since 4/10/18
 */
@FunctionalInterface
public interface Fn0<T> {

  T ap() throws Throwable;

  default <T2> Fn0<T2> andThen(Fn<T, T2> f2) {
    return () -> f2.ap(this.ap());
  }
}
