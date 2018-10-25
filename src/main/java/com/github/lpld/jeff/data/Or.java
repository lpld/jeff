package com.github.lpld.jeff.data;

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
