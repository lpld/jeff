package com.github.lpld.jeff;

/**
 * @author leopold
 * @since 6/10/18
 */
abstract class LList<T> {

  public LList<T> prepend(T value) {
    return new LCons<>(value, this);
  }

  boolean isNotEmpty() {
    return this instanceof LCons;
  }
}

class LNil extends LList<Object> {

  private static final LNil INSNTANCE = new LNil();

  @SuppressWarnings("unchecked")
  static <T> LList<T> instance() {
    return (LList<T>) INSNTANCE;
  }
}

class LCons<T> extends LList<T> {

  final T value;
  final LList<T> tail;

  public LCons(T value, LList<T> tail) {
    this.value = value;
    this.tail = tail;
  }

}
