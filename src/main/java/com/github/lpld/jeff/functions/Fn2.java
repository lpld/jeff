package com.github.lpld.jeff.functions;

/**
 * @author leopold
 * @since 4/10/18
 */
@FunctionalInterface
public interface Fn2<T1, T2, R> {

  R ap(T1 t1, T2 t2);

  default Fn2<T2, T1, R> swap() {
    return (t2, t1) -> ap(t1, t2);
  }

  default <R2> Fn2<T1, T2, R2> andThen(Fn<R, R2> f2) {
    return (t1, t2) -> f2.ap(ap(t1, t2));
  }

}
