package com.github.lpld.jeff.functions;

/**
 * @author leopold
 * @since 11/10/18
 */
public interface Xn<T, R> {

  R ap(T t) throws Throwable;
}
