package com.github.lpld.jeff;

import com.github.lpld.jeff.functions.F;

import java.util.function.Supplier;

/**
 * @author leopold
 * @since 5/10/18
 */
public interface Eval<T> {

  T getValue();

  default <U> Eval<U> map(F<T, U> f) {
    return flatMap(f.andThen(Eval::now));
  }

  default <U> Eval<U> flatMap(F<T, Eval<U>> f) {
    return Eval.always(() -> f.applyUnsafe(getValue()).getValue());
  }

  static <T> Eval<T> now(T value) {
    return new Now<>(value);
  }

  static <T> Eval<T> always(Supplier<T> thunk) {
    return new Always<>(thunk);
  }

  static <T> Eval<T> later(Supplier<T> thunk) {
    return new Later<>(thunk);
  }
}

class Now<T> implements Eval<T> {

  private final T value;

  Now(T value) {
    this.value = value;
  }

  @Override
  public T getValue() {
    return value;
  }
}

class Always<T> implements Eval<T> {

  private final Supplier<T> thunk;

  Always(Supplier<T> thunk) {
    this.thunk = thunk;
  }

  @Override
  public T getValue() {
    return thunk.get();
  }
}

class Later<T> implements Eval<T> {

  private volatile boolean computed = false;
  private T value = null;
  private Supplier<T> thunk;

  Later(Supplier<T> thunk) {
    this.thunk = thunk;
  }

  @Override
  public T getValue() {
    if (computed) {
      return value;
    }

    synchronized (this) {
      if (!computed) {
        value = thunk.get();
        thunk = null;
        computed = true;
      }
      return value;
    }
  }
}
