package com.github.lpld.jeff;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

/**
 * @author leopold
 * @since 6/10/18
 */
public abstract class LList<T> {

  public LList<T> prepend(T value) {
    return new LCons<>(value, this);
  }

  public boolean isNotEmpty() {
    return this instanceof LCons;
  }

  public boolean isEmpty() {
    return this instanceof LNil;
  }

  @SafeVarargs
  public static <T> LList<T> of(T... ts) {
    LList<T> l = LNil.instance();
    for (int i = ts.length - 1; i >= 0; i--) {
      l = l.prepend(ts[i]);
    }
    return l;
  }

  public static class LNil extends LList<Object> {

    public static final LNil INSNTANCE = new LNil();

    @SuppressWarnings("unchecked")
    public static <T> LList<T> instance() {
      return (LList<T>) INSNTANCE;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LNil;
    }

    @Override
    public String toString() {
      return "Nil";
    }
  }

  @EqualsAndHashCode(callSuper = false)
  @RequiredArgsConstructor
  public static class LCons<T> extends LList<T> {
    final T head;
    final LList<T> tail;

    @Override
    public String toString() {
      return head + "::" + tail;
    }
  }
}

