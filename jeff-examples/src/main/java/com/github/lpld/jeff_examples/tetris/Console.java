package com.github.lpld.jeff_examples.tetris;

import com.github.lpld.jeff.IO;
import com.github.lpld.jeff.Stream;
import com.github.lpld.jeff.data.Unit;

import org.fusesource.jansi.Ansi;

import io.vavr.collection.Seq;

import static com.github.lpld.jeff.IO.IO;

/**
 * @author leopold
 * @since 2019-01-27
 */
public class Console {

  private static final Ansi ansi = Ansi.ansi();

  public static IO<Unit> eraseScreen = IO(() -> ansi.eraseScreen()).toUnit();

  public static IO<Unit> cursor(int x, int y) {
    return IO(() -> ansi.cursor(x, y)).toUnit();
  }

  public static IO<Unit> print(String s) {
    return IO(() -> ansi.a(s)).toUnit();
  }

  public static IO<Unit> printAt(int x, int y, String s) {
    return cursor(x, y).chain(print(s));
  }

  public static IO<Unit> printLinesAt(int x, int y, Seq<String> lines) {
    return Stream.ofAll(lines)
        .zipWithIndex()
        .mapEval(lineWithIdx -> printAt(x + lineWithIdx._2, y, lineWithIdx._1))
        .drain();
  }

}
