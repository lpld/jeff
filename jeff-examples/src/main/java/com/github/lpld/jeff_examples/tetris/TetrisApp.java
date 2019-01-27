package com.github.lpld.jeff_examples.tetris;

import com.github.lpld.jeff.IO;
import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff_examples.tetris.Tetris.GameState;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.vavr.collection.List;
import io.vavr.collection.Seq;

/**
 * @author leopold
 * @since 2019-01-27
 */
public class TetrisApp {

  private static final int HEIGHT = 15;
  private static final int WIDTH = 15;

  public static void main(String[] args) throws IOException {
    System.out.println("Starting tetris app!");
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    final Tetris tetris = new Tetris(HEIGHT, WIDTH, new UserInput().getMoves(), scheduler);

    final IO<Unit> drawTheGame = Console.eraseScreen.chain(
        tetris.gameStates()
            .mapEval(u -> IO.unit)
            .drain()
    );

    drawTheGame.run();
    scheduler.shutdown();
  }

  private static IO<Unit> printState(GameState state) {
    System.out.println("Printing");

    return Console.printLinesAt(1, 1, state.getFieldWithPiece().getCells().map(TetrisApp::showRow))
        .chain(Console.printAt(2, WIDTH + 2, "Score: " + state.getScore()))
        .chain(Console.printAt(3, WIDTH + 2, "Level: " + state.getLevel()))
        .chain(Console.printAt(5, WIDTH + 2, "Next: "))
        .chain(Console.printLinesAt(7, WIDTH + 3, List.fill(4, "...."))) // erase
        .chain(Console.printLinesAt(7, WIDTH + 3, state.getPiecesSource()._1.getCells().map(TetrisApp::showRow)))

        // intentionally adding extra space in the end to clear previous output:
        .chain(
            Console.printAt(12, WIDTH + 2, "Lines left: " + (Tetris.LINES_PER_LEVEL - state.getLinesCleared()) + " "))
        .chain(Console.cursor(HEIGHT + 1, 0));
  }

  private static String showRow(Seq<Boolean> row) {
    return row.map(cell -> cell ? '\u25A0' : '.').mkString();
  }

}
