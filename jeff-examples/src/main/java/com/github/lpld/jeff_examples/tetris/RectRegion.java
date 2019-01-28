package com.github.lpld.jeff_examples.tetris;

import com.github.lpld.jeff.data.Pr;

import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import lombok.Getter;

import static com.github.lpld.jeff_examples.tetris.Utils.sequenceSeqOpt;
import static io.vavr.API.None;
import static io.vavr.API.Some;

/**
 * Rectangular region that consists of empty or filled cells.
 *
 * @author leopold
 * @since 2019-01-26
 */
public class RectRegion {

  @Getter
  private final Array<Array<Boolean>> cells;
  private final int height;
  private final int width;

  public RectRegion(Array<Array<Boolean>> cells) {
    this.cells = cells;
    this.height = cells.size();
    this.width = cells.headOption().map(Array::size).getOrElse(0);
  }

  /**
   * Create a region from string lines.
   */
  public static RectRegion parse(String... lines) {

    final Array<Array<Boolean>> cells =
        Stream.of(lines)
            .map(String::trim)
            .map(line -> Stream
                .ofAll(line.toCharArray())
                .map(cell -> cell == 'X')
                .toArray()
            ).toArray();

    return new RectRegion(cells);
  }

  /**
   * Create empty region of given dimensions.
   */
  public static RectRegion ofSize(int height, int width) {
    return new RectRegion(Array.fill(height, Array.fill(width, false)));
  }

  public int height() {
    return height;
  }

  public int width() {
    return width;
  }

  /**
   * Get boolean value of a cell at given coordinates.
   */
  public boolean get(int i, int j) {
    return cells.get(i).get(j);
  }

  // array of vertical indices: [0 ... height]
  private Array<Integer> vertIndices() {
    return Array.range(0, height);
  }

  // array of horizontal indices: [0 ... weight]
  private Array<Integer> horIndices() {
    return Array.range(0, width);
  }

  /**
   * Rotate this region clockwise.
   * <pre>
   * {@code
   *   .XX.    ...
   *   .X.. -> XXX
   *   .X..    ..X
   *           ...
   * }
   * </pre>
   */
  public RectRegion rotate() {
    if (height == 0) {
      return this;
    }

    return new RectRegion(
        horIndices().map(
            j -> vertIndices().reverse().map(
                i -> get(i, j)))
    );
  }

  /**
   * Mirror this region vertically.
   * <pre>
   * {@code
   *   .XX.    .XX.
   *   .X.. -> ..X.
   *   .X..    ..X.
   * }
   * </pre>
   */
  public RectRegion mirror() {
    if (height == 0) {
      return this;
    }

    return new RectRegion(
        vertIndices().map(
            i -> horIndices().reverse().map(
                j -> get(i, j)))
    );
  }

  /**
   * Remove filled rows and add empty rows on top of the region instead of them.
   */
  public Option<Pr<RectRegion, Integer>> clearFilledRows() {

    final Array<Array<Boolean>> newCells = cells.filter(row -> row.exists(b -> !b));

    final int cleared = height - newCells.size();

    if (cleared == 0) {
      return None();
    }

    return Some(Pr.of(
        new RectRegion(Array.fill(cleared, Array.fill(width, false)).appendAll(newCells)),
        cleared
    ));
  }

  /**
   * Try "inject" a new {@code injectee} region into this region at coordinates {@code coord}.
   * "Injection" means that the resulting region will be the same size as "this" region and
   * that the corresponding cells of both regions will be combined (if possible). Two cells can be
   * combined if at least one of them is empty (false). If both cells are non-empty (true),
   * it means that there's no room for {@code injectee} region. In this case this function will
   * return {@link Option.None}. Otherwise it will return new {@code RectRegion} that
   * is the result of the injection.
   */
  public Option<RectRegion> inject(RectRegion injectee, Coord coord) {
    // Check the boundaries. If we try to inject a region outside of
    // "this" region, we will return Empty

    if (coord.x < 0 || coord.y < 0 ||
        coord.x + injectee.height > this.height ||
        coord.y + injectee.width > this.width) {
      return None();
    }

    // Combine cells of this RectRegion at coordinates (x, y) with
    // corresponding cells of the injectee region.
    final Stream<Stream<Option<Boolean>>> combined =
        vertIndices().toStream().map(
            x -> horIndices().toStream().map(
                y -> x < coord.x || y < coord.y ||
                     x >= coord.x + injectee.height ||
                     y >= coord.y + injectee.width

                     ? Some(get(x, y))
                     : combineValues(get(x, y), injectee.get(x - coord.x, y - coord.y))
            )
        );

    return sequenceSeqOpt(combined.map(Utils::sequenceSeqOpt))
        .map(newCells -> new RectRegion(newCells.map(List::toArray).toArray()));
  }

  // Combine two values only if they are not both true.
  private static Option<Boolean> combineValues(boolean v1, boolean v2) {
    if (v1 && v2) {
      return None();
    }

    return Some(v1 || v2);
  }
}
