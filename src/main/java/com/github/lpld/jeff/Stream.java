package com.github.lpld.jeff;

import com.github.lpld.jeff.functions.F;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author leopold
 * @since 5/10/18
 */
public abstract class Stream<T> {

  @SafeVarargs
  public static <T> Stream<T> of(Evaluation evaluation, T... ts) {
    Stream<T> stream = Nil.instance();
    for (T t : ts) {
      final Stream<T> tail = stream;
      stream = new Cons<>(Eval.now(t), evaluation.eval(() -> tail));
    }
    return stream;
  }

  @SafeVarargs
  public static <T> Stream<T> of(T... ts) {
    return Stream.of(Evaluation.LAZY, ts);
  }

  public boolean isEmpty() {
    return this instanceof Nil;
  }

  public boolean isNotEmpty() {
    return this instanceof Cons;
  }

  public abstract <U> Stream<U> flatMap(F<T, Stream<U>> f);

  public enum Evaluation {
    LAZY(Eval::always),
    MEMO(Eval::later);

    private final Function<Supplier, Eval> eval;

    @SuppressWarnings("unchecked")
    <T> Eval<T> eval(Supplier<T> value) {
      return eval.apply((Supplier) value.get());
    }

    Evaluation(Function<Supplier, Eval> eval) {
      this.eval = eval;
    }
  }
}

class Cons<T> extends Stream<T> {

  private final Eval<T> head;
  private final Eval<Stream<T>> tail;

  Cons(Eval<T> head, Eval<Stream<T>> tail) {
    this.head = head;
    this.tail = tail;
  }

  @Override
  public <U> Stream<U> flatMap(F<T, Stream<U>> f) {
    return new Concat<>(
        head.map(f),
        tail.map(ts -> ts.flatMap(f))
    );
  }
}

class Nil extends Stream<Object> {

  private static final Nil INSTANCE = new Nil();

  @SuppressWarnings("unchecked")
  static <T> Stream<T> instance() {
    return (Stream<T>) INSTANCE;
  }

  @Override
  public <U> Stream<U> flatMap(F<Object, Stream<U>> f) {
    return instance();
  }
}

class Concat<T> extends Stream<T> {

  private final Eval<Stream<T>> stream1;
  private final Eval<Stream<T>> stream2;

  public Concat(Eval<Stream<T>> stream1, Eval<Stream<T>> stream2) {
    this.stream1 = stream1;
    this.stream2 = stream2;
  }

  @Override
  public <U> Stream<U> flatMap(F<T, Stream<U>> f) {
    return new Concat<>(
        stream1.map(s -> s.flatMap(f)),
        stream2.map(s -> s.flatMap(f))
    );
  }
}