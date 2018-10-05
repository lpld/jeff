package com.github.lpld.jeff;

import java.util.function.Supplier;

/**
 * @author leopold
 * @since 5/10/18
 */
@SuppressWarnings("WeakerAccess")
public abstract class Trampoline<T> {

  public static <T> Trampoline<T> more(Supplier<Trampoline<T>> call)  {
    return new More<>(call);
  }

  public static <T> Trampoline<T> done(T value)  {
    return new Done<>(value);
  }

  public T eval() {
    Trampoline<T> t = this;
    while (!(t instanceof Done)) {
      t = ((More<T>) t).call.get();
    }
    return ((Done<T>) t).value;
  }
}

class Done<T> extends Trampoline<T> {
  final T value;

  Done(T value) {
    this.value = value;
  }
}

class More<T> extends Trampoline<T> {
  final Supplier<Trampoline<T>> call;

  More(Supplier<Trampoline<T>> call) {
    this.call = call;
  }
}
