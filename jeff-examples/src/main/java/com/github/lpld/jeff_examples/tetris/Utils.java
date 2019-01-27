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

  public static <T> Option<List<T>> sequenceOptSeq(Seq<Option<T>> str) {

    return str.foldRight(
        some(List.empty()),
        (elem, acc) -> elem.flatMap(e -> acc.map(a -> a.prepend(e)))
    );
  }

  /**
   * Create a function that applies function f `times` times
   */
  public static <A> Function<A, A> multF(Function<A, A> f, int times) {
    return List.range(0, times).foldLeft(Function.identity(), (c, t) -> c.andThen(f));
  }
}
