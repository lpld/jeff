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
    return new Delay<>(() -> f.apply(run()));
  }

  public <U> IO<U> flatMap(F<T, IO<U>> f) {
    return new Delay<>(() -> f.apply(run()).run());
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

  abstract T run();
}

class Delay<T> extends IO<T> {

  private final F0<T> resume;

  Delay(F0<T> resume) {
    this.resume = resume;
  }

  @Override
  T run() {
    try {
      return resume.get();
    } catch (Throwable throwable) {
      return WrappedError.throwWrapped(throwable);
    }
  }
}

class Pure<T> extends IO<T> {

  private final T pure;

  Pure(T pure) {
    this.pure = pure;
  }

  @Override
  T run() {
    return pure;
  }
}

class RaiseError<T> extends IO<T> {

  private final Throwable t;

  RaiseError(Throwable t) {
    this.t = t;
  }

  @Override
  T run() {
    return WrappedError.throwWrapped(t);
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
  T run() {
    try {
      return io.run();
    } catch (Throwable t) {
      return recover
          .apply(t)
          .orElseGet(() -> WrappedError.throwWrapped(t))
          .run();
    }
  }
}
