package com.github.lpld.jeff_examples.tetris;

import com.github.lpld.jeff.data.Pr;

import io.vavr.collection.Array;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import io.vavr.control.Option;
import lombok.Getter;

import static com.github.lpld.jeff_examples.tetris.Utils.sequenceOptSeq;
import static io.vavr.API.None;
import static io.vavr.API.Some;

/**
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

  public static RectRegion ofSize(int height, int width) {
    return new RectRegion(Array.fill(height, Array.fill(width, false)));
  }

  public int height() {
    return height;
  }

  public int width() {
    return width;
  }

  public boolean get(int i, int j) {
    return cells.get(i).get(j);
  }

  private Array<Integer> vertIndices() {
    return Array.range(0, height);
  }

  private Array<Integer> horIndices() {
    return Array.range(0, width);
  }

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

    return sequenceOptSeq(combined.map(Utils::sequenceOptSeq))
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
