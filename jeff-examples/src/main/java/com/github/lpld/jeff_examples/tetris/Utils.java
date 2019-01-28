package com.github.lpld.jeff_examples.tetris;

import java.util.function.Function;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Option;

import static io.vavr.control.Option.some;

/**
 * @author leopold
 * @since 2019-01-26
 */
public class Utils {

  /**
   * Sequence operation for Vavr's Seq and Option.
   */
  public static <T> Option<List<T>> sequenceSeqOpt(Seq<Option<T>> str) {

    return str.foldRight(
        some(List.empty()),
        (elem, acc) -> elem.flatMap(e -> acc.map(a -> a.prepend(e)))
    );
  }

  /**
   * Create a function that applies function f {@code nTimes} times
   */
  public static <A> Function<A, A> multF(Function<A, A> f, int nTimes) {
    return List.range(0, nTimes).foldLeft(Function.identity(), (c, t) -> c.andThen(f));
  }
}
