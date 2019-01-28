package com.github.lpld.jeff_examples.tetris;

import com.github.lpld.jeff.IO;
import com.github.lpld.jeff.Stream;
import com.github.lpld.jeff.data.Unit;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import io.vavr.collection.Seq;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.github.lpld.jeff.IO.IO;

/**
 * Functional wrapper over jansi console.
 *
 * @author leopold
 * @since 2019-01-27
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AnsiOut {

  private final Ansi ansi = Ansi.ansi();

  public IO<Unit> eraseScreen() {
    return IO(() -> ansi.eraseScreen()).toUnit();
  }

  public IO<Unit> cursor(int x, int y) {
    return IO(() -> ansi.cursor(x, y)).toUnit();
  }

  public IO<Unit> print(String s) {
    return IO(() -> ansi.a(s)).toUnit();
  }

  public IO<Unit> printAt(int x, int y, String s) {
    return cursor(x, y).chain(print(s));
  }

  public IO<Unit> printLinesAt(int x, int y, Seq<String> lines) {
    return Stream.ofAll(lines)
        .zipWithIndex()
        .mapEval(lineWithIdx -> printAt(x + lineWithIdx._2, y, lineWithIdx._1))
        .drain();
  }

  public IO<Unit> flush() {
    return IO.delay(() -> AnsiConsole.out.println(ansi));
  }

  public static IO<AnsiOut> create = IO.delay(AnsiOut::new);
}
