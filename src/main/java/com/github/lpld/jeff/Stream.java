package com.github.lpld.jeff;

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

import static com.github.lpld.jeff.IO.Delay;
import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.IO.Pure;
import static com.github.lpld.jeff.IO.Suspend;
import static com.github.lpld.jeff.IOMethods.flatMap2;
import static com.github.lpld.jeff.data.Pr.Pr;

/**
 * @author leopold
 * @since 5/10/18
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class Stream<T> {

  // FACTORY METHODS:

  public static <T> Stream<T> Nil() {
    return Nil.instance();
  }

  public static <T, S> Stream<T> unfold(S z, Fn<S, Optional<Pr<T, S>>> f) {
    // we want to defer first step:
    return defer(() -> unfoldEager(z, f));
  }

  public static <T> Stream<T> Cons(T head, Stream<T> tail) {
    return SCons(Pure(head), Pure(tail));
  }

  public static <T> Stream<T> Concat(Stream<T> s1, Stream<T> s2) {
    return SConcat(Pure(s1), Pure(s2));
  }

  @SafeVarargs
  public static <T> Stream<T> of(T... elements) {
    return ofAll(Arrays.asList(elements));
  }

  public static <T> Stream<T> ofAll(Iterable<T> elems) {
    return unfold(
        elems.iterator(),
        iterator -> Optional.of(iterator)
            .filter(Iterator::hasNext)
            .map(it -> Pr(it.next(), it))
    );
  }

  // Unfold that eagerly evaluates the first step:
  private static <T, S> Stream<T> unfoldEager(S z, Fn<S, Optional<Pr<T, S>>> f) throws Throwable {
    return f.ap(z)
        .map(p -> SCons(Pure(p._1), IO(() -> unfoldEager(p._2, f))))
        .orElseGet(Stream::Nil);
  }

  private static <T> Stream<T> defer(Fn0<Stream<T>> streamEval) {
    // Defer is implemented in terms of concat (which is lazy)
    return SConcat(Delay(streamEval), Pure(Nil()));
  }

  // COMBINATOR METHODS:

  public abstract <U> Stream<U> flatMap(Fn<T, Stream<U>> f);

  public <U> Stream<U> map(Fn<T, U> f) {
    return flatMap(f.andThen(Stream::of));
  }

  public abstract <U> Stream<U> mapEval(Fn<T, IO<U>> f);

  public abstract <R> IO<R> foldLeft(R z, Fn2<R, T, R> f);

  public abstract <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f);

  public <R> IO<R> foldRight(R z, Fn2<T, R, R> f) {
    return foldRight(Pure(z), (t, ior) -> ior.map(r -> f.ap(t, r)));
  }

  static <T> Stream<T> SCons(IO<T> head, IO<Stream<T>> tail) {
    return new Cons<>(head, tail);
  }

  static <T> Stream<T> SConcat(IO<Stream<T>> s1, IO<Stream<T>> s2) {
    return new Concat<>(s1, s2);
  }
}

@RequiredArgsConstructor
class Cons<T> extends Stream<T> {

  final IO<T> head;
  final IO<Stream<T>> tail;

  @Override
  public <U> Stream<U> flatMap(Fn<T, Stream<U>> f) {
    return SConcat(head.map(f),
                   tail.map(s -> s.flatMap(f)));
  }

  @Override
  public <U> Stream<U> mapEval(Fn<T, IO<U>> f) {
    return SCons(head.flatMap(f),
                 tail.map(t -> t.mapEval(f)));
  }

  @Override
  public <R> IO<R> foldLeft(R z, Fn2<R, T, R> f) {
    return transform((h, t) -> t.foldLeft(f.ap(z, h), f));
  }

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {
    return transform((h, t) -> f.ap(h, t.foldRight(z, f)));
  }

  private <U> IO<U> transform(Fn2<T, Stream<T>, IO<U>> f) {
    return flatMap2(head, tail, f);
  }
}

@RequiredArgsConstructor
class Concat<T> extends Stream<T> {

  final IO<Stream<T>> stream1;
  final IO<Stream<T>> stream2;

  @Override
  public <U> Stream<U> flatMap(Fn<T, Stream<U>> f) {
    return SConcat(stream1.map(s -> s.flatMap(f)),
                   stream2.map(s -> s.flatMap(f)));
  }

  @Override
  public <U> Stream<U> mapEval(Fn<T, IO<U>> f) {
    return SConcat(stream1.map(s -> s.mapEval(f)),
                   stream2.map(s -> s.mapEval(f)));
  }

  @Override
  public <R> IO<R> foldLeft(R z, Fn2<R, T, R> f) {
    return transform((s1, s2) -> s1.foldLeft(z, f).flatMap(zz -> s2.foldLeft(zz, f)));
  }

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {
    return transform((s1, s2) -> s1.foldRight(Suspend(() -> s2.foldRight(z, f)), f));
  }

  private <U> IO<U> transform(Fn2<Stream<T>, Stream<T>, IO<U>> f) {
    return flatMap2(stream1, stream2, f);
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
    return instance();
  }

  @Override
  public <U> Stream<U> mapEval(Fn<Object, IO<U>> f) {
    return instance();
  }

  @Override
  public <R> IO<R> foldLeft(R z, Fn2<R, Object, R> f) {
    return Pure(z);
  }

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<Object, IO<R>, IO<R>> f) {
    return z;
  }
}
