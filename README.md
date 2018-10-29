# Jeff: Effects for Java

This library aims to provide a way to work with side-effects in Java in a purely functional way, similar to what [cats-effect](https://typelevel.org/cats-effect/) or [zio](https://scalaz.github.io/scalaz-zio/) do in Scala. Currently, it is on a very early stage of development and can be considered an experiment.

**Jeff** is very much inspired by [cats-effect](https://typelevel.org/cats-effect/), however, because Java lacks the expressiveness Scala has (in particular, it doesn't have [higher-kinded polymorphism](https://en.wikipedia.org/wiki/Kind_(type_theory))), the library can't provide same level of abstraction. 

Jeff has two main classes: `IO` and `Stream`.

## IO

`IO` provides a way to work with effectful computations as if they were pure values. In fact, `IO` instances are pure values. It means that you can substitute a call to a function that returns `IO` with a result of that function.

Consider the following program:

```java
IO<Unit> printHello = IO(() -> System.out.println("hello"));

IO<Unit> program = printHello
  .chain(printHello)
  .chain(printHello);

program.run();

// this will print:
// hello
// hello
// hello
```

As you can see, `printHello` is a value, not a function call. This gives you a great power to compose your program of pure values. You don't have such power with traditional imperative style. If you call, say, `System.out.println` in your code, it will print the line to console right away, without giving you a chance to somehow intervene. As opposed to that, in functional programming you suspend your side effects til the last moment (usually called _"the end of the world"_) and only then you run them: `program.run()`.

You can watch a great talk by Daniel Spiewak to better understand the motivation behind this approach: [The Making of an IO](https://www.youtube.com/watch?v=g_jP47HFpWA).

### Constructing IOs

There are several different ways to construct an `IO`:

#### IO.delay

`IO.delay` is a way to describe syncrhonous action that will be evaluated on current thread.

```java
IO.delay(() -> System.out.println("Hi there"));
```

There is a shortcut for `IO.delay`, a method `IO.IO` which can be statically imported and used like this:

```java
IO(() -> System.out.println("Hi there"));
```

#### IO.pure

`IO.pure` lets you take a pure value (one that has already been computed) and lift it to `IO` type.

```java
IO.pure(5);
```

#### IO.suspend

`IO.suspend` is a way to defer construcion of an `IO`.

```java
IO<String> constructEffect() {
    // impl
}

IO.suspend(() -> constructEffect());
```

`IO.suspend` can be useful for **trampolining** (technique for achieving stackless recursion in stack-oriented languages). This will be described later.

#### IO.fail

`IO.fail` lets you create an IO that fails with a given exception:

```java
IO.fail(new IOException("File not found."));
```

#### IO.async and IO.cancellable

`IO.async` and `IO.cancellable` both describe asynchronous actions. `IO.async` accepts a function that injects a callback that you must call when the async action is completed.

```java
// Suppose you have an asynchrnonous method that accepts a callback:
public void httpGetAsync(String path, AsyncCallback callback);

interface AsyncCallback {
    void onSuccess(String result);
    
    void onFailure(HttpResponseException error);
}

// You can wrap it in IO.async like this:
IO.async(onFinish -> 
    httpGetAsync("https://github.com", new AsyncCallback() {
        @Override
        void onSuccess(String result) {
            onFinish.run(Or.Right(result));
        }
        
        @Override    
        void onFailure(HttpResponseException error) {
            onFinish.run(Or.Left(error));
        }
    })
);
```

`Or.Right` and `Or.Left` are constructors for `Or<L, R>` class, a [coproduct](https://en.wikipedia.org/wiki/Coproduct) type that can hold either `L` or `R`, but not both. It is similar to Scala's [Either](http://lampwww.epfl.ch/~hmiller/scaladoc/library/scala/Either.html).

`IO.cancellable` is very much the same as `IO.async`, but allows you to provide cancellation action. As an example, here is an implementation of function `sleep`, already present in the `IO` class:

```java
  IO<Unit> sleep(ScheduledExecutorService scheduler, long millis) {
    return IO.cancellable(onFinish -> {
      final ScheduledFuture<?> task = scheduler
          .schedule(() -> onFinish.run(Right(Unit.unit)), millis, TimeUnit.MILLISECONDS);

      return IO.delay(() -> task.cancel(false));
    });
  }
```

`IO.cancellable` is useful when you use `IO.race`, a function that returns an `IO` that completes with a value of the first of two `IO`s. The second one will be cancelled.

```java
final ScheduledThreadPoolExecutor scheduler = ...;

final IO<String> first = IO.sleep(scheduler, 500).map(u -> 42);
final IO<String> second = IO.sleep(scheduler, 200).map(u -> "42");

final Or<Integer, String> result = IO.race(scheduler, first, second).run();

// Will return Or.Right("42") because second IO completes first.
```

### Composing IOs

With imperative approach it was easy:

```java
String s1 = func1();
String s2 = func2(s1);
```

But now that our functions return `IO`, how can we get a `String` out of `IO<String>`?

```java
IO<String> s1 = func1();
IO<String> s2 = func2( ??? );
```

The answer is: _Monads_!

Monads in functional programming are a way to describe sequential computations. It is a very simple yet powerful concept. A monad `M` is somethid that has two functions defined:

```java
<T> M<T> pure(T pure);

<T, U> M<U> flatMap(M<T> m, Function<T, M<U>> f);
```

The first one is simple, it just lifts a pure value `T` to a monad. And by the way, we already have that: `IO.pure`.

The second one is more interesting.

#### flatMap

`flatMap` is an function for sequentially composing two `IO`s.

```java
IO<String> readLine = IO(() -> System.console().readLine());
IO<Unit> printLine(String str) {
    return IO(() -> System.out.println(str));
}

IO<Unit> program = printLine("What is your name?")
    .flatMap(u -> readLine)
    .flatMap(name -> printLine("Nice to meet you " + name));

program.run();
```

Note that `flatMap` accepts a function from `T` to `IO<U>`. In other words, in order to produce `IO<U>` it needs a value `T` from previous computation step. It means that there is a guarantee that function `f` won't be called before there is a value `T`.

#### chain and then

Sometimes you don't need a value from previous computation step (usually because that step describes an effect without return value). `chain` is a version of `flatMap` that ignores the previous value.

```java
// In the previous example we could rewrite
printLine("What is your name?").flatMap(u -> readLine);

// as this:
printLine("What is your name?").chain(readLine);    
```

`then` is quite the opposite of `chain`: it ignores the value produced by function `f` and returns value from the previous step.

```java
IO<String> name = printLine("What is your name?") // IO<Unit>
    .chain(readLine) // IO<String>
    .then(name -> printLine("Hello " + name));

// Note, that result type of the expression is IO<String>, while if we
// used flatMap instead of then, it would be IO<Unit>, because result
// type of `printLine` function is IO<Unit>.
```

## Stream

```java

      Stream
        .eval(IO(() -> Files.newBufferedReader(Paths.get("/home/lpld/.vimrc"))))
        .flatMap(reader -> Stream.iterate(() -> Optional.ofNullable(reader.readLine())))
        .foldLeft("", (l1, l2) -> l1 + "\n" + l2)
        .recover(rules(on(NoSuchFileException.class).doReturn("-- No such file --")))
        .flatMap(Console::printLine)
        .run();
```
