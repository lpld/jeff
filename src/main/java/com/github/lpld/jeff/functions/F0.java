package com.github.lpld.jeff.functions;

/**
 * @author leopold
 * @since 4/10/18
 */
@FunctionalInterface
public interface F0<T> {

  T get() throws Throwable;

}
