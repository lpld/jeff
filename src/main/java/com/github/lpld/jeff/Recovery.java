package com.github.lpld.jeff;


import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;

/**
 * @author leopold
 * @since 5/10/18
 */
public class Recovery {

  @SafeVarargs
  public static <T> Function<Throwable, Optional<T>> rules(RecoveryRule<T>... rules) {
    return throwable -> Arrays.stream(rules)
        .filter(rule -> rule.predicate.test(throwable))
        .findFirst()
        .map(rule -> rule.onError.get());
  }

  public static <E extends Throwable> ThrowablePredicate<E> on(Class<E> errorClass) {
    return new ThrowablePredicate<>(errorClass, (e) -> true);
  }

  @RequiredArgsConstructor
  static class ThrowablePredicate<E extends Throwable> {

    private final Class<E> clazz;
    private final Predicate<E> predicate;

    public ThrowablePredicate<E> and(Predicate<E> predicate) {
      return new ThrowablePredicate<>(clazz, this.predicate.and(predicate));
    }

    public <T> RecoveryRule<T> doReturn(Supplier<T> supplier) {
      return new RecoveryRule<>(this, supplier);
    }

    public <T> RecoveryRule<T> doReturn(T value) {
      return doReturn(() -> value);
    }

    @SuppressWarnings("unchecked")
    boolean test(Throwable throwable) {
      return clazz.isInstance(throwable) && predicate.test((E) throwable);
    }
  }

  @RequiredArgsConstructor
  static class RecoveryRule<T> {
    private final ThrowablePredicate<?> predicate;
    final Supplier<T> onError;
  }
}


