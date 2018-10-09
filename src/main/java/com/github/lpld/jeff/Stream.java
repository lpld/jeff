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

  @RequiredArgsConstructor
  public static class Collect<T> {

    final T value;
    final boolean over;
    final int skipSteps;

    public static <T> Collect<T> value(T t) {
      return value(t, 0);
    }

    public static <T> Collect<T> value(T t, int skipSteps) {
      return new Collect<>(t, false, skipSteps);
    }

    public static <T> Collect<T> value(T t, boolean over) {
      return new Collect<>(t, over, 0);
    }

    public boolean skip() {
      return skipSteps > 0;
    }

    public Collect<T> skipped() {
      Validate.state(skipSteps > 0, "skipSteps == 0");
      return skip(skipSteps - 1);
    }

    public Collect<T> skip(int newSkip) {
      Validate.state(!over, "Cannot update skip when over=true");
      Validate.arg(newSkip >= 0, "skipSteps must be > 0");
      return new Collect<>(value, false, newSkip);
    }
  }

  public abstract <R> IO<Collect<R>> collect(Collect<R> initial, Fn2<R, T, Collect<R>> f);

  public abstract <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f);

  public <R> IO<R> foldLeft(R z, Fn2<R, T, R> f) {
    return collect(
        Collect.value(z),
        (r, t) -> Collect.value(f.ap(r, t))
    ).map(c -> c.value);
  }

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

  public Stream<T> drop(int i) {
    Validate.arg(i >= 0, "i <= 0");
    return
        i == 0
        ? this
        : defer(collect(
            Collect.value(Stream.<T>Nil(), i),
            (acc, elem) -> Collect.value(acc.append(elem))
        ).map(c -> c.value));
  }

  public Stream<T> tail() {
    return drop(1);
  }

  public Stream<T> take(int i) {
    Validate.arg(i >= 0, "i <= 0");
    return
        i == 0
        ? Nil()
        : defer(
            collect(
                Collect.value(Pr(i, Stream.<T>Nil())),
                (col, elem) -> Collect.value(Pr(col._1 - 1, col._2.append(elem)), col._1 == 1)
            ).map(c -> c.value._2)
        );
  }

  public Stream<T> head() {
    return take(1);
  }

  public Stream<T> takeWhile(Fn<T, Boolean> p, boolean includeFailure) {

    return defer(
        collect(
            Collect.value(Stream.<T>Nil()),
            (acc, elem) -> {
              final Boolean take = p.ap(elem);
              final Stream<T> newStream = take || includeFailure ? acc.append(elem) : acc;
              return Collect.value(newStream, take);
            }
        ).map(c -> c.value)
    );
  }

  public Stream<T> takeWhile(Fn<T, Boolean> p) {
    return takeWhile(p, false);
  }

  public Stream<T> dropWhile(Fn<T, Boolean> p) {
    return defer(
        collect(
            Collect.value(Pr(true, Stream.<T>Nil())),
            (pr, elem) -> {
              final boolean drop = pr._1 && p.ap(elem);
              final Stream<T> newStream = drop ? pr._2 : pr._2.append(elem);
              return Collect.value(Pr(drop, newStream));
            })
            .map(c -> c.value._2)
    );
  }

  public Stream<T> filter(Fn<T, Boolean> p) {
    return defer(
        foldLeft(Nil(), (acc, elem) -> p.ap(elem) ? acc.append(elem) : acc)
    );
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
  public <R> IO<Collect<R>> collect(Collect<R> initial, Fn2<R, T, Collect<R>> f) {
    if (initial.over) {
      return Pure(initial);
    }

    if (initial.skip()) {
      return Pure(initial.skipped());
    }

    return head.flatMap(h -> {
      final Collect<R> collect = f.ap(initial.value, h);

      if (collect.over) {
        return Pure(collect);
      }

      return tail.flatMap(t -> t.collect(collect, f));
    });
  }

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {
    return head.flatMap(h -> f.ap(h, tail.flatMap(t -> t.foldRight(z, f))));
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
  public <R> IO<Collect<R>> collect(Collect<R> initial, Fn2<R, T, Collect<R>> f) {
    if (initial.over) {
      return Pure(initial);
    }

    return stream1
        .flatMap(s1 -> s1.collect(initial, f)
            .flatMap(
                col1 -> col1.over
                        ? Pure(col1)
                        : stream2.flatMap(s2 -> s2.collect(col1, f))
            )
        );
  }

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<T, IO<R>, IO<R>> f) {
    return stream1.flatMap(s1 -> s1.foldRight(stream2.flatMap(s2 -> s2.foldRight(z, f)), f));
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
  public <R> IO<Collect<R>> collect(Collect<R> initial, Fn2<R, Object, Collect<R>> f) {
    return Pure(initial);
  }

  @Override
  public <R> IO<R> foldRight(IO<R> z, Fn2<Object, IO<R>, IO<R>> f) {
    return z;
  }
}

class Validate {

  static void arg(boolean cond, String msg) {
    if (!cond) {
      throw new IllegalArgumentException(msg);
    }
  }

  static void state(boolean cond, String msg) {
    if (!cond) {
      throw new IllegalStateException(msg);
    }
  }
}