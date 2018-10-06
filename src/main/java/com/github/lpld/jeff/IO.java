package com.github.lpld.jeff;

import com.github.lpld.jeff.functions.F;
import com.github.lpld.jeff.functions.F0;
import com.github.lpld.jeff.functions.Run;

import java.util.Optional;
import java.util.function.Function;


/**
 * IO monad for Java, sort of.
 *
 * @author leopold
 * @since 4/10/18
 */
@SuppressWarnings("WeakerAccess")
public abstract class IO<T> {

  IO() {
  }

  public static <T> IO<T> IO(F0<T> action) {
    return delay(action);
  }

  public static IO<Unit> IO(Run action) {
    return delay(action);
  }

  public static final IO<Unit> unit = pure(Unit.unit);

  public static <T> IO<T> delay(F0<T> action) {
    return new Delay<>(action);
  }

  public static IO<Unit> delay(Run action) {
    return delay(action.toF0());
  }

  public static <T> IO<T> pure(T pure) {
    return new Pure<>(pure);
  }

  public static <T> IO<T> raiseError(Throwable t) {
    return new RaiseError<>(t);
  }

  public <U> IO<U> map(F<T, U> f) {
    return flatMap(f.andThen(IO::pure));
  }

  public <U> IO<U> flatMap(F<T, IO<U>> f) {
    return new FlatMap<>(this, f);
  }

  public <U> IO<U> then(F0<IO<U>> f) {
    return flatMap(t -> f.get());
  }

  public IO<T> recover(Function<Throwable, Optional<T>> r) {
    return recoverWith(r.andThen(opt -> opt.map(IO::pure)));
  }

  public IO<T> recoverWith(Function<Throwable, Optional<IO<T>>> r) {
    return new Recover<>(this, r);
  }

  public T runUnsafe() {
    try {
      return run();
    } catch (Throwable throwable) {
      return WrappedError.throwWrapped(throwable);
    }
  }

  protected T run() throws Throwable {
    IO<T> io = this;

    while (true) {
      if (io instanceof Pure) {
        return ((Pure<T>) io).pure;
      } else if (io instanceof Delay) {
        return ((Delay<T>) io).thunk.get();
      } else if (io instanceof Suspend) {
        io = unfoldSuspend((Suspend<T>) io);
      } else if (io instanceof FlatMap) {
        io = unfoldFlatMap(((FlatMap<?, T>) io));
      }
    }
    // todo: handle RaiseError and Recover
  }

  private static <T> IO<T> unfoldSuspend(Suspend<T> s) throws Throwable {
    IO<T> io = s;
    while (io instanceof Suspend) {
      io = ((Suspend<T>) io).resume.get();
    }
    return io;
  }

  private static <T, U> IO<U> unfoldFlatMap(FlatMap<T, U> fm) throws Throwable {
    final F<T, IO<U>> f = fm.f;
    IO<T> source = fm.source;
    if (source instanceof Suspend) {
      source = unfoldSuspend(((Suspend<T>) source));
    }

    if (source instanceof Pure) {
      return f.apply(((Pure<T>) source).pure);
    } else if (source instanceof Delay) {
      return f.apply(((Delay<T>) source).thunk.get());
    } else if (source instanceof FlatMap) {
      final FlatMap<Object, T> fm2 = (FlatMap<Object, T>) source;
      return fm2.source.flatMap(a -> fm2.f.apply(a).flatMap(f));
    }

    throw new IllegalStateException();
  }
}

class Delay<T> extends IO<T> {

  final F0<T> thunk;

  Delay(F0<T> thunk) {
    this.thunk = thunk;
  }
}

class Suspend<T> extends IO<T> {

  final F0<IO<T>> resume;

  Suspend(F0<IO<T>> resume) {
    this.resume = resume;
  }
}

class Pure<T> extends IO<T> {

  final T pure;

  Pure(T pure) {
    this.pure = pure;
  }
}

class RaiseError<T> extends IO<T> {

  private final Throwable t;

  RaiseError(Throwable t) {
    this.t = t;
  }

  @Override
  protected T run() throws Throwable {
    throw t;
  }
}

class Recover<T> extends IO<T> {

  private final IO<T> io;
  private final Function<Throwable, Optional<IO<T>>> recover;

  Recover(IO<T> io, Function<Throwable, Optional<IO<T>>> recover) {
    this.io = io;
    this.recover = recover;
  }

  @Override
  protected T run() throws Throwable {
    try {
      return io.run();
    } catch (Throwable t) {
      return recover
          .apply(t)
          .orElseThrow(() -> t)
          .run();
    }
  }
}

class FlatMap<T, U> extends IO<U> {

  final IO<T> source;
  final F<T, IO<U>> f;

  public FlatMap(IO<T> io, F<T, IO<U>> f) {
    this.source = io;
    this.f = f;
  }
}