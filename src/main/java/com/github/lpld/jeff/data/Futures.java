package com.github.lpld.jeff.data;

import com.github.lpld.jeff.functions.Xn0;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * @author leopold
 * @since 12/10/18
 */
public final class Futures {

  public static <T> CompletableFuture<T> run(Xn0<T> run, Executor executor) {
    final CompletableFuture<T> future = new CompletableFuture<>();
    executor.execute(() -> {
      try {
        future.complete(run.ap());
      } catch (Throwable ex) {
        future.completeExceptionally(ex);
      }
    });
    return future;
  }
}
