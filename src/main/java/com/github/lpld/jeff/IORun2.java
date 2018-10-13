package com.github.lpld.jeff;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.github.lpld.jeff.IO.Pure;

/**
 * @author leopold
 * @since 13/10/18
 */
public class IORun2 {

  public static <T> T run(IO<T> io) throws ExecutionException, InterruptedException {
    return runAsyncInternal(io).get();
  }

  private static <T, U, V> CompletableFuture<T> runAsyncInternal(IO<T> io) {
//    System.out.println(1);

    while (true) {
      io = unwrap(io);

      if (io instanceof Pure) {
        return CompletableFuture.completedFuture(((Pure<T>) io).pure);
      }

      if (io instanceof Async) {
        return executeAsync(new CompletableFuture<>(), (Async<T>) io);
        // todo: errors
      }

      if (io instanceof Bind) {
        final Bind<U, T> bind = (Bind<U, T>) io;

        final IO<U> source = unwrap(bind.source);
        if (source instanceof Async) {
          final CompletableFuture<U> promise = new CompletableFuture<>();

          // we want to register thenApply and thenCompose callbacks
          // before the async callback is called, because we want to remain
          // in async callback's thread. If we don't do this and if async callback
          // is very short, we might call "thenApply" on a future that is already completed,
          // and thenApply will be executed in current thread, which is not desirable behavior.
          final CompletableFuture<T> result = promise
              .thenApply(bind.f::ap)
              .thenCompose(IORun2::runAsyncInternal);
          executeAsync(promise, (Async<U>) source);
          return result;
          // todo: errors
        }

        if (source instanceof Pure) {
          io = bind.f.ap(((Pure<U>) source).pure);
        } else if (source instanceof Bind) {
          final Bind<V, U> bind2 = (Bind<V, U>) source;
          io = bind2.source.flatMap(a -> bind2.f.ap(a).flatMap(bind.f));
        }
      }
    }
  }

  // Handle Suspend and Delay
  private static <T> IO<T> unwrap(IO<T> io) {
    try {
      while (io instanceof Suspend) {
        io = ((Suspend<T>) io).resume.ap();
      }

      if (io instanceof Delay) {
        io = Pure(((Delay<T>) io).thunk.ap());
      }

      return io;
    } catch (Throwable t) {
      // todo: handle Fail and Recover cases.
      return WrappedError.throwWrapped(t);
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
