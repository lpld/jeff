package com.github.lpld.jeff.functions;

/**
 * @author leopold
 * @since 4/10/18
 */
@FunctionalInterface
public interface Fs2<T1, T2, R> extends Fn2<T1, T2, R> {

  R ap(T1 t1, T2 t2);

  default Fs2<T2, T1, R> swap() {
    return (t2, t1) -> ap(t1, t2);
  }
}
