package com.github.lpld.jeff_examples.tetris;

import com.github.lpld.jeff.IO;
import com.github.lpld.jeff.Stream;
import com.github.lpld.jeff.data.Pr;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Wither;

import static io.vavr.API.None;
import static io.vavr.API.Some;

/**
 * @author leopold
 * @since 2019-01-27
 */
public class Tetris {


  public enum Move {ROTATE, RIGHT, LEFT, DOWN}

  public enum Status {ACTIVE, PAUSED, OVER}

  public enum EventType {TICK, USER_MOVE}

  @RequiredArgsConstructor
  public static class Event {

    public final EventType type;
    public final Move userMove;

    static Event tick() {
      return new Event(EventType.TICK, null);
    }

    static Event userAction(Move move) {
      return new Event(EventType.USER_MOVE, move);
    }
  }

  @Getter
  @Wither
  @RequiredArgsConstructor
  public static class GameState {

    private final Status status;
    private final RectRegion field;
    private final RectRegion fieldWithPiece;
    private final Option<Pr<RectRegion, Coord>> activePiece;
    private final Pr<RectRegion, Stream<RectRegion>> piecesSource;
    private final int score;
    private final int level;
    private final int linesCleared;
  }

  public Tetris(int height, int width,
                Stream<Move> interactions, ScheduledExecutorService scheduler) {
    this.height = height;
    this.width = width;
    this.interactions = interactions;
    this.scheduler = scheduler;
    this.start = new Coord(0, width / 2 - 1);
  }

  private final int height;
  private final int width;
  private final Stream<Move> interactions;
  private final ScheduledExecutorService scheduler;
  private final Coord start;

  public Stream<GameState> gameStates() {
    final RectRegion emptyField = RectRegion.ofSize(height, width);

    // Two sources of events:
    // 1. Regular ticks
    final Stream<Event> ticks = Stream.tick(scheduler, 500).map(t -> Event.tick());

    final Stream<Event> userMoves = interactions.map(Event::userAction);

    final Stream<Event> allEvents = ticks.merge(scheduler, userMoves);

    allEvents.mapEval(e -> IO.delay(() -> System.out.println(e.type)))
        .drain().run();

    final GameState initial = new GameState(
        Status.ACTIVE,
        emptyField,
        emptyField,
        None(),
        pullNextPiece(Pieces.infiniteStream),
        0,
        1,
        0);

    final Stream<GameState> states = allEvents.scanLeft(initial, this::nextState);

    return states.takeWhile(s -> s.status != Status.OVER);
  }

  private GameState nextState(GameState state, Event event) {
    System.out.println("Next");
    switch (event.type) {
      case TICK:
        return state.activePiece
            .flatMap(ap -> injectExisting(state, ap._1, ap._2.rowDown()))
            .getOrElse(() -> activateNew(state));

      default: // user move

        return state.activePiece.flatMap(ap -> {

          final RectRegion piece = ap._1;
          final Coord at = ap._2;

          switch (event.userMove) {
            case LEFT:
              return injectExisting(state, piece, at.left());
            case RIGHT:
              return injectExisting(state, piece, at.right());
            case DOWN:
              return moveDown(state, piece, at);
            default: // rotate
              final Pr<RectRegion, Coord> rotated = rotate(piece, at);
              return injectExisting(state, rotated._1, rotated._2);
          }
        }).getOrElse(state);
    }
  }

  private Option<GameState> moveDown(GameState state, RectRegion piece, Coord at) {
    // trying to shift the piece (downInterval + 1) rows down:
    final List<Pr<RectRegion, Coord>> shifts =
        Stream.unfold(at, prevCoord -> {
          final Coord newCoord = prevCoord.rowDown();
          return state.field
              .inject(piece, newCoord)
              .map(f -> Pr.of(Pr.of(f, newCoord), newCoord))
              .toJavaOptional();
        })
            .take(DOWN_INTERVAL + 1)
            .foldRight(List.<Pr<RectRegion, Coord>>empty(), (el, l) -> l.prepend(el))
            .run();

    if (shifts.length() == DOWN_INTERVAL + 1) {
      // if succeeded, then the piece is still active. shifting it `downInterval` rows down:
      final Pr<RectRegion, Coord> shifted = shifts.get(DOWN_INTERVAL - 1);
      return Some(
          state
              .withFieldWithPiece(shifted._1)
              .withActivePiece(Some(Pr.of(piece, shifted._2))));
    } else {
      // otherwise, taking the last shift position and making the piece inactive:
      return shifts.lastOption().map(
          shifted -> state
              .withField(shifted._1)
              .withFieldWithPiece(shifted._1)
              .withActivePiece(None())
      );
    }
  }

  private GameState activateNew(GameState state) {
    return checkFilledRows(state).getOrElse(injectNew(state));
  }

  private Option<GameState> checkFilledRows(GameState state) {

    return state.fieldWithPiece.clearFilledRows()
        .map(res -> {
          final RectRegion newField = res._1;
          final int clearedCount = res._2;

          final int newScore = state.score + scoreFor(clearedCount);
          final int linesCleared = state.linesCleared + clearedCount;
          final boolean levelUp = linesCleared >= LINES_PER_LEVEL;
          final int level = levelUp ? state.level + 1 : state.level;

          return state
              .withField(newField)
              .withFieldWithPiece(newField)
              .withScore(newScore)
              .withLinesCleared(levelUp ? 0 : linesCleared)
              .withLevel(level);
        });
  }

  private static int scoreFor(int cleared) {
    switch (cleared) {
      case 1:
        return 100;
      case 2:
        return 400;
      case 3:
        return 800;
      default:
        return 1200;
    }
  }

  private Pr<RectRegion, Coord> rotate(RectRegion piece, Coord coord) {
    final int diff = (piece.height() - piece.width()) / 2;
    return Pr.of(piece.rotate(), new Coord(coord.x + diff, coord.y - diff));
  }

  private GameState injectNew(GameState state) {
    final Pr<RectRegion, Stream<RectRegion>> newSource = pullNextPiece(state.piecesSource._2);

    return tryInject(state.fieldWithPiece, state.piecesSource._1, start, newSource, state)
        .getOrElse(state
                       .withStatus(Status.OVER)
                       .withField(state.fieldWithPiece)
                       .withActivePiece(None())
        );
  }

  private Option<GameState> injectExisting(GameState state, RectRegion piece, Coord at) {
    return tryInject(state.field, piece, at, state.piecesSource, state);
  }

  private Option<GameState> tryInject(RectRegion field, RectRegion piece, Coord at,
                                      Pr<RectRegion, Stream<RectRegion>> piecesSource,
                                      GameState state) {
    return field.inject(piece, at)
        .map(result -> state
            .withField(field)
            .withFieldWithPiece(result)
            .withActivePiece(Some(Pr.of(piece, at)))
            .withPiecesSource(piecesSource)
        );
  }

  private static Pr<RectRegion, Stream<RectRegion>> pullNextPiece(Stream<RectRegion> pieces) {
    // pieces is actually a pure stream (with no side-effects), so we can safely call 'run' here
    // todo: but can we define this on type level?
    return pieces.split().map(Optional::get).run();
  }

  public static final int LINES_PER_LEVEL = 10;
  public static final int DOWN_INTERVAL = 2;
}


