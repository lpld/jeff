package com.github.lpld.jeff;

import com.github.lpld.jeff.functions.F;

import java.util.Optional;
import java.util.function.Function;

/**
 * @author leopold
 * @since 6/10/18
 */
final class IORun {

  private IORun() {
  }

  static <T> T run(IO<T> io) throws Throwable {

    final ErrorHandlers<T> errorHandlers = new ErrorHandlers<>();

    while (true) {
      // Recover, RaiseError, Suspend and Delay:
      io = unfold(io, errorHandlers);

      // Pure:
      if (io instanceof Pure) {
        return ((Pure<T>) io).pure;
      }

      assert io instanceof FlatMap;

      // FlatMap:
      try {
        io = applyFlatMap(((FlatMap<?, T>) io));
      } catch (Throwable t) {
        io = errorHandlers.tryHandle(t);
      }
    }
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
          io = ((Suspend<T>) io).resume.get();
        } catch (Throwable t) {
          io = errorHandlers.tryHandle(t);
        }
      } else if (io instanceof Delay) {
        try {
          unfolded = IO.pure(((Delay<T>) io).thunk.get());
        } catch (Throwable t) {
          unfolded = errorHandlers.tryHandle(t);
        }
      } else {
        unfolded = io;
      }
    }
    return unfolded;
  }

  private static <T, U, V> IO<U> applyFlatMap(FlatMap<T, U> fm) throws Throwable {
    final ErrorHandlers<T> errorHandlers = new ErrorHandlers<>();
    final F<T, IO<U>> f = fm.f;
    final IO<T> source = unfold(fm.source, errorHandlers);

    if (source instanceof Pure) {
      return f.apply(((Pure<T>) source).pure);
    }

    assert source instanceof FlatMap;
    final FlatMap<V, T> fm2 = (FlatMap<V, T>) source;
    return fm2.source.flatMap(a -> fm2.f.apply(a).flatMap(f));
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