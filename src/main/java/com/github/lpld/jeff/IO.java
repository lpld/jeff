package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Or;
import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff.functions.Fn;
import com.github.lpld.jeff.functions.Fn0;
import com.github.lpld.jeff.functions.Run;

import java.util.Optional;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;


/**
 * IO monad for Java, sort of.
 *
 * @author leopold
 * @since 4/10/18
 */
@SuppressWarnings("WeakerAccess")
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class IO<T> {

  public static <T> IO<T> IO(Fn0<T> action) {
    return Delay(action);
  }

  public static IO<Unit> IO(Run action) {
    return Delay(action);
  }

  public static final IO<Unit> unit = Pure(Unit.unit);

  public static <T> IO<T> Delay(Fn0<T> action) {
    return new Delay<>(action);
  }

  public static IO<Unit> Delay(Run action) {
    return Delay(action.toF0());
  }

  public static <T> IO<T> Suspend(Fn0<IO<T>> resume) {
    return new Suspend<>(resume);
  }

  public static <T> IO<T> Pure(T pure) {
    return new Pure<>(pure);
  }

  public static <T> IO<T> RaiseError(Throwable t) {
    return new RaiseError<>(t);
  }

  public <U> IO<U> map(Fn<T, U> f) {
    return flatMap(f.andThen(IO::Pure));
  }

  public IO<Unit> toUnit() {
    return map(any -> Unit.unit);
  }

  public <U> IO<U> flatMap(Fn<T, IO<U>> f) {
    return new Bind<>(this, f);
  }

  public <U> IO<U> then(Fn0<IO<U>> f) {
    return flatMap(t -> f.ap());
  }

  public IO<Or<Throwable, T>> attempt() {
    return map(Or::<Throwable, T>right).recover(t -> Optional.of(Or.left(t)));
  }

  public IO<T> recover(Function<Throwable, Optional<T>> r) {
    return recoverWith(r.andThen(opt -> opt.map(IO::Pure)));
  }

  public IO<T> recoverWith(Function<Throwable, Optional<IO<T>>> r) {
    return new Recover<>(this, r);
  }

  public T run() {
    try {
      return IORun.run(this);
    } catch (Throwable throwable) {
      return WrappedError.throwWrapped(throwable);
    }
  }
}

@RequiredArgsConstructor
class Delay<T> extends IO<T> {
  final Fn0<T> thunk;
}

@RequiredArgsConstructor
class Suspend<T> extends IO<T> {
  final Fn0<IO<T>> resume;
}

@RequiredArgsConstructor
class Pure<T> extends IO<T> {
  final T pure;
}

@RequiredArgsConstructor
class RaiseError<T> extends IO<T> {
  final Throwable t;
}

@RequiredArgsConstructor
class Recover<T> extends IO<T> {
  final IO<T> io;
  final Function<Throwable, Optional<IO<T>>> recover;
}

@RequiredArgsConstructor
class Bind<T, U> extends IO<U> {
  final IO<T> source;
  final Fn<T, IO<U>> f;
}