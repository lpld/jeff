package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Or;
import com.github.lpld.jeff.data.Pr;
import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff.functions.Fn;
import com.github.lpld.jeff.functions.Run1;
import com.github.lpld.jeff.functions.XRun;
import com.github.lpld.jeff.functions.Xn0;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import static com.github.lpld.jeff.data.Or.Left;
import static com.github.lpld.jeff.data.Or.Right;
import static com.github.lpld.jeff.data.Pr.Pr;
import static com.github.lpld.jeff.functions.Fn.id;


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

  public static IO<Unit> Fork(Executor executor) {
    return Async(onFinish -> executor.execute(() -> onFinish.run(Right(Unit.unit))));
  }

  public static <T> IO<T> Async(Run1<Run1<Or<Throwable, T>>> f) {
    return new Async<>(f);
  }

  public static <T> IO<T> never() {
    return Async(cb -> {
    });
  }

  // todo: support cancellation logic
  public static <L, R> IO<Or<L, R>> race(Executor executor, IO<L> left, IO<R> right) {

    return Async(callback -> {
      final AtomicBoolean done = new AtomicBoolean();
      final BiConsumer<Or<L, R>, Throwable> onComplete = (res, err) -> {
        if (done.compareAndSet(false, true)) {
          callback.run(Or.of(err, res));
        }
      };

      Fork(executor).chain(left).runAsync()
          .whenComplete((res, err) -> onComplete.accept(Left(res), err));
      Fork(executor).chain(right).runAsync()
          .whenComplete((res, err) -> onComplete.accept(Right(res), err));
    });
  }

  /**
   * Function that non-deterministically places two IO values in a sequence.
   * Return value of this method is a product of either L and IO<R> or a product of R and IO<L>,
   * depending on which of the two IO values computes first.
   */
  public static <T, U> IO<Or<Pr<T, IO<U>>, Pr<U, IO<T>>>>
  seq(Executor executor, IO<T> io1, IO<U> io2) {

    return Async(callback -> {

      final AtomicBoolean fstDone = new AtomicBoolean();
      final CompletableFuture<Object> sndPromise = new CompletableFuture<>();

      final Consumer<Or<Throwable, Or<T, U>>> onComplete =
          (Or<Throwable, Or<T, U>> res) -> {

            if (fstDone.getAndSet(true)) {
              res.forEach(
                  sndPromise::completeExceptionally,
                  success -> sndPromise.complete(success.fold(l -> l, r -> r))
              );
            } else {
              callback.run(res.transform(
                  id(),
                  success -> success.transform(
                      l -> Pr(l, fromFuture(Futures.cast(sndPromise))),
                      r -> Pr(r, fromFuture(Futures.cast(sndPromise)))
                  )
              ));
            }
          };

      Fork(executor).chain(io1).runAsync()
          .handle((res, err) -> Or.of(err, Or.<T, U>Left(res)))
          .thenAccept(onComplete);
      Fork(executor).chain(io2).runAsync()
          .handle((res, err) -> Or.of(err, Or.<T, U>Right(res)))
          .thenAccept(onComplete);
    });
  }

  public static <T> IO<Pr<T, IO<T>>> pair(Executor executor, IO<T> io1, IO<T> io2) {
    return seq(executor, io1, io2).map(Or::flatten);
  }

  public static <T, U> IO<Pr<T, U>> both(Executor executor, IO<T> io1, IO<U> io2) {
    return IO.seq(executor, io1, io2)
        .flatMap(seq -> seq.fold(
            l -> l._2.map(u -> Pr(l._1, u)),
            r -> r._2.map(t -> Pr(t, r._1))
        ));
  }

  public static IO<Unit> sleep(ScheduledExecutorService scheduler, long millis) {
    return Async(onFinish -> scheduler
        .schedule(() -> onFinish.run(Right(Unit.unit)), millis, TimeUnit.MILLISECONDS));
  }

  public static <T> IO<T> fromFuture(CompletableFuture<T> future) {
    return Async(onFinish -> future.whenComplete((res, err) -> onFinish.run(Or.of(err, res))));
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

  public CompletableFuture<T> runAsync() {
    return IORun.runAsync(this, new CallStack<>(), new CompletableFuture<>());
  }

  public T run() {
    try {
      return runAsync().get();
    } catch (InterruptedException err) {
      return WrappedError.throwWrapped(err);
    } catch (ExecutionException err) {
      return WrappedError.throwWrapped(err.getCause());
    }
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