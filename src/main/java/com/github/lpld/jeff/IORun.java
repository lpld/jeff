package com.github.lpld.jeff;

import com.github.lpld.jeff.LList.LCons;
import com.github.lpld.jeff.LList.LNil;
import com.github.lpld.jeff.data.Futures;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import lombok.RequiredArgsConstructor;

import static com.github.lpld.jeff.IO.Pure;

/**
 * @author leopold
 * @since 13/10/18
 */
class IORun {

  static <T, U, V> CompletableFuture<T> runAsync(IO<T> io) {
    final BindStack<T> bindStack = new BindStack<>();

    while (true) {
      try {
        io = unwrap(io, bindStack);
      } catch (Throwable err) {
        return Futures.failed(err);
      }

      if (io instanceof Pure) {
        return CompletableFuture.completedFuture(((Pure<T>) io).pure);
      }

      if (io instanceof Async) {
        return handleAsyncErrors(executeAsync(new CompletableFuture<>(), (Async<T>) io), bindStack);
      }

      try {
        if (io instanceof Bind) {
          final Bind<U, T> bind = (Bind<U, T>) io;

          final ErrorRulesHolder<U> uRules = new ErrorRulesHolder<>();
          final IO<U> source = unwrap(bind.source, uRules);

          if (uRules.rules.isEmpty()) {
            bindStack.push();
          } else {
            bindStack.pushWithRecovery(t -> uRules.tryHandle(t).map(u -> u.flatMap(bind.f)));
          }

          if (source instanceof Async) {
            final CompletableFuture<U> promise = new CompletableFuture<>();

            // we want to register `thenCompose` callback before the async callback is called,
            // because we want to remain in async callback's thread. If we don't do this and if
            // async callback is very short, we might call `thenCompose` on a future that is already
            // completed, and `thenCompose` will be executed in current thread, which is not a
            // desirable behavior.
            final CompletableFuture<T> result =
                promise.thenCompose(io1 -> runAsync(bind.f.ap(io1)));

            executeAsync(promise, (Async<U>) source);
            return handleAsyncErrors(result, bindStack);
          }

          if (source instanceof Pure) {
            io = bind.f.ap(((Pure<U>) source).pure);
          } else if (source instanceof Bind) {
            final Bind<V, U> bind2 = (Bind<V, U>) source;
            io = bind2.source.flatMap(a -> bind2.f.ap(a).flatMap(bind.f));
          }
        } else {
          bindStack.remove();
        }
      } catch (Throwable err) {
        final Optional<IO<T>> handleRes = bindStack.tryHandle(err);

        if (handleRes.isPresent()) {
          io = handleRes.get();
        } else {
          return Futures.failed(err);
        }
      }
    }
  }

  private static <T> CompletableFuture<T> handleAsyncErrors(CompletableFuture<T> tAsync,
                                                            ErrorHandler<T> handler) {
    return tAsync.handle((result, err) -> {
      if (err == null) {
        return CompletableFuture.completedFuture(result);
      } else {
        // todo: what thread are we in??
        return handler.tryHandle(err)
            .map(IORun::runAsync)
            .orElseGet(() -> Futures.failed(err));
      }
    }).thenCompose(Function.identity());
  }

  /**
   * Handle Fail, Recover, Suspend and Delay cases.
   */
  private static <T> IO<T> unwrap(IO<T> io, ErrorHandler<T> errorHandler) throws Throwable {
    while (true) {
      try {
        if (io instanceof Fail) {
          throw ((Fail<T>) io).err;
        } else if (io instanceof Recover) {
          errorHandler.addRecoveryRule(((Recover<T>) io).recover);
          io = ((Recover<T>) io).io;
        } else if (io instanceof Suspend) {
          io = ((Suspend<T>) io).resume.ap();
        } else if (io instanceof Delay) {
          return Pure(((Delay<T>) io).thunk.ap());
        } else {
          return io;
        }
      } catch (Throwable err) {
        io = errorHandler.tryHandle(err).orElseThrow(() -> err);
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

/**
 * Entity that is capable of handling errors and transforming them into IO values of type `T`.
 */
interface ErrorHandler<T> {

  Optional<IO<T>> tryHandle(Throwable err);

  void addRecoveryRule(Function<Throwable, Optional<IO<T>>> rule);
}

/**
 * Just a container for recovery rules.
 */
class ErrorRulesHolder<T> implements ErrorHandler<T> {

  LList<Function<Throwable, Optional<IO<T>>>> rules = LNil.instance();

  @Override
  public void addRecoveryRule(Function<Throwable, Optional<IO<T>>> rule) {
    rules = rules.prepend(rule);
  }

  @Override
  public Optional<IO<T>> tryHandle(Throwable err) {
    LList<Function<Throwable, Optional<IO<T>>>> rule = this.rules;

    while (rule.isNotEmpty()) {

      final Optional<IO<T>> result =
          ((LCons<Function<Throwable, Optional<IO<T>>>>) rule).head
              .apply(err);

      if (result.isPresent()) {
        return result;
      }

      rule = ((LCons<Function<Throwable, Optional<IO<T>>>>) rule).tail;
    }

    return Optional.empty();
  }
}

/**
 * Stack for keeping track of `Bind` calls and managing error handlers.
 *
 * For efficiency this stack
 */
class BindStack<T> implements ErrorHandler<T> {

  private StackItem<T> top = new Start<>();

  /**
   * Push new bind call.
   */
  void push() {
    top.callsCount++;
  }

  /**
   * Push new bind call with recovery rule.
   */
  void pushWithRecovery(Function<Throwable, Optional<IO<T>>> rule) {
    top = new Next<>(top);
    top.addRecoveryRule(rule);
  }

  /**
   * Remove the latest bind call.
   */
  void remove() {
    if (top.callsCount > 0) {
      top.callsCount--;
    } else {
      top = ((Next<T>) top).prev;
    }
  }

  @Override
  public void addRecoveryRule(Function<Throwable, Optional<IO<T>>> rule) {
    top.addRecoveryRule(rule);
  }

  /**
   * Find a rule that matches this error and apply it.
   */
  public Optional<IO<T>> tryHandle(Throwable err) {
    StackItem<T> item = top;

    while (item != null) {
      final Optional<IO<T>> result = item.tryHandle(err);

      if (result.isPresent()) {
        return result;
      }

      item = item.hasPrev() ? ((Next<T>) item).prev : null;
    }

    return Optional.empty();
  }
}

/**
 * Item of a bind stack.
 */
abstract class StackItem<T>
    extends ErrorRulesHolder<T>
    implements ErrorHandler<T> {

  int callsCount = 0;

  boolean hasPrev() {
    return this instanceof Next;
  }
}

/**
 * First item of BindStack, that doesn't have prev.
 */
class Start<T> extends StackItem<T> {

}

/**
 * Item of BindStack that holds a reference to previous item.
 */
@RequiredArgsConstructor
class Next<T> extends StackItem<T> {

  final StackItem<T> prev;
}
