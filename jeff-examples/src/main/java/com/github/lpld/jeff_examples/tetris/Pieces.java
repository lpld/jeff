package com.github.lpld.jeff_examples.tetris;

import com.github.lpld.jeff.Stream;

import io.vavr.collection.List;

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

  /**
   * A list of all possible pieces.
   */
  public static final List<RectRegion> allPossiblePieces =
      // mirror
      List.of(0, 1).flatMap(
          // rotate
          m -> List.of(0, 1, 2, 3).flatMap(
              // all shapes:
              r -> List.of(O, I, J, S, T).map(
                  // apply the transformation
                  p -> multF(RectRegion::mirror, m).andThen(multF(RectRegion::rotate, r))
                      .apply(p)
              )
          )
      );

  /**
   * Infinite stream of pieces.
   */
  public static final Stream<RectRegion> infiniteStream = Stream.ofAll(allPossiblePieces).repeat();

}
