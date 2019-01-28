package com.github.lpld.jeff_examples.tetris;

import com.github.lpld.jeff.IO;
import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff_examples.tetris.Tetris.GameState;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
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

    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    final ExecutorService userInputExecutor = Executors.newSingleThreadExecutor();

    try {
      // input needs an executor, because it uses blocking IO.
      final PlayerInput playerInput = new PlayerInput(userInputExecutor);

      final Tetris tetris = new Tetris(HEIGHT, WIDTH, playerInput.getInteractions(), scheduler);

      eraseScreen
          // taking the stream of game states
          .chain(tetris.gameStates()
                     // and printing each of them
                     .mapEval(TetrisApp::printState)
                     .drain()
          ).run();

    } finally {
      scheduler.shutdown();
      userInputExecutor.shutdown();
    }
  }

  private static IO<Unit> eraseScreen = AnsiOut.create.then(AnsiOut::eraseScreen).flatMap(AnsiOut::flush);

  private static IO<Unit> printState(GameState state) {

    return AnsiOut.create
        .then(a -> a.printLinesAt(1, 1, state.getFieldWithPiece().getCells().map(TetrisApp::showRow)))

        .then(a -> a.printAt(2, WIDTH + 2, "Score: " + state.getScore()))
        .then(a -> a.printAt(3, WIDTH + 2, "Level: " + state.getLevel()))
        .then(a -> a.printAt(5, WIDTH + 2, "Next: "))
        .then(a -> a.printLinesAt(7, WIDTH + 3, List.fill(4, "...."))) // erase
        .then(a -> a.printLinesAt(7, WIDTH + 3, state.getPiecesSource()._1.getCells().map(TetrisApp::showRow)))

        // intentionally adding extra space in the end to clear previous output:
        .then(a -> a.printAt(12, WIDTH + 2, "Lines left: " + (Tetris.LINES_PER_LEVEL - state.getLinesCleared()) + " "))
        .then(a -> a.cursor(HEIGHT + 1, 0))
        .flatMap(AnsiOut::flush);
  }

  private static String showRow(Seq<Boolean> row) {
    return row.map(cell -> cell ? '\u25A0' : '.').mkString();
  }

}
