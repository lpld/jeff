package com.github.lpld.jeff;

import com.github.lpld.jeff.functions.Fn2;

import java.util.Optional;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author leopold
 * @since 29/10/18
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IOFunctions {

  public static <T1, T2, T3> IO<T3> map2(IO<T1> io1, IO<T2> io2, Fn2<T1, T2, T3> f) {
    return flatMap2(io1, io2, f.andThen(IO::pure));
  }

  public static <T1, T2, T3> IO<T3> flatMap2(IO<T1> io1, IO<T2> io2, Fn2<T1, T2, IO<T3>> f) {
    return io1.flatMap(t1 -> io2.flatMap(t2 -> f.ap(t1, t2)));
  }

  public static <T1, T2, T3> Optional<T3> map2Opt(Optional<T1> o1, Optional<T2> o2, Fn2<T1, T2, T3> f) {
    return o1.flatMap(t1 -> o2.map(t2 -> f.ap(t1, t2)));
  }
}
