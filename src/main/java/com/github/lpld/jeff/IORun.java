package com.github.lpld.jeff;

import com.github.lpld.jeff.functions.Fn;

import java.util.Optional;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

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
      // Recover, RaiseError, Suspend and Delay:
      io = unfold(io, errorHandlers);

      // Pure:
      if (io instanceof Pure) {
        return ((Pure<T>) io).pure;
      }

      assert io instanceof Bind;

      // FlatMap:
      try {
        io = applyFlatMap(((Bind<?, T>) io));
      } catch (Throwable t) {
        io = errorHandlers.tryHandle(t);
      }
    }
  }

  private static <T, U, V> IO<U> applyFlatMap(Bind<T, U> fm) throws Throwable {
    final ErrorHandlers<T> errorHandlers = new ErrorHandlers<>();
    final Fn<T, IO<U>> f = fm.f;
    final IO<T> source = unfold(fm.source, errorHandlers);

    if (source instanceof Pure) {
      return f.ap(((Pure<T>) source).pure);
    }

    assert source instanceof Bind;
    final Bind<V, T> fm2 = (Bind<V, T>) source;
    return fm2.source.flatMap(a -> fm2.f.ap(a).flatMap(f));
  }

  private static <T> IO<T> unfold(IO<T> io, ErrorHandlers<T> errorHandlers) throws Throwable {
    IO<T> unfolded = null;
    while (unfolded == null) {
      if (io instanceof Recover) {
        errorHandlers.add(((Recover<T>) io).recover);
        io = ((Recover<T>) io).io;
      } else if (io instanceof RaiseError) {
        io = errorHandlers.tryHandle(((RaiseError<T>) io).t);
      } else if (io instanceof Suspend) {
        try {
          io = ((Suspend<T>) io).resume.ap();
        } catch (Throwable t) {
          io = errorHandlers.tryHandle(t);
        }
      } else if (io instanceof Delay) {
        try {
          unfolded = IO.pure(((Delay<T>) io).thunk.ap());
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
          ((LCons<Function<Throwable, Optional<IO<T>>>>) h).value.apply(t);

      if (res.isPresent()) {
        return res.get();
      }

      h = ((LCons<Function<Throwable, Optional<IO<T>>>>) h).tail;
    }
    throw t;
  }
}