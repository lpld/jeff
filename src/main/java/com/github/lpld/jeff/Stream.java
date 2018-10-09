package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Pr;
import com.github.lpld.jeff.functions.Fn;
import com.github.lpld.jeff.functions.Fn2;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.IO.Pure;
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
    return defer(IO(() -> unfoldEager(z, f)));
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

  public static <T> Stream<T> ofOptional(Optional<T> opt) {
    return opt.map(Stream::of).orElse(Nil());
  }

  @SafeVarargs
  public static <T> Stream<T> Stream(T... elements) {
    Stream<T> s = Nil();
    for (int i = elements.length - 1; i >= 0; i--) {
      s = Cons(elements[i], s);
    }

    return s;
  }

  public static <T> Stream<T> eval(IO<T> io) {
    return SCons(io, Pure(Nil()));
  }

  public static <T> Stream<T> ofAll(Iterable<T> elems) {
    return defer(IO(() -> unfoldEager(
        elems.iterator(),
        iterator -> Optional.of(iterator)
            .filter(Iterator::hasNext)
            .map(it -> Pr(it.next(), it))
    )));
  }

  // Unfold that eagerly evaluates the first step:
  private static <T, S> Stream<T> unfoldEager(S z, Fn<S, Optional<Pr<T, S>>> f) throws Throwable {
    return f.ap(z)
        .map(p -> SCons(Pure(p._1), IO(() -> unfoldEager(p._2, f))))
        .orElseGet(Stream::Nil);
  }

  static <T> Stream<T> defer(IO<Stream<T>> streamEval) {
    // Defer is implemented in terms of concat (which is lazy)
    return SConcat(streamEval, Pure(Nil()));
  }

  // INSTANCE METHODS:

  public abstract <U> Stream<U> flatMap(Fn<T, Stream<U>> f);

  public <U> Stream<U> map(Fn<T, U> f) {
    return flatMap(f.andThen(Stream::of));
  }

  public abstract <U> Stream<U> mapEval(Fn<T, IO<U>> f);

  public abstract <R> IO<R> foldl(R z, Fn2<R, T, Optional<R>> f);

  public <R> IO<R> foldLeft(R z, Fn2<R, T, R> f) {
    return foldl(z, f.andThen(Optional::of));
  }

  public abstract <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f);

  public <R> IO<R> foldRight(R z, Fn2<T, R, R> f) {
    return foldRight(Pure(z), (t, ior) -> ior.map(r -> f.ap(t, r)));
  }

  public <R> Stream<R> scanLeft(R z, Fn2<R, T, R> f) {
    return defer(
        foldLeft(Pr(Stream.<R>Nil(), z),
                 (acc, elem) -> {
                   final R r = f.ap(acc._2, elem);
                   return Pr(Cons(r, acc._1), r);
                 })
            .map(Pr::_1)
    );
  }

  public <R> Stream<R> scanRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {

    return defer(
        this.foldRight(z.map(zz -> Pr(Stream.<R>Nil(), zz)),
                       (elem, acc) -> acc
                           .map(Pr::_1)
                           .flatMap(accStr -> f.ap(elem, acc.map(Pr::_2))
                               .map(r -> Pr(Cons(r, accStr), r))
                           ))
            .map(Pr::_1));
  }

  public <R> Stream<R> scanRight(R z, Fn2<T, R, R> f) {
    return scanRight(Pure(z), (t, ior) -> ior.map(r -> f.ap(t, r)));
  }

  public Stream<T> append(T t) {
    return Concat(this, Stream(t));
  }

  public IO<Boolean> exists(Fn<T, Boolean> p) {
    return foldRight(Pure(false), (elem, searchMore) -> p.ap(elem) ? Pure(true) : searchMore);
  }

  public IO<Boolean> forall(Fn<T, Boolean> p) {
    return foldRight(Pure(true), (elem, searchMore) -> p.ap(elem) ? searchMore : Pure(false));
  }

  public Stream<T> reverse() {
    return defer(foldLeft(Nil(), (acc, t) -> Cons(t, acc)));
  }

  public Stream<T> takeWhile(Fn<T, Boolean> p, boolean includeFailure) {
    return defer(
        foldl(
            Pr(Stream.<T>Nil(), true),
            (pr, elem) -> {
              if (pr._2) { // means all previous elements did satisfy p
                final Boolean take = p.ap(elem);
                if (take || includeFailure) {
                  return Optional.of(Pr(pr._1.append(elem), take));
                }
              }
              return Optional.empty();
            }
        ).map(Pr::_1)
    );
  }

  public Stream<T> takeWhile(Fn<T, Boolean> p) {
    return takeWhile(p, false);
  }

  public Stream<T> dropWhile(Fn<T, Boolean> p) {
    return defer(
        foldLeft(Pr(Stream.<T>Nil(), true),
                 // pr._2 means that all previous elements did satisfy p
                 (pr, elem) -> pr._2 && p.ap(elem) ? pr : Pr(pr._1.append(elem), false)
        ).map(Pr::_1)
    );
  }

  public Stream<T> filter(Fn<T, Boolean> p) {
    return defer(
        foldLeft(Nil(), (acc, elem) -> p.ap(elem) ? acc.append(elem) : acc)
    );
  }

  public Stream<T> tail() {
    return defer(
        foldLeft(
            Pr(Stream.<T>Nil(), false),
            (pr, elem) -> Pr(pr._2 ? pr._1.append(elem) : Nil(), true)
        ).map(Pr::_1)
    );
  }

  public Stream<T> head() {
    return defer(
        foldl(
            Pr(Stream.<T>Nil(), false),
            (pr, elem) -> pr._2 ? Optional.empty() : Optional.of(Pr(Stream(elem), true))
        ).map(Pr::_1)
    );
  }

  public abstract IO<Optional<T>> evalHead();

  public Stream<T> take(int n) {
    if (n < 0) {
      throw new IllegalArgumentException(n + "");
    }
    if (n == 0) {
      return Nil();
    }
    return Concat(head(), tail().take(n - 1));
  }

  public Stream<T> drop(int n) {
    if (n < 0) {
      throw new IllegalArgumentException(n + "");
    }
    return n == 0 ? this : tail().drop(n - 1);
  }

  IO<LList<T>> toLList() {
    return foldRight(LNil.instance(), (el, l) -> l.prepend(el));
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
  public <R> IO<R> foldl(R z, Fn2<R, T, Optional<R>> f) {
    return transform(
        (h, t) ->
            f.ap(z, h)
                .map(r -> t.foldl(r, f))
                .orElseGet(() -> Pure(z))
    );
  }

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {
    return transform((h, t) -> f.ap(h, t.foldRight(z, f)));
  }

  @Override
  public IO<Optional<T>> evalHead() {
    return head.map(Optional::of);
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
  public <R> IO<R> foldl(R z, Fn2<R, T, Optional<R>> f) {
    return transform((s1, s2) -> s1.foldl(z, f).flatMap(zz -> s2.foldl(zz, f)));
  }

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {
    return transform((s1, s2) -> s1.foldRight(s2.foldRight(z, f), f));
  }

  public IO<Optional<T>> evalHead() {
    return stream1
        .flatMap(Stream::evalHead)
        .flatMap((Optional<T> opt) -> opt
            .map(t -> Pure(Optional.of(t)))
            .orElseGet(() -> stream2.flatMap(Stream::evalHead)));
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
  public <R> IO<R> foldl(R z, Fn2<R, Object, Optional<R>> f) {
    return Pure(z);
  }

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<Object, IO<R>, IO<R>> f) {
    return z;
  }

  @Override
  public IO<Optional<Object>> evalHead() {
    return Pure(Optional.empty());
  }
}
