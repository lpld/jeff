package com.github.lpld.jeff;

import com.github.lpld.jeff.LList.LCons;
import com.github.lpld.jeff.LList.LNil;
import com.github.lpld.jeff.data.Futures;
import com.github.lpld.jeff.data.Or;
import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff.functions.Fn;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.github.lpld.jeff.data.Or.Left;
import static com.github.lpld.jeff.data.Or.Right;

/**
 * @author leopold
 * @since 6/10/18
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class IORun {

  /**
   * Stack-safe evaluation of IO.
   */
  static <T> T run(IO<T> io) throws Throwable {

    final ErrorHandlers<T> errorHandlers = new ErrorHandlers<>();

    while (true) {
      // Recover, Fail, Suspend and Delay:
      io = unfold(io, errorHandlers);

      if (io instanceof Fork) {
        return (T) Unit.unit;
      }

      // Pure:
      if (io instanceof Pure) {
        return ((Pure<T>) io).pure;
      }

      assert io instanceof Bind;

      // FlatMap:
      try {
        final Or<IO<T>, CompletableFuture<T>> result = applyFlatMap(((Bind<?, T>) io));
        if (result.isLeft()) {
          io = result.getLeft();
        } else {
          // todo: errors!
          return result.getRight().get();
        }
      } catch (Throwable t) {
        io = errorHandlers.tryHandle(t);
      }
    }
  }

  private static <T, U, V> Or<IO<U>, CompletableFuture<U>> applyFlatMap(Bind<T, U> fm)
      throws Throwable {
    final ErrorHandlers<T> errorHandlers = new ErrorHandlers<>();
    final Fn<T, IO<U>> f = fm.f;
    final IO<T> source = unfold(fm.source, errorHandlers);

    if (source instanceof Fork) {
      final ExecutorService executor = ((Fork) source).executor;
      final Fn<Unit, IO<U>> ff = (Fn<Unit, IO<U>>) f;

      return Right(Futures.run(() -> run(ff.ap(Unit.unit)), executor));
    }

    if (source instanceof Pure) {
      return Left(f.ap(((Pure<T>) source).pure));
    }

    assert source instanceof Bind;
    final Bind<V, T> fm2 = (Bind<V, T>) source;
    return Left(fm2.source.flatMap(a -> fm2.f.ap(a).flatMap(f)));
  }

  private static <T> IO<T> unfold(IO<T> io, ErrorHandlers<T> errorHandlers) throws Throwable {
    IO<T> unfolded = null;
    while (unfolded == null) {
      if (io instanceof Recover) {
        errorHandlers.add(((Recover<T>) io).recover);
        io = ((Recover<T>) io).io;
      } else if (io instanceof Fail) {
        io = errorHandlers.tryHandle(((Fail<T>) io).t);
      } else if (io instanceof Suspend) {
        try {
          io = ((Suspend<T>) io).resume.ap();
        } catch (Throwable t) {
          io = errorHandlers.tryHandle(t);
        }
      } else if (io instanceof Delay) {
        try {
          unfolded = IO.Pure(((Delay<T>) io).thunk.ap());
        } catch (Throwable t) {
          unfolded = errorHandlers.tryHandle(t);
        }
      } else {
        unfolded = io;
      }
    }
    return unfolded;
  }

}

class ErrorHandlers<T> {

  private LList<Function<Throwable, Optional<IO<T>>>> handlers = LNil.instance();

  void add(Function<Throwable, Optional<IO<T>>> handler) {
    handlers = handlers.prepend(handler);
  }

  IO<T> tryHandle(Throwable t) throws Throwable {
    LList<Function<Throwable, Optional<IO<T>>>> h = handlers;

    while (h.isNotEmpty()) {
      final Optional<IO<T>> res =
          ((LCons<Function<Throwable, Optional<IO<T>>>>) h).head.apply(t);

      if (res.isPresent()) {
        return res.get();
      }

      h = ((LCons<Function<Throwable, Optional<IO<T>>>>) h).tail;
    }
    throw t;
  }
}