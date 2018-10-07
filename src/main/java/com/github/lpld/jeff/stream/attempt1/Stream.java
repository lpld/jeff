package com.github.lpld.jeff.stream.attempt1;

import com.github.lpld.jeff.IO;
import com.github.lpld.jeff.data.Pr;
import com.github.lpld.jeff.functions.Fn;
import com.github.lpld.jeff.functions.Fn0;
import com.github.lpld.jeff.functions.Fn2;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * @author leopold
 * @since 5/10/18
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class Stream<T> {

  // FACTORY METHODS:

  public static <T> Stream<T> empty() {
    return Nil.instance();
  }

  public static <T, S> Stream<T> unfold(S z, Fn<S, Optional<Pr<T, S>>> f) {
    // we want to defer first step:
    return defer(() -> unfoldEager(z, f));
  }

  @SafeVarargs
  public static <T> Stream<T> of(T... elements) {
    return ofAll(Arrays.asList(elements));
  }

  public static <T> Stream<T> ofAll(Iterable<T> elems) {
    return unfold(elems.iterator(), iterator -> Optional.of(iterator)
        .filter(Iterator::hasNext)
        .map(it -> Pr.of(it.next(), it))
    );
  }

  // Unfold that eagerly evaluates the first step:
  private static <T, S> Stream<T> unfoldEager(S z, Fn<S, Optional<Pr<T, S>>> f) throws Throwable {
    return f.apply(z)
        .map(p -> ((Stream<T>) new Cons<>(IO.pure(p._1), IO.delay(() -> unfoldEager(p._2, f)))))
        .orElseGet(Nil::instance);
  }

  private static <T> Stream<T> defer(Fn0<Stream<T>> streamEval) {
    // Defer is implemented in terms of concat (which is lazy)
    return new Concat<>(IO.delay(streamEval), IO.pure(Nil.instance()));
  }

  // COMBINATOR METHODS:

  public abstract <U> Stream<U> flatMap(Fn<T, Stream<U>> f);

  public <U> Stream<U> map(Fn<T, U> f) {
    return flatMap(f.andThen(Stream::of));
  }

  public abstract <U> Stream<U> mapEval(Fn<T, IO<U>> f);

  public <R> IO<R> foldLeft(R z, Fn2<R, T, R> f) {
    if (this instanceof Nil) {
      return IO.pure(z);
    }

    if (this instanceof Cons) {
      final IO<T> head = ((Cons<T>) this).head;
      final IO<Stream<T>> tail = ((Cons<T>) this).tail;

      return head.flatMap(h -> {
        final R zz = f.apply(z, h);
        return tail.flatMap(t -> t.foldLeft(zz, f));
      });
    }

    if (this instanceof Concat) {
      final IO<Stream<T>> stream1 = ((Concat<T>) this).stream1;
      final IO<Stream<T>> stream2 = ((Concat<T>) this).stream2;

      return stream1.flatMap(
          s1 -> s1.foldLeft(z, f).flatMap(
          zz -> stream2.flatMap(
          s2 -> s2.foldLeft(zz, f)
      )));
    }

    throw new IllegalStateException();
  }
}

@RequiredArgsConstructor
class Cons<T> extends Stream<T> {

  final IO<T> head;
  final IO<Stream<T>> tail;

  @Override
  public <U> Stream<U> flatMap(Fn<T, Stream<U>> f) {
    return new Concat<>(
        head.map(f),
        tail.map(s -> s.flatMap(f))
    );
  }

  @Override
  public <U> Stream<U> mapEval(Fn<T, IO<U>> f) {
    return new Cons<>(
        head.flatMap(f),
        tail.map(t -> t.mapEval(f))
    );
  }

  @Override
  public <R> IO<R> foldLeft(R z, Fn2<R, T, R> f) {
    return super.foldLeft(z, f);
  }
}

@RequiredArgsConstructor
class Concat<T> extends Stream<T> {

  final IO<Stream<T>> stream1;
  final IO<Stream<T>> stream2;

  @Override
  public <U> Stream<U> flatMap(Fn<T, Stream<U>> f) {
    return new Concat<>(
        stream1.map(s -> s.flatMap(f)),
        stream2.map(s -> s.flatMap(f))
    );
  }

  @Override
  public <U> Stream<U> mapEval(Fn<T, IO<U>> f) {
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
  public <U> Stream<U> flatMap(Fn<Object, Stream<U>> f) {
    return Nil.instance();
  }

  @Override
  public <U> Stream<U> mapEval(Fn<Object, IO<U>> f) {
    return Nil.instance();
  }
}
