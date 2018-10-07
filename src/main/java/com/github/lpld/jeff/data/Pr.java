package com.github.lpld.jeff.data;

import lombok.RequiredArgsConstructor;

/**
 * Product of types T1 and T2
 *
 * @author leopold
 * @since 7/10/18
 */
@RequiredArgsConstructor
public final class Pr<T1, T2> {

  public final T1 _1;
  public final T2 _2;

  public static <T1, T2> Pr<T1, T2> of(T1 t1, T2 t2) {
    return new Pr<>(t1, t2);
  }

  public T1 _1() {
    return _1;
  }

  public T2 _2() {
    return _2;
  }

  @Override
  public String toString() {
    return "P(" + _1 + "," + _2 + ")";
  }
}
