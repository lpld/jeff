package com.github.lpld.jeff.functions;

/**
 * @author leopold
 * @since 4/10/18
 */
@FunctionalInterface
public interface Fn2<T1, T2, R> {

  R apply(T1 t1, T2 t2) throws Throwable;
}
