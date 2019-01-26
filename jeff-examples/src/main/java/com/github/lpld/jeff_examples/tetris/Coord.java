package com.github.lpld.jeff_examples.tetris;

import lombok.RequiredArgsConstructor;

/**
 * 2D coordinate.
 *
 * @author leopold
 * @since 2019-01-26
 */
@RequiredArgsConstructor
public class Coord {

  final int x;
  final int y;

  public Coord rowDown() {
    return new Coord(x + 1, y);
  }

  public Coord left() {
    return new Coord(x, y - 1);
  }

  public Coord right() {
    return new Coord(x, y + 1);
  }
}
