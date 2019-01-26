package com.github.lpld.jeff;

import com.github.lpld.jeff.functions.Fn0;
import com.github.lpld.jeff.functions.Xn0;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author leopold
 * @since 12/10/18
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Futures {

  static <T> CompletableFuture<T> failed(Throwable err) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(err);
    return future;
  }

  static <T> CompletableFuture<T> completed(CompletableFuture<T> promise, T value) {
    promise.complete(value);
    return promise;
  }

  static <T> CompletableFuture<T> cancelled(CompletableFuture<T> promise) {
    promise.cancel(false);
    return promise;
  }

  @SuppressWarnings("unchecked")
  static <U> CompletableFuture<U> cast(CompletableFuture<?> sndPromise) {
    return (CompletableFuture<U>) sndPromise;
  }

  public static <T> CompletableFuture<T> run(Xn0<T> run, Executor executor) {
    // Arrr I hate JDK guys for this!
    // Why not just CompletableFuture.supplyAsync(run::ap, executor) ???

    final CompletableFuture<T> future = new CompletableFuture<>();
    executor.execute(() -> {
      try {
        future.complete(run.ap());
      } catch (Throwable err) {
        future.completeExceptionally(err);
      }
    });
    return future;
  }
}
