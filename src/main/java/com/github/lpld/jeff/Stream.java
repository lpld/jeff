package com.github.lpld.jeff;

import com.github.lpld.jeff.LList.LNil;
import com.github.lpld.jeff.data.Pr;
import com.github.lpld.jeff.functions.Fn;
import com.github.lpld.jeff.functions.Fn2;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.IO.Pure;
import static com.github.lpld.jeff.IO.Suspend;

/**
 * @author leopold
 * @since 10/10/18
 */
public abstract class Stream<T> {

  public static <T> Stream<T> Nil() {
    return Nil.instance();
  }

  public static <T> Stream<T> SCons(IO<T> head, Stream<T> tail) {
    return new Cons<>(head, tail);
  }

  public static <T> Stream<T> Cons(T head, Stream<T> tail) {
    return SCons(Pure(head), tail);
  }

  public static <T> Stream<T> Defer(IO<Stream<T>> stream) {
    return new Defer<>(stream);
  }

  public static <T, S> Stream<T> unfold(S z, Fn<S, Optional<Pr<T, S>>> f) {
    // we want to defer first step:
    return Defer(IO(() -> unfoldEager(z, f)));
  }


  private static <T, M> Stream<T> fromList(List<M> list, Function<M, IO<T>> f) {
    Stream<T> s = Nil();
    for (int i = list.size() - 1; i >= 0; i--) {
      s = SCons(f.apply(list.get(i)), s);
    }
    return s;
  }

  private static <T, M> Stream<T> fromIterable(Iterable<M> iterable, Function<M, IO<T>> f) {
    return fromIterator(iterable.iterator(), f).run();
  }

  private static <T, M> IO<Stream<T>> fromIterator(Iterator<M> iterator, Function<M, IO<T>> f) {
    return Suspend(() -> {

      if (!iterator.hasNext()) {
        return Pure(Nil());
      }
      final M next = iterator.next();
      return fromIterator(iterator, f).map(s -> SCons(f.apply(next), s));
    });
  }

  @SafeVarargs
  public static <T> Stream<T> of(T... elements) {
    return fromList(Arrays.asList(elements), IO::Pure);
  }

  @SafeVarargs
  public static <T> Stream<T> Stream(T... elements) {
    return of(elements);
  }

  @SafeVarargs
  public static <T> Stream<T> eval(IO<T>... ios) {
    return fromList(Arrays.asList(ios), Function.identity());
  }

  public static <T> Stream<T> ofAll(Iterable<T> elems) {
    return elems instanceof List ? fromList(((List<T>) elems), IO::Pure)
                                 : fromIterable(elems, IO::Pure);
  }

  // Unfold that eagerly evaluates the first step:
  private static <T, S> Stream<T> unfoldEager(S z, Fn<S, Optional<Pr<T, S>>> f) throws Throwable {
    return f.ap(z)
        .map(p -> SCons(Pure(p._1), Defer(IO(() -> unfoldEager(p._2, f)))))
        .orElseGet(Stream::Nil);
  }

  public abstract <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f);

  public <R> IO<R> foldRight(R z, Fn2<T, R, R> f) {
    return foldRight(Pure(z), (t, ior) -> ior.map(r -> f.ap(t, r)));
  }

  public abstract <R> IO<R> foldLeft(R z, Fn2<R, T, R> f);

  public Stream<T> append(Stream<T> other) {
    return Defer(foldRight(other, Stream::Cons));
  }

  public abstract Stream<T> take(int n);

  public abstract Stream<T> drop(int n);

  public Stream<T> head() {
    return take(1);
  }

  public Stream<T> tail() {
    return drop(1);
  }

  public abstract Stream<T> takeWhile(Fn<T, Boolean> p, boolean includeFailure);

  public Stream<T> takeWhile(Fn<T, Boolean> p) {
    return takeWhile(p, false);
  }

  public abstract Stream<T> dropWhile(Fn<T, Boolean> p);

  public abstract <U> Stream<U> flatMap(Fn<T, Stream<U>> f);

  public abstract <U> Stream<U> map(Fn<T, U> f);

  public abstract <U> Stream<U> mapEval(Fn<T, IO<U>> f);

  public abstract Stream<T> filter(Fn<T, Boolean> p);

  public IO<Boolean> exists(Fn<T, Boolean> p) {
    return foldRight(Pure(false), (elem, searchMore) -> p.ap(elem) ? Pure(true) : searchMore);
  }

  public IO<Boolean> forall(Fn<T, Boolean> p) {
    return foldRight(Pure(true), (elem, searchMore) -> p.ap(elem) ? searchMore : Pure(false));
  }

  public Stream<T> reverse() {
    return Defer(foldLeft(Stream.Nil(), (acc, elem) -> Cons(elem, acc)));
  }

  public Stream<T> repeat() {
    return append(Defer(IO(this::repeat)));
  }

  IO<LList<T>> toLList() {
    return foldRight(LNil.instance(), (el, l) -> l.prepend(el));
  }

  <U> Stream<U> deferTransform(Function<Stream<T>, Stream<U>> conv) {
    if (this instanceof Cons) {
      return Defer(IO(() -> conv.apply(this)));
    }

    return conv.apply(this);
  }
}

@RequiredArgsConstructor
class Cons<T> extends Stream<T> {

  final IO<T> head;
  final Stream<T> tail;

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {
    return head.flatMap(h -> f.ap(h, tail.foldRight(z, f)));
  }

  @Override
  public <R> IO<R> foldLeft(R z, Fn2<R, T, R> f) {
    return head.flatMap(h -> tail.foldLeft(f.ap(z, h), f));
  }

  @Override
  public Stream<T> filter(Fn<T, Boolean> p) {
    return Defer(head.map(h -> p.ap(h) ? Cons(h, tail.filter(p)) : tail.filter(p)));
  }

  @Override
  public Stream<T> take(int n) {
    return n == 0 ? Nil() : SCons(head, tail.deferTransform(s -> s.take(n - 1)));
  }

  @Override
  public Stream<T> drop(int n) {
    return n == 0 ? this : tail.deferTransform(s -> s.drop(n - 1));
  }

  @Override
  public Stream<T> takeWhile(Fn<T, Boolean> p, boolean includeFailure) {
    return Defer(head.map(h -> p.ap(h) ? Cons(h, tail.takeWhile(p))
                                       : includeFailure ? Cons(h, Nil())
                                                        : Nil()
    ));
  }

  @Override
  public Stream<T> dropWhile(Fn<T, Boolean> p) {
    return Defer(head.map(h -> p.ap(h) ? tail.dropWhile(p) : this));
  }

  @Override
  public <U> Stream<U> flatMap(Fn<T, Stream<U>> f) {
    return Defer(head.map(h -> f.ap(h).append(tail.flatMap(f))));
  }

  @Override
  public <U> Stream<U> map(Fn<T, U> f) {
    return SCons(head.map(f), tail.deferTransform(s -> s.map(f)));
  }

  @Override
  public <U> Stream<U> mapEval(Fn<T, IO<U>> f) {
    return SCons(head.flatMap(f), tail.deferTransform(s -> s.mapEval(f)));
  }
}

@RequiredArgsConstructor
class Defer<T> extends Stream<T> {

  final IO<Stream<T>> evalStream;

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {
    return evalStream.flatMap(s -> s.foldRight(z, f));
  }

  @Override
  public <R> IO<R> foldLeft(R z, Fn2<R, T, R> f) {
    return evalStream.flatMap(s -> s.foldLeft(z, f));
  }

  @Override
  public Stream<T> take(int n) {
    return Defer(evalStream.map(s -> s.take(n)));
  }

  @Override
  public Stream<T> drop(int n) {
    return Defer(evalStream.map(s -> s.drop(n)));
  }

  @Override
  public Stream<T> takeWhile(Fn<T, Boolean> p, boolean includeFailure) {
    return Defer(evalStream.map(s -> s.takeWhile(p, includeFailure)));
  }

  @Override
  public Stream<T> dropWhile(Fn<T, Boolean> p) {
    return Defer(evalStream.map(s -> s.dropWhile(p)));
  }

  @Override
  public <U> Stream<U> flatMap(Fn<T, Stream<U>> f) {
    return Defer(evalStream.map(s -> s.flatMap(f)));
  }

  @Override
  public <U> Stream<U> map(Fn<T, U> f) {
    return Defer(evalStream.map(s -> s.map(f)));
  }

  @Override
  public <U> Stream<U> mapEval(Fn<T, IO<U>> f) {
    return Defer(evalStream.map(s -> s.mapEval(f)));
  }

  @Override
  public Stream<T> filter(Fn<T, Boolean> p) {
    return Defer(evalStream.map(s -> s.filter(p)));
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
  public <R> IO<R> foldRight(IO<R> z, Fn2<Object, IO<R>, IO<R>> f) {
    return z;
  }

  @Override
  public <R> IO<R> foldLeft(R z, Fn2<R, Object, R> f) {
    return Pure(z);
  }

  @Override
  public Stream<Object> take(int n) {
    return instance();
  }

  @Override
  public Stream<Object> drop(int n) {
    return instance();
  }

  @Override
  public Stream<Object> takeWhile(Fn<Object, Boolean> p, boolean includeFailure) {
    return instance();
  }

  @Override
  public Stream<Object> dropWhile(Fn<Object, Boolean> p) {
    return instance();
  }

  @Override
  public <U> Stream<U> flatMap(Fn<Object, Stream<U>> f) {
    return instance();
  }

  @Override
  public <U> Stream<U> map(Fn<Object, U> f) {
    return instance();
  }

  @Override
  public <U> Stream<U> mapEval(Fn<Object, IO<U>> f) {
    return instance();
  }

  @Override
  public Stream<Object> filter(Fn<Object, Boolean> p) {
    return instance();
  }
}
