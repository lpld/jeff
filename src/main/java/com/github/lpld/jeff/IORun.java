package com.github.lpld.jeff;

import com.github.lpld.jeff.LList.LCons;
import com.github.lpld.jeff.LList.LNil;
import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff.functions.Fn;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import lombok.RequiredArgsConstructor;

import static com.github.lpld.jeff.IO.pure;

/**
 * @author leopold
 * @since 20/10/18
 */
public final class IORun {

  static <T> CompletableFuture<T> runAsync(IO<T> io, RunningIO runningIO) {
    return doRun(io, new CallStack<>(), runningIO, new CompletableFuture<>());
  }

  private static <T, U, V> CompletableFuture<T> doRun(IO<T> io, CallStack<T> stack,
                                                      RunningIO runningIO,
                                                      CompletableFuture<T> resultPromise) {

    while (true) {
      try {
        io = unwrap(io, stack, Fn.id());

        if (io instanceof Pure) {
          return Futures.completed(resultPromise, ((Pure<T>) io).pure);
        }

        if (io instanceof Async) {
          return executeAsync(resultPromise, runningIO, (Async<T>) io);
        }

        if (io instanceof Bind) {
          stack.stackPush();
          final Bind<U, T> bind = (Bind<U, T>) io;
          final IO<U> source = unwrap(bind.source, stack, u -> u.flatMap(bind.f));

          if (source instanceof Async) {
            final CompletableFuture<U> promise = new CompletableFuture<>();
            final Fn<U, IO<T>> f = bind.f;

            // we want to register `thenAccept` callback before the async callback is called,
            // because we want to remain in async callback's thread. If we don't do this and if
            // async callback is very short, we might call `thenAccept` on a future that is already
            // completed, and `thenAccept` will be executed in current thread, which is not a
            // desirable behavior.

            promise.thenAccept(u -> doRun(f.ap(u), stack, runningIO, resultPromise));

            executeAsync(promise, runningIO, (Async<U>) source);

            return resultPromise;
          }

          if (source instanceof Pure) {
            io = bind.f.ap(((Pure<U>) source).pure);
          } else if (source instanceof Bind) {
            final Bind<V, U> bind2 = (Bind<V, U>) source;
            io = bind2.source.flatMap(a -> bind2.f.ap(a).flatMap(bind.f));
          }
        } else {
          stack.stackPop();
        }
      } catch (Throwable err) {
        final Optional<IO<T>> result = stack.tryHandle(err);

        if (result.isPresent()) {
          io = result.get();
        } else {
          return Futures.failed(err);
        }
      }
    }
  }

  private static <T, U> IO<T> unwrap(IO<T> io, CallStack<U> stack,
                                     Fn<IO<T>, IO<U>> f) throws Throwable {

    while (true) {
      if (io instanceof Fail) {
        throw ((Fail<T>) io).err.ap();
      } else if (io instanceof Recover) {
        IO<T> finalIo = io;
        stack.addRecoveryRule(err -> ((Recover<T>) finalIo).recover.apply(err).map(f::ap));
        io = ((Recover<T>) io).io;
      } else if (io instanceof Suspend) {
        io = ((Suspend<T>) io).resume.ap();
      } else if (io instanceof Delay) {
        return pure(((Delay<T>) io).thunk.ap());
      } else {
        return io;
      }
    }
  }

  private static <T> CompletableFuture<T> executeAsync(CompletableFuture<T> promise,
                                                       RunningIO runningIO,
                                                       Async<T> async) {

    if (runningIO.isCancellable()) {
      final RunState state = runningIO.updateAndGetState(st -> new RunState(st.isCancelled,
                                                                            st.cancellingNow,
                                                                            true));

      if (state.isCancelled || state.cancellingNow) {
        return Futures.cancelled(promise);
      }
    }

    runningIO.setCancelLogic(async.cb.ap(result -> result.forEach(
        promise::completeExceptionally,
        promise::complete
    )));

    promise.thenRun(() -> runningIO.setCancelLogic(IO.unit));

    if (runningIO.isCancellable()) {
      // todo: rethink this logic. Can we miss a cancellation request?
      final RunState newState =
          runningIO.updateAndGetState(st -> new RunState(st.isCancelled, st.cancellingNow, false));

      if (newState.cancellingNow) {
        runningIO.cancelNow();
      }
    }

    return promise;
  }
}

@RequiredArgsConstructor
class RunState {

  public static final RunState INITIAL = new RunState(false, false, false);

  final boolean isCancelled;
  final boolean cancellingNow;
  final boolean startingNow;
}

interface CancellableIO extends RunningIO {

  void cancel();

  static CancellableIO create() {
    return new CancellableIOTask();
  }
}

interface RunningIO {

  boolean isCancellable();

  RunState updateAndGetState(UnaryOperator<RunState> u);

  void setCancelLogic(IO<Unit> cancelLogic);

  void cancelNow();
}

class UncancellableIOTask implements RunningIO {

  public static final UncancellableIOTask INSTANCE = new UncancellableIOTask();

  @Override
  public boolean isCancellable() {
    return false;
  }

  @Override
  public RunState updateAndGetState(UnaryOperator<RunState> u) {
    return RunState.INITIAL;
  }

  @Override
  public void setCancelLogic(IO<Unit> cancelLogic) {

  }

  @Override
  public void cancelNow() {

  }
}

class CancellableIOTask implements RunningIO, CancellableIO {

  private final AtomicReference<RunState> state = new AtomicReference<>(RunState.INITIAL);
  private IO<Unit> cancelAction = IO.unit;

  @Override
  public boolean isCancellable() {
    return true;
  }

  @Override
  public RunState updateAndGetState(UnaryOperator<RunState> u) {
    return state.updateAndGet(u);
  }

  @Override
  public void setCancelLogic(IO<Unit> cancelLogic) {
    this.cancelAction = cancelLogic;
  }

  @Override
  public void cancelNow() {
    // todo: if an error happens here, we will ignore it, but is it OK?
    this.cancelAction.runAsync();
  }

  @Override
  public void cancel() {
    final RunState prevState =
        this.state.getAndUpdate(st -> new RunState(st.isCancelled, true, st.startingNow));

    if (prevState.isCancelled || prevState.startingNow || prevState.cancellingNow) {
      return;
    }

    cancelNow();

    state.set(new RunState(true, false, false));
  }
}

class CallStack<T> {

  private CallItem<T> top = new CallItem<>(null);

  void stackPush() {
    top.callCount++;
  }

  void stackPop() {
    if (top.callCount > 0) {
      top.callCount--;
    } else {
      top = top.prev;
    }
  }

  void addRecoveryRule(Function<Throwable, Optional<IO<T>>> rule) {
    if (top.callCount > 0) {
      top.callCount--;
      top = new CallItem<>(top);
    }
    top.addRule(rule);
  }

  Optional<IO<T>> tryHandle(Throwable err) {

    while (top != null) {

      top.callCount = 0;
      Optional<IO<T>> result = top.tryHandle(err);

      if (top.rules.isEmpty()) {
        top = top.prev;
      }

      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }
}

@lombok.RequiredArgsConstructor
class CallItem<T> {

  final CallItem<T> prev;
  int callCount = 0;
  LList<Function<Throwable, Optional<IO<T>>>> rules = LNil.instance();

  void addRule(Function<Throwable, Optional<IO<T>>> rule) {
    rules = rules.prepend(rule);
  }

  Optional<IO<T>> tryHandle(Throwable err) {
    while (rules.isNotEmpty()) {
      final Function<Throwable, Optional<IO<T>>> head =
          ((LCons<Function<Throwable, Optional<IO<T>>>>) rules).head;

      rules =
          ((LCons<Function<Throwable, Optional<IO<T>>>>) rules).tail;

      final Optional<IO<T>> result = head.apply(err);
      if (result.isPresent()) {
        return result;
      }

    }
    return Optional.empty();
  }


}
