package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Or;
import com.github.lpld.jeff.data.Pr;
import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff.functions.Fn;
import com.github.lpld.jeff.functions.Run1;
import com.github.lpld.jeff.functions.Run3;
import com.github.lpld.jeff.functions.XRun;
import com.github.lpld.jeff.functions.Xn0;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * It represents a computation of type {@code T} that also can perform a side-effect when evaluated.
 * IO values can represent both synchronous and asynchronous computations.
 *
 * @author leopold
 * @since 4/10/18
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class IO<T> {

  /**
   * Describes a synchronous action that will be evaluated on current thread.
   */
  public static <T> IO<T> delay(Xn0<T> action) {
    return new Delay<>(action);
  }

  public static IO<Unit> delay(XRun action) {
    return delay(action.toXn0());
  }

  /**
   * Shortcut for {@link IO#delay}
   */
  public static <T> IO<T> IO(Xn0<T> action) {
    return delay(action);
  }

  public static IO<Unit> IO(XRun action) {
    return delay(action);
  }

  /**
   * Lift a pure value to IO type.
   */
  public static <T> IO<T> pure(T pure) {
    return new Pure<>(pure);
  }

  public static final IO<Unit> unit = pure(Unit.unit);

  /**
   * Defer the construction of IO value.
   */
  public static <T> IO<T> suspend(Xn0<IO<T>> resume) {
    return new Suspend<>(resume);
  }

  /**
   * Create an IO that fails with an error {@code t}.
   */
  public static <T> IO<T> fail(Throwable t) {
    return new Fail<>(t);
  }

  /**
   * Shift the execution of IO to another thread/thread-pool.
   */
  public static IO<Unit> forked(Executor executor) {
    return async(onFinish -> executor.execute(() -> onFinish.run(Right(Unit.unit))));
  }

  public IO<T> fork(Executor executor) {
    return this.then(IO.forked(executor));
  }

  /**
   * Creates an async boundary.
   */
  public IO<T> fork() {
    return this.then(IO.async(onFinish -> onFinish.run(Right(Unit.unit))));
  }

  /**
   * Create an IO that represents asynchronous computation. Function {@code f} injects a callback
   * ({@code Run1<Or<Throwable, T>>}) that user can use to signal completion with either
   * {@code Or.Right(T)} (successful result) or {@code Or.Left(Throwable)} (failure).
   */
  public static <T> IO<T> async(Run1<Run1<Or<Throwable, T>>> f) {
    return new Async<>(cb -> {
      f.run(cb);
      return IO.unit;
    });
  }

  /**
   * Create an async IO that can be cancelled. Function {@code f} injects a callback (similar to
   * {@link IO#async(Run1)}) and expects the user to provide cancellation action. See implementation
   * of {@link IO#sleep(ScheduledExecutorService, long)} for example.
   */
  public static <T> IO<T> cancellable(Fn<Run1<Or<Throwable, T>>, IO<?>> f) {
    return new Async<>(f.andThen(IO::toUnit));
  }

  /**
   * IO that never completes.
   */
  public static <T> IO<T> never() {
    return async(cb -> {
    });
  }

  /**
   * Initiate a "race" between two IO computations and return an IO that will contain a value of
   * the IO that completes first. The second IO will be cancelled.
   */
  public static <L, R> IO<Or<L, R>> race(Executor executor, IO<L> io1, IO<R> io2) {

    return IO.cancellable(callback -> {
      final AtomicBoolean done = new AtomicBoolean();
      final Run3<Or<L, R>, Throwable, CancellableIO> onComplete = (res, err, other) -> {
        if (done.compareAndSet(false, true)) {
          other.cancel();
          callback.run(Or.of(err, res));
        }
      };

      final CancellableIO task1 = CancellableIO.create();
      final CancellableIO task2 = CancellableIO.create();

      IORun.runAsync(IO.forked(executor).chain(io1), task1)
          .whenComplete((res, err) -> onComplete.run(Left(res), err, task2));

      IORun.runAsync(IO.forked(executor).chain(io2), task2)
          .whenComplete((res, err) -> onComplete.run(Right(res), err, task1));

      return IO.delay(() -> {
        task1.cancel();
        task2.cancel();
      });
    });
  }

  /**
   * Function that non-deterministically places two IO values in a sequence.
   * Return value of this method is a product of either L and IO<R> or a product of R and IO<L>,
   * depending on which of the two IO values computes first.
   */
  public static <T, U> IO<Or<Pr<T, IO<U>>, Pr<U, IO<T>>>>
  seq(Executor executor, IO<T> io1, IO<U> io2) {

    return async(callback -> {

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

      IO.forked(executor).chain(io1).runAsync()
          .handle((res, err) -> Or.of(err, Or.<T, U>Left(res)))
          .thenAccept(onComplete);
      IO.forked(executor).chain(io2).runAsync()
          .handle((res, err) -> Or.of(err, Or.<T, U>Right(res)))
          .thenAccept(onComplete);
    });
  }

  /**
   * Same as {@link IO#seq(Executor, IO, IO)}, but for cases when result types of both tasks are
   * the same. This will allow to work with much simpler type signatures:
   * {@code Pr<T, IO<T>>} instead of {@code Or<Pr<T, IO<U>>, Pr<U, IO<T>>>}
   */
  public static <T> IO<Pr<T, IO<T>>> pair(Executor executor, IO<T> io1, IO<T> io2) {
    return seq(executor, io1, io2).map(Or::flatten);
  }

  /**
   * Create an IO that will complete with values of concurrent execution of {@code io1} and
   * {@code io2}.
   */
  public static <T, U> IO<Pr<T, U>> both(Executor executor, IO<T> io1, IO<U> io2) {
    return IO.seq(executor, io1, io2)
        .flatMap(seq -> seq.fold(
            l -> l._2.map(u -> Pr(l._1, u)),
            r -> r._2.map(t -> Pr(t, r._1))
        ));
  }

  /**
   * Sleep for {@code millis} amount of milliseconds.
   */
  public static IO<Unit> sleep(ScheduledExecutorService scheduler, long millis) {
    return IO.cancellable(onFinish -> {
      final ScheduledFuture<?> task = scheduler
          .schedule(() -> onFinish.run(Right(Unit.unit)), millis, TimeUnit.MILLISECONDS);

      return IO.delay(() -> task.cancel(false));
    });
  }

  public static <T> IO<T> fromFuture(CompletableFuture<T> future) {
    return async(onFinish -> future.whenComplete((res, err) -> onFinish.run(Or.of(err, res))));
  }

  /**
   * Apply transformation {@code f} to this IO.
   */
  public <U> IO<U> map(Fn<T, U> f) {
    return flatMap(f.andThen(IO::pure));
  }

  /**
   * Shortcut for {@code map(__ -> Unit.unit)}
   */
  public IO<Unit> toUnit() {
    return map(any -> Unit.unit);
  }

  /**
   * Compose this IO with another IO that depends on the result of this IO.
   */
  public <U> IO<U> flatMap(Fn<T, IO<U>> f) {
    return new Bind<>(this, f);
  }

  /**
   * {@code flatMap} that ignores result of this IO.
   */
  public <U> IO<U> chain(IO<U> io) {
    return flatMap(t -> io);
  }

  /**
   * After computing this IO, execute an action {@code io} and ignore it's result.
   */
  public IO<T> then(IO<?> io) {
    return flatMap(t -> io.map(any -> t));
  }

  public IO<T> then(Fn<T, IO<?>> f) {
    return flatMap(t -> f.ap(t).chain(pure(t)));
  }

  public IO<Or<Throwable, T>> attempt() {
    return map(Or::<Throwable, T>Right).recover(t -> Optional.of(Left(t)));
  }

  public IO<T> recover(Function<Throwable, Optional<T>> r) {
    return recoverWith(r.andThen(opt -> opt.map(IO::pure)));
  }

  public IO<T> recoverWith(Function<Throwable, Optional<IO<T>>> r) {
    return new Recover<>(this, r);
  }

  /**
   * Trigger asynchronous execution of this IO.
   */
  public CompletableFuture<T> runAsync() {
    return IORun.runAsync(this, UncancellableIOTask.INSTANCE);
  }

  /**
   * Synchronously run this IO. This method will block if this IO has asynchronous or blocking
   * parts.
   */
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
    return "delay(.)";
  }
}

@RequiredArgsConstructor
class Suspend<T> extends IO<T> {

  final Xn0<IO<T>> resume;

  @Override
  public String toString() {
    return "suspend(.)";
  }
}

@RequiredArgsConstructor
class Pure<T> extends IO<T> {
  final T pure;

  @Override
  public String toString() {
    return "pure(" + pure + ")";
  }
}

@RequiredArgsConstructor
class Fail<T> extends IO<T> {

  final Throwable err;

  @Override
  public String toString() {
    return "fail(" + err + ")";
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
  final Fn<Run1<Or<Throwable, T>>, IO<Unit>> cb;

  @Override
  public String toString() {
    return "async(.)";
  }
}