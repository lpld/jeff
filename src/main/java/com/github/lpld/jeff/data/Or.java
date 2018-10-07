package com.github.lpld.jeff.data;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * @author leopold
 * @since 7/10/18
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class Or<L, R> {

  @SuppressWarnings("unchecked")
  public static <L, R> Or<L, R> left(L value) {
    return (Or<L, R>) new Left<>(value);
  }

  @SuppressWarnings("unchecked")
  public static <L, R> Or<L, R> right(R value) {
    return (Or<L, R>) new Right<>(value);
  }
}

@RequiredArgsConstructor
class Left<L> extends Or<L, Object> {

  final L value;
}

@RequiredArgsConstructor
class Right<R> extends Or<Object, R> {

  final R value;
}
