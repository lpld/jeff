package com.github.lpld.jeff_examples.tetris;

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
 * Tetris game engine.
 *
 * The constructor of this class takes initial parameters (height and width)
 * and stream of player's interactions. The output is {@link Tetris#gameStates()} method
 * which returns a stream of {@link GameState}s, each of which represents the state
 * of the game at a given time.
 *
 * @author leopold
 * @since 2019-01-27
 */
public class Tetris {

  /**
   * Player's move
   */
  public enum Move {ROTATE, RIGHT, LEFT, DOWN}

  /**
   * Status of the game
   */
  public enum Status {ACTIVE, PAUSED, OVER}

  /**
   * Game event. Anything that can change state of the game.
   * Roughly speaking, old state + event = new state.
   */
  @RequiredArgsConstructor
  public static class Event {

    public final EventType type;
    public final Move userMove;

    /**
     * Regular "tick" event.
     */
    static Event tick() {
      return new Event(EventType.TICK, null);
    }

    /**
     * Player's interaction.
     */
    static Event userAction(Move move) {
      return new Event(EventType.USER_MOVE, move);
    }
  }

  /**
   * Type of the event.
   */
  public enum EventType {TICK, USER_MOVE}

  /**
   * Holds all information about the state of the game at a given moment.
   */
  @Getter
  @Wither
  @RequiredArgsConstructor
  public static class GameState {

    /**
     * Status of the game: active, paused or over
     */
    private final Status status;

    /**
     * "Static" field (does not contain the currently falling piece)
     */
    private final RectRegion field;

    /**
     * "Static" field plus currently falling piece at its current position.
     */
    private final RectRegion fieldWithPiece;

    /**
     * Currently falling piece and its coordinate.
     */
    private final Option<Pr<RectRegion, Coord>> activePiece;

    /**
     * Source of the pieces. Consists of a single piece (the next piece) and an
     * infinite stream of all other pieces.
     */
    private final Pr<RectRegion, Stream<RectRegion>> piecesSource;

    /**
     * Player's score
     */
    private final int score;

    /**
     * Player's level
     */
    private final int level;

    /**
     * The number or cleared lines in the current level
     */
    private final int linesCleared;
  }

  /**
   * Create a new tetris game of a given dimensions.
   *
   * @param interactions Stream of player's interactions.
   */
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

  /**
   * Stream of all game states.
   */
  public Stream<GameState> gameStates() {

    // empty initial field
    final RectRegion emptyField = RectRegion.ofSize(height, width);

    // Two sources of events:
    // 1. Regular ticks
    final Stream<Event> ticks = Stream.tick(scheduler, 500).map(t -> Event.tick());

    // 2. Player's interactions:
    final Stream<Event> userMoves = interactions.map(Event::userAction);

    // Merging them together:
    final Stream<Event> allEvents = ticks.merge(scheduler, userMoves);

    final GameState initial = new GameState(
        Status.ACTIVE,
        emptyField,
        emptyField,
        None(),
        pullNextPiece(Pieces.infiniteStream),
        0,
        1,
        0);

    // transforming events stream into a stream of game states
    // by taking the initial state and computing the next one using nextState method
    return allEvents
        .scanLeft(initial, this::nextState)
        .takeWhile(s -> s.status != Status.OVER);
  }

  // Compute the next game state given the previous state and an event
  private GameState nextState(GameState state, Event event) {
    switch (event.type) {
      // Regular tick:
      case TICK:
        return state.activePiece

            // Moving the current piece one row down
            .flatMap(ap -> updateCurrentPiece(state, ap._1, ap._2.rowDown()))

            // or clear filled rows, if any:
            .orElse(() -> clearFilledRows(state))

            // or put new piece into the field:
            .getOrElse(() -> injectNew(state));

      // Player's move:
      default:

        return state.activePiece.flatMap(ap -> {

          final RectRegion piece = ap._1;
          final Coord at = ap._2;

          switch (event.userMove) {
            case LEFT:
              return updateCurrentPiece(state, piece, at.left());
            case RIGHT:
              return updateCurrentPiece(state, piece, at.right());
            case DOWN:
              return moveDown(state, piece, at);
            default: // rotate
              final Pr<RectRegion, Coord> rotated = rotate(piece, at);
              return updateCurrentPiece(state, rotated._1, rotated._2);
          }
        }).getOrElse(state);
    }
  }

  // Moving the active piece down (player has pressed 'down')
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

  // clear filled rows, if there are any
  private Option<GameState> clearFilledRows(GameState state) {

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

  // rotate a piece at a given coordinate
  private Pr<RectRegion, Coord> rotate(RectRegion piece, Coord coord) {
    final int diff = (piece.height() - piece.width()) / 2;
    return Pr.of(piece.rotate(), new Coord(coord.x + diff, coord.y - diff));
  }

  // put new piece into the field at the starting position
  private GameState injectNew(GameState state) {
    final Pr<RectRegion, Stream<RectRegion>> newSource = pullNextPiece(state.piecesSource._2);

    // trying to inject the new piece
    return tryInject(state.fieldWithPiece, state.piecesSource._1, start, newSource, state)
        // if failed, then the game is over
        .getOrElse(state
                       .withStatus(Status.OVER)
                       .withField(state.fieldWithPiece)
                       .withActivePiece(None())
        );
  }

  // update current piece, by injecting a new one into the `state.field`.
  private Option<GameState> updateCurrentPiece(GameState state, RectRegion piece, Coord at) {
    return tryInject(state.field, piece, at, state.piecesSource, state);
  }

  // try injecting a piece into the field at a given coordinates
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


