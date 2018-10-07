package com.github.lpld.jeff.stream.attempt1;

import com.github.lpld.jeff.IO;
import com.github.lpld.jeff.data.P;
import com.github.lpld.jeff.functions.F;
import com.github.lpld.jeff.functions.F0;

import java.util.Optional;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * @author leopold
 * @since 5/10/18
 */
public abstract class Stream<T> {

  public static <T> Stream<T> empty() {
    return Nil.instance();
  }

  public static <T, S> Stream<T> unfold(S z, F<S, Optional<P<T, S>>> f) {
    // we want to defer first step:
    return defer(() -> unfoldEager(z, f));
  }

  // Unfold that eagerly evaluates the first step:
  private static <T, S> Stream<T> unfoldEager(S z, F<S, Optional<P<T, S>>> f) throws Throwable {
    return f.apply(z)
        .map(p -> ((Stream<T>) new Cons<>(IO.pure(p._1), IO.delay(() -> unfoldEager(p._2, f)))))
        .orElseGet(Nil::instance);
  }

  @SafeVarargs
  public static <T> Stream<T> of(T... elements) {
    throw new UnsupportedOperationException();
  }

  public static <T> Stream<T> defer(F0<Stream<T>> streamEval) {
    // Defer is implemented in terms of concat (which is lazy)
    return new Concat<>(IO.delay(streamEval), IO.pure(Nil.instance()));
  }

  public abstract <U> Stream<U> flatMap(F<T, Stream<U>> f);

  public <U> Stream<U> map(F<T, U> f) {
    return flatMap(f.andThen(Stream::of));
  }

  public abstract <U> Stream<U> mapEval(F<T, IO<U>> f);
}

@RequiredArgsConstructor
class Cons<T> extends Stream<T> {

  final IO<T> head;
  final IO<Stream<T>> tail;

  @Override
  public <U> Stream<U> flatMap(F<T, Stream<U>> f) {
    return new Concat<>(
        head.map(f),
        tail.map(s -> s.flatMap(f))
    );
  }

  @Override
  public <U> Stream<U> mapEval(F<T, IO<U>> f) {
    return new Cons<>(
        head.flatMap(f),
        tail.map(t -> t.mapEval(f))
    );
  }
}

@RequiredArgsConstructor
class Concat<T> extends Stream<T> {

  private final IO<Stream<T>> stream1;
  private final IO<Stream<T>> stream2;

  @Override
  public <U> Stream<U> flatMap(F<T, Stream<U>> f) {
    return new Concat<>(
        stream1.map(s -> s.flatMap(f)),
        stream2.map(s -> s.flatMap(f))
    );
  }

  @Override
  public <U> Stream<U> mapEval(F<T, IO<U>> f) {
    return new Concat<>(
        stream1.map(s -> s.mapEval(f)),
        stream2.map(s -> s.mapEval(f))
    );
  }
}

@NoArgsConstructor(access = AccessLevel.PRIVATE)
class Nil extends Stream<Object> {

  private static final Nil INSTANCE = new Nil();

  @SuppressWarnings("unchecked")
  static <T> Stream<T> instance() {
    return (Stream<T>) INSTANCE;
  }

  @Override
  public <U> Stream<U> flatMap(F<Object, Stream<U>> f) {
    return Nil.instance();
  }

  @Override
  public <U> Stream<U> mapEval(F<Object, IO<U>> f) {
    return Nil.instance();
  }
}
