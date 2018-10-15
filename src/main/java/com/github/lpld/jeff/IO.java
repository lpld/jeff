package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Or;
import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff.functions.Fn;
import com.github.lpld.jeff.functions.Run1;
import com.github.lpld.jeff.functions.XRun;
import com.github.lpld.jeff.functions.Xn0;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import static com.github.lpld.jeff.data.Or.Left;
import static com.github.lpld.jeff.data.Or.Right;


/**
 * IO monad for Java, sort of.
 *
 * @author leopold
 * @since 4/10/18
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class IO<T> {

  public static <T> IO<T> IO(Xn0<T> action) {
    return Delay(action);
  }

  public static IO<Unit> IO(XRun action) {
    return Delay(action);
  }

  public static final IO<Unit> unit = Pure(Unit.unit);

  public static <T> IO<T> Delay(Xn0<T> action) {
    return new Delay<>(action);
  }

  public static IO<Unit> Delay(XRun action) {
    return Delay(action.toXn0());
  }

  public static <T> IO<T> Suspend(Xn0<IO<T>> resume) {
    return new Suspend<>(resume);
  }

  public static <T> IO<T> Pure(T pure) {
    return new Pure<>(pure);
  }

  public static <T> IO<T> Fail(Throwable t) {
    return new Fail<>(t);
  }

  public static IO<Unit> Fork(ExecutorService executor) {
    return Async(onFinish -> executor.submit(() -> {
      onFinish.run(Right(Unit.unit));
    }));
  }

  public static <T> IO<T> Async(Run1<Run1<Or<Throwable, T>>> f) {
    return new Async<>(f);
  }

  public static IO<Unit> sleep(ScheduledExecutorService scheduler, long millis) {
    return Async(onFinish -> scheduler
        .schedule(() -> onFinish.run(Right(Unit.unit)), millis, TimeUnit.MILLISECONDS));
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

  public <U> IO<U> chain(IO<U> io) {
    return flatMap(t -> io);
  }

  public IO<Or<Throwable, T>> attempt() {
    return map(Or::<Throwable, T>Right).recover(t -> Optional.of(Left(t)));
  }

  public IO<T> recover(Function<Throwable, Optional<T>> r) {
    return recoverWith(r.andThen(opt -> opt.map(IO::Pure)));
  }

  public IO<T> recoverWith(Function<Throwable, Optional<IO<T>>> r) {
    return new Recover<>(this, r);
  }

  public T run() {
    try {
      return IORun.runAsync(this).get();
    } catch (InterruptedException | ExecutionException err) {
      return WrappedError.throwWrapped(err);
    }
  }

  public CompletableFuture<T> runAsync() {
    return IORun.runAsync(this);
  }
}

@RequiredArgsConstructor
class Delay<T> extends IO<T> {

  final Xn0<T> thunk;

  @Override
  public String toString() {
    return "Delay(.)";
  }
}

@RequiredArgsConstructor
class Suspend<T> extends IO<T> {

  final Xn0<IO<T>> resume;

  @Override
  public String toString() {
    return "Suspend(.)";
  }
}

@RequiredArgsConstructor
class Pure<T> extends IO<T> {
  final T pure;

  @Override
  public String toString() {
    return "Pure(" + pure + ")";
  }
}

@RequiredArgsConstructor
class Fail<T> extends IO<T> {

  final Throwable err;

  @Override
  public String toString() {
    return "Fail(" + err + ")";
  }
}

@RequiredArgsConstructor
class Recover<T> extends IO<T> {
  final IO<T> io;
  final Function<Throwable, Optional<IO<T>>> recover;

  @Override
  public String toString() {
    return "Recover(" + io + ")";
  }
}

@RequiredArgsConstructor
class Bind<T, U> extends IO<U> {
  final IO<T> source;
  final Fn<T, IO<U>> f;

  @Override
  public String toString() {
    return "Bind(" + source + ", .)";
  }
}

@RequiredArgsConstructor
class Async<T> extends IO<T> {

  // (Or<Throwable, T> => Unit) => Unit
  final Run1<Run1<Or<Throwable, T>>> cb;

  @Override
  public String toString() {
    return "Async(.)";
  }
}