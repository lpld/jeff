package com.github.lpld.jeff;

import com.github.lpld.jeff.functions.Fn2;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author leopold
 * @since 29/10/18
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IOFunctions {

  public static <T1, T2, T3> IO<T3> flatMap2(IO<T1> io1, IO<T2> io2, Fn2<T1, T2, IO<T3>> f) {
    return io1.flatMap(t1 -> io2.flatMap(t2 -> f.ap(t1, t2)));
  }
}
