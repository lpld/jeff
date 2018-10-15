package com.github.lpld.jeff;

import com.github.lpld.jeff.LList.LCons;
import com.github.lpld.jeff.LList.LNil;
import com.github.lpld.jeff.data.Futures;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.github.lpld.jeff.IO.Pure;

/**
 * @author leopold
 * @since 13/10/18
 */
public class IORun {

  public static <T> T run(IO<T> io) throws ExecutionException, InterruptedException {
    return runAsyncInternal(io).get();
  }

  private static <T, U, V> CompletableFuture<T> runAsyncInternal(IO<T> io) {
    final ErrorHandlers<T> tHandlers = new ErrorHandlers<>();
    int nestedHandlers = 0;
    while (true) {
      try {
        io = unwrap(io, tHandlers);
      } catch (Throwable err) {
        return Futures.failed(err);
      }

      if (io instanceof Pure) {
        return CompletableFuture.completedFuture(((Pure<T>) io).pure);
      }

      if (io instanceof Async) {
        return handleAsyncErros(tHandlers, executeAsync(new CompletableFuture<>(), (Async<T>) io));
      }

      boolean handlerAdded = false;
      try {
        if (io instanceof Bind) {
          final Bind<U, T> bind = (Bind<U, T>) io;

          final ErrorHandlers<U> uHandlers = new ErrorHandlers<>();

          final IO<U> source = unwrap(bind.source, uHandlers);

          if (source instanceof Async) {
            final CompletableFuture<U> promise =
                handleAsyncErros(uHandlers, new CompletableFuture<>());

            // we want to register `thenCompose` callback before the async callback is called,
            // because we want to remain in async callback's thread. If we don't do this and if
            // async callback is very short, we might call `thenCompose` on a future that is already
            // completed, and `thenCompose` will be executed in current thread, which is not a
            // desirable behavior.
            final CompletableFuture<T> result =
                promise.thenCompose(io1 -> runAsyncInternal(bind.f.ap(io1)));

            executeAsync(promise, (Async<U>) source);
            return handleAsyncErros(tHandlers, result);
          }

          if (source instanceof Pure) {
            io = bind.f.ap(((Pure<U>) source).pure);
          } else if (source instanceof Bind) {
            final Bind<V, U> bind2 = (Bind<V, U>) source;
            if (!uHandlers.isEmpty()) {
              tHandlers.add(t -> uHandlers.handle(t).map(u -> u.flatMap(bind.f)));
              nestedHandlers++;
              handlerAdded = true;
            }
            io = bind2.source.flatMap(a -> bind2.f.ap(a).flatMap(bind.f));
          }
        }
      } catch (Throwable err) {
        try {
          io = tHandlers.handleOrRethrow(err);
        } catch (Throwable err2) {
          return Futures.failed(err2);
        }
      }

      if (!handlerAdded && nestedHandlers > 0) {
        tHandlers.removeLast();
        nestedHandlers--;
      }
    }
  }

  private static <T> CompletableFuture<T> handleAsyncErros(ErrorHandlers<T> tHandlers,
                                                           CompletableFuture<T> tAsync) {
    return tAsync.handle((result, err) -> {
      if (err != null) {
        return Futures.run(() -> runAsyncInternal(tHandlers.handleOrRethrow(err)));
      } else {
        return CompletableFuture.completedFuture(result);
      }
    }).thenCompose(Function.identity());
  }

  // Handle Suspend and Delay
  private static <T> IO<T> unwrap(IO<T> io, ErrorHandlers<T> handlers) throws Throwable {
    while (true) {
      if (io instanceof Fail) {
        io = handlers.handleOrRethrow(((Fail<T>) io).t);
      } else if (io instanceof Recover) {
        handlers.add(((Recover<T>) io).recover);
        io = ((Recover<T>) io).io;
      } else {

        try {
          if (io instanceof Suspend) {
            io = ((Suspend<T>) io).resume.ap();
          } else if (io instanceof Delay) {
            return Pure(((Delay<T>) io).thunk.ap());
          } else {
            return io;
          }
        } catch (Throwable err) {
          handlers.handleOrRethrow(err);
        }
      }
    }
  }

  private static <T> CompletableFuture<T> executeAsync(CompletableFuture<T> promise,
                                                       Async<T> async) {
    async.cb.run(result -> {
      if (result.isLeft()) {
        promise.completeExceptionally(result.getLeft());
      } else {
        promise.complete(result.getRight());
      }
    });
    return promise;
  }
}

class ErrorHandlers<T> {

  @Override
  public String toString() {
    return "ErrorHandlers(" + (isEmpty() ? "<empty>" : "<rules>") + ")";
  }

  boolean isEmpty() {
    return handlers.isEmpty();
  }

  private LList<Function<Throwable, Optional<IO<T>>>> handlers = LNil.instance();

  void add(Function<Throwable, Optional<IO<T>>> handler) {
    handlers = handlers.prepend(handler);
  }

  void removeLast() {
    if (handlers.isEmpty()) {
      throw new NoSuchElementException();
    }

    handlers = ((LCons<Function<Throwable, Optional<IO<T>>>>) handlers).tail;
  }

  Optional<IO<T>> handle(Throwable err) {

    LList<Function<Throwable, Optional<IO<T>>>> h = handlers;

    while (h.isNotEmpty()) {
      final Optional<IO<T>> res =
          ((LCons<Function<Throwable, Optional<IO<T>>>>) h).head.apply(err);

      if (res.isPresent()) {
        return res;
      }

      h = ((LCons<Function<Throwable, Optional<IO<T>>>>) h).tail;
    }
    return Optional.empty();
  }

  IO<T> handleOrRethrow(Throwable err) throws Throwable {
    return handle(err).orElseThrow(() -> err);
  }
}
