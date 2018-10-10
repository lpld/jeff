package com.github.lpld.jeff.functions;

/**
 * @author leopold
 * @since 4/10/18
 */
@FunctionalInterface
public interface Fs<T, R> extends Fn<T, R> {

  R ap(T t);

  static <T> Fs<T, T> id() {
    return t -> t;
  }
}
