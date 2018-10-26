package com.github.lpld.jeff.data;

import com.github.lpld.jeff.functions.Fn;
import com.github.lpld.jeff.functions.Fn0;
import com.github.lpld.jeff.functions.Fn2;
import com.github.lpld.jeff.functions.Run1;

import java.util.NoSuchElementException;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * @author leopold
 * @since 7/10/18
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class Or<L, R> {

  @SuppressWarnings("unchecked")
  public static <L, R> Or<L, R> Left(L value) {
    return (Or<L, R>) new Left<>(value);
  }

  @SuppressWarnings("unchecked")
  public static <L, R> Or<L, R> Right(R value) {
    return (Or<L, R>) new Right<>(value);
  }

  public boolean isLeft() {
    return this instanceof Left;
  }

  public boolean isRight() {
    return this instanceof Right;
  }

  public L getLeft() {
    if (isLeft()) {
      return ((Left<L>) this).value;
    }
    throw new NoSuchElementException();
  }

  public R getRight() {
    if (isRight()) {
      return ((Right<R>) this).value;
    }
    throw new NoSuchElementException();
  }

  public <T> T fold(Fn<L, T> l, Fn<R, T> r) {
    return isLeft() ? l.ap(getLeft()) : r.ap(getRight());
  }

  public <L1, R1> Or<L1, R1> transform(Fn<L, L1> l, Fn<R, R1> r) {
    return fold(l.andThen(Or::Left), r.andThen(Or::Right));
  }

  public void forEach(Run1<L> l, Run1<R> r) {
    fold(l.toFn(), r.toFn());
  }

  public static <T> T flatten(Or<T, T> or) {
    return or.fold(Fn.id(), Fn.id());
  }

  /**
   * A bit similar to {@link java.util.Optional#ofNullable}:
   * if {@code l} is null, construct {@code Right(r)}, otherwise construct {@code Left(l)}
   */
  public static <L, R> Or<L, R> of(L l, R r) {
    return l == null ? Right(r) : Left(l);
  }
}

@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor
class Left<L> extends Or<L, Object> {

  final L value;

  @Override
  public String toString() {
    return "Left(" + value + ")";
  }
}

@EqualsAndHashCode(callSuper = false)
@RequiredArgsConstructor
class Right<R> extends Or<Object, R> {

  final R value;

  @Override
  public String toString() {
    return "Right(" + value + ")";
  }
}
