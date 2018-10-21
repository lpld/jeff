package com.github.lpld.jeff;

import com.github.lpld.jeff.LList.LCons;
import com.github.lpld.jeff.LList.LNil;
import com.github.lpld.jeff.data.Futures;
import com.github.lpld.jeff.functions.Fn;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.github.lpld.jeff.IO.Pure;

/**
 * @author leopold
 * @since 20/10/18
 */
public class IORun3 {

  @SuppressWarnings("Duplicates")
  static <T, U, V> CompletableFuture<T> runAsync(IO<T> io) {

    final CallStack<T> stack = new CallStack<>();

    while (true) {
      try {
        io = unwrap(io, stack, Fn.id());

        if (io instanceof Pure) {
          return CompletableFuture.completedFuture(((Pure<T>) io).pure);
        }

        if (io instanceof Bind) {
          stack.push();
          final Bind<U, T> bind = (Bind<U, T>) io;
          final IO<U> source = unwrap(bind.source, stack, u -> u.flatMap(bind.f));

          if (source instanceof Pure) {
            io = bind.f.ap(((Pure<U>) source).pure);
          } else if (source instanceof Bind) {
            final Bind<V, U> bind2 = (Bind<V, U>) source;
            io = bind2.source.flatMap(a -> bind2.f.ap(a).flatMap(bind.f));
          }
        } else {
          stack.pull();
        }
      } catch (Throwable err) {
        Optional<IO<T>> result = stack.tryHandle(err);

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
        throw ((Fail<T>) io).err;
      } else if (io instanceof Recover) {
        IO<T> finalIo = io;
        stack.addRule(err -> ((Recover<T>) finalIo).recover.apply(err).map(f::ap));
        io = ((Recover<T>) io).io;
      } else if (io instanceof Suspend) {
        io = ((Suspend<T>) io).resume.ap();
      } else if (io instanceof Delay) {
        return Pure(((Delay<T>) io).thunk.ap());
      } else {
        return io;
      }
    }
  }
}

class CallStack<T> {

  private CallItem<T> top = new CallItem<>(null);

  void push() {
    top.callCount++;
  }

  void pull() {
    if (top.callCount > 0) {
      top.callCount--;
    } else {
      top = top.prev;
    }
  }

  void addRule(Function<Throwable, Optional<IO<T>>> rule) {
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
