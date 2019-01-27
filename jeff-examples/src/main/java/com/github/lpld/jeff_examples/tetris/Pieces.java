package com.github.lpld.jeff_examples.tetris;

import com.github.lpld.jeff.Stream;

import io.vavr.collection.List;
import io.vavr.collection.Seq;

import static com.github.lpld.jeff_examples.tetris.Utils.multF;

/**
 * @author leopold
 * @since 2019-01-27
 */
public class Pieces {

  public static final RectRegion O =
      RectRegion.parse("XX",
                       "XX");

  public static final RectRegion I =
      RectRegion.parse("X",
                       "X",
                       "X",
                       "X");

  public static final RectRegion J =
      RectRegion.parse("X.",
                       "X.",
                       "XX");

  public static final RectRegion S =
      RectRegion.parse(".XX",
                       "XX.");

  public static final RectRegion T =
      RectRegion.parse("XXX",
                       ".X.");

  public static final Seq<RectRegion> allPossiblePieces = List.of(0, 1).flatMap(
      // mirror
      m -> List.of(0, 1, 2, 3).flatMap(
          // rotate
          r -> List.of(O, I, J, S, T).map(
              p -> multF(RectRegion::mirror, m).andThen(multF(RectRegion::rotate, r))
                  .apply(p)
          )
      )
  );

  // no randomness for now
  public static final Stream<RectRegion> infiniteStream = Stream.ofAll(allPossiblePieces).repeat();

}
