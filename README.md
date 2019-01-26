# Jeff: Effects for Java

This library aims to provide a way to work with side-effects in Java in a purely functional way, similar to what [cats-effect](https://typelevel.org/cats-effect/) and [zio](https://scalaz.github.io/scalaz-zio/) do in Scala. Currently, it is on a very early stage of development and can be considered an experiment.

**Jeff** is very much inspired by [cats-effect](https://typelevel.org/cats-effect/), however, because Java lacks the expressiveness Scala has (in particular, it doesn't have [higher-kinded polymorphism](https://en.wikipedia.org/wiki/Kind_(type_theory))), the library can't provide same level of abstraction.

Jeff has two main classes: `IO` and `Stream`.

## IO

`IO` provides a way to work with effectful computations as if they were pure values. In fact, `IO` instances are pure values. It means that you can always substitute a call to a function that returns `IO` with result of that function.

Consider the following example:

```java
// `Unit` is almost like a `void`, but it has exactly one instance.

IO<Unit> printLine(String line) {
    return IO(() -> System.out.println(line));
}


IO<Unit> program1 = printLine("hello")
  .chain(printLine("hello"));


IO<Unit> printHello = printLine("hello");
IO<Unit> program2 = printHello
  .chain(printHello);


program1.run();
program2.run();

// both programs will print "hello" twice:
// hello
// hello
```

As you can see, `printHello` is a result of calling `printLine("hello")`, and it is a _value_. And values do compose, as opposed to side-effectful function calls. You don't have such power with traditional imperative style. If you call, say, `System.out.println` in your code, it will print the line to console right away, without giving you a chance to somehow intervene. As opposed to that, in functional programming you suspend your side effects til the last moment (usually called _"the end of the world"_) and only then you run them: `program.run()`.

You can watch a great talk by Daniel Spiewak (author of  cats-effect) to better understand the motivation behind this approach: [The Making of an IO](https://www.youtube.com/watch?v=g_jP47HFpWA).

### Constructing IOs

There are several different ways to construct an `IO` :

#### IO.delay

`IO.delay` is a way to describe a syncrhonous task that will be evaluated on current thread.

```java
IO.delay(() -> System.out.println("Hi there"));
```

There is a shortcut for `IO.delay`, a static method `IO.IO` that can be statically imported and used like this:

```java
IO(() -> System.out.println("Hi there"));
```

#### IO.pure

`IO.pure` lets you take a pure value (one that has already been computed) and lift it to `IO` type.

```java
IO<Integer> pureFive = IO.pure(5);
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

`IO.fail` creates an IO that fails with a given exception:

```java
IO.fail(() -> new IOException("File not found."));
```

#### IO.async and IO.cancellable

`IO.async` and `IO.cancellable` both describe asynchronous tasks. `IO.async` accepts a function that injects a callback that you must call when the asynchronous task is completed.

```java
// Suppose you have an asynchrnonous method that accepts a callback:
public void httpGetAsync(String path, AsyncCallback callback);

interface AsyncCallback {
    void onSuccess(HttpResponse result);
    
    void onFailure(HttpResponseException error);
}

// You can wrap it in `IO.async` like this:
IO<HttpResponse> httpGet = IO.async(onFinish -> 
    httpGetAsync("https://github.com/lpld/jeff", new AsyncCallback() {
        @Override
        void onSuccess(HttpResponse result) {
            onFinish.run(Or.Right(result));
        }
        
        @Override    
        void onFailure(HttpResponseException error) {
            onFinish.run(Or.Left(error));
        }
    })
);
```

`Or.Right` and `Or.Left` are constructors of `Or<L, R>` class, a [coproduct](https://en.wikipedia.org/wiki/Coproduct) type that can hold either `L` or `R`, but not both. It is similar to Scala's [Either](http://lampwww.epfl.ch/~hmiller/scaladoc/library/scala/Either.html).

`IO.cancellable` is very much the same as `IO.async`, but allows you to provide cancellation action. As an example, here is an implementation of function `sleep` (already present in the `IO` class):

```java
  IO<Unit> sleep(ScheduledExecutorService scheduler, long millis) {
    return IO.cancellable(onFinish -> {
      // scheduling the task:
      final ScheduledFuture<?> task = scheduler
          .schedule(() -> onFinish.run(Right(Unit.unit)), millis, TimeUnit.MILLISECONDS);

      // return cancellation action:
      return IO.delay(() -> task.cancel(false));
    });
  }
```

`IO.cancellable` is useful when you use `IO.race`, that will be described further.

#### IO.never

`IO.never` creates an `IO` that is never completed.

#### IO.forked and fork

`IO.forked` allows to switch the evaluation of all following `IO`s in the chain to another thread/thread-pool.

```java
ExecutorService tp1 = Executors.newSingleThreadExecutor();
ExecutorService tp2 = Executors.newSingleThreadExecutor();

IO<Unit> printThreadName = IO(() -> System.out.println(Thread.currentThread().getName()));

IO<Unit> program = printThreadName
   .chain(IO.forked(tp1))
   .chain(printThreadName)
   .chain(IO.forked(tp2))
   .chain(printThreadName);

program.run();
// prints:
// 
// main
// pool-1-thread-1
// pool-2-thread-1
```

For convenience, you can rewrite this example using `fork` instance method:

```java
printThreadName
   .fork(tp1)
   .chain(printThreadName)
   .fork(tp2)
   .chain(printThreadName);
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

The answer is: _Monads!_

Monads in functional programming are a way to describe sequential computations. It is a very simple yet powerful concept. A monad `M` is somethid that has two functions defined:

```java
<T> M<T> pure(T pure);

<T, U> M<U> flatMap(M<T> m, Function<T, M<U>> f);
```

The first one is simple, it just lifts a pure value `T` to a monad. And by the way, we already have that: `IO.pure`.

The second one is more interesting.

#### flatMap

`flatMap` is an function for sequentially composing two `IO`s (implemented as an instance method):

```java
class IO<T> {
    // ...
    
    IO<U> flatMap(Function<T, IO<U>> f) {
        // ...
    }
}

IO<String> readLine = IO(() -> System.console().readLine());
IO<Unit> printLine(String str) {
    return IO(() -> System.out.println(str));
}

IO<Unit> program = printLine("What is your name?")
    .flatMap(u -> readLine)
    .flatMap(name -> printLine("Nice to meet you " + name));
```

Note that `flatMap` accepts a function from `T` to `IO<U>`. In other words, in order to produce `IO<U>` it needs a value `T` from previous computation step. It means that there is a guarantee that function `f` won't be called before value `T` is computed.

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
IO<String> printedName = printLine("What is your name?") // IO<Unit>
    .chain(readLine) // IO<String>
    .then(name -> printLine("Hello " + name));

// Note, that result type of the expression is IO<String>, while if we
// used flatMap instead of then, it would be IO<Unit>, because result
// type of `printLine` function is IO<Unit>.
```

#### map

`map` is a way to transform an `IO` with a given function.

```java
IO<String> readLine = IO(() -> System.console().readLine());

IO<Integer> stringLength = readLine.map(String::length);
```

Note, that `map(f)` can be expressed in terms of `flatMap` and `pure`: `flatMap(f.andThen(IO::pure))`

#### IO.race

`IO.race` initiates a "race" between two `IO` tasks and creates an `IO` that contains a value of the one that completes first. The second one will be cancelled if possible.

```java
ScheduledThreadPoolExecutor scheduler = ...;

IO<Integer> first = IO.sleep(scheduler, 500).map(u -> 42);
IO<String> second = IO.sleep(scheduler, 200).map(u -> "42");

Or<Integer, String> result = IO.race(scheduler, first, second).run();

// Will return Or.Right("42") because second IO completes first.
```

Cancellation logic works as following:

- If currently running task is cancellable (i.e. was created using `IO.cancellable`) it will be cancelled using its cancellation action, and no further tasks that are chained with it will start.
- If the task is not cancellable, it will continue to run til the next async boundary (`async`, `cancellable` or `fork`).

You can use parameterless `fork` method to create a synthetic async boundary. Consider an example:

```java
AtomicInteger state = new AtomicInteger();

// Generally speaking, it's not a good idea to use Thread.sleep,
// because it blocks current thread of execution, but we will
// use it as an example of uncancellable task:
IO<Integer> first = IO(() -> Thread.sleep(200))
    .chain(IO(() -> state.updateAndGet(i -> i + 2)));

IO<Integer> second = IO(() -> Thread.sleep(500))
    .chain(IO(() -> state.updateAndGet(i -> i + 1)));

// Now, if we create a race, both tasks will update the state,
// because neither they have a cancel action defined, nor there is an
// async boundary in any of them.
IO.race(threadPool, first, second).run();

// But we could create a synthetic async boundary:
IO<Integer> first = IO(() -> Thread.sleep(200))
    .fork()
    .chain(IO(() -> state.updateAndGet(i -> i + 2)));

IO<Integer> second = IO(() -> Thread.sleep(500))
    .fork()
    .chain(IO(() -> state.updateAndGet(i -> i + 1)));

IO.race(threadPool, first, second).run();

// Now, when the first task completes, it will trigger cancellation
// of the second task. Second task will check cancellation status
// when reaching the async boundary (fork) and won't proceed to 
// updating the state.
```
Note, that there is a race condition in this program, so there is still no guarantee that both tasks won't update the state. If you need a strong guarantee that the state will be updated only once, you have to ensure it yourself. In this case it will be sufficient to use `compareAndSet` instead of `updateAndGet`:
```java
state.compareAndSet(0, 1);
```

#### IO.seq

`IO.seq` can be useful when you need to non-deterministacally place two concurrent IO tasks in a sequence, in the order of their completion.

```java
IO<Integer> first = ...;
IO<String> second = ...;

IO<Or<Pr<Integer, IO<String>>, Pr<String, IO<Integer>>>> whoIsFaster =
    IO.seq(executor, first, second);
```

Return type of this method is a bit clumsy, but it basically means that the resulting `IO` will complete either with a value of type `Pr<Integer, IO<String>>` or a value of type `Pr<String, IO<Integer>>>`, depending on which of the two `IO`s completes first. `Pr<A, B>` is a [product](https://en.wikipedia.org/wiki/Product_(category_theory)) type that contains both `A` and `B`, also known as _pair_ or [_tuple_](https://en.wikipedia.org/wiki/Tuple). In the example above if `first` task completes first, then the result will be `Pr<Integer, IO<String>>`. At the moment when `whoIsFaster` is completed, `first` is completed too, so the `Integer` part of the result is pure (not wrapped in an `IO`). But `second` task is still running (might even be forever), that's why its result is wrapped: `IO<String>`.

#### IO.pair and IO.both

There are two variations of `IO.seq` with a bit clearer return types.

If both tasks that are passed into `IO.seq` have the same type, we can be a little bit more concise.

```java
IO<String> first = ...;
IO<String> second = ...;

IO<Or<Pr<String, IO<String>>, Pr<String, IO<String>>>> whoIsFaster =
    IO.seq(executor, first, second);
```

Here `whoIsFaster` can complete with either `Pr<String, IO<String>>>` or `Pr<String, IO<String>>>`, which is the same type, so in this case we can use `IO.pair`:

```java
IO<String> first = ...;
IO<String> second = ...;

IO<Pr<String, IO<String>> whoIsFaster = IO.pair(executor, first, second);
```

But of course, in this case you lose the information about which task has completed in which order.

If you need both `IO` tasks to complete, than you can use `IO.both`.

```java
IO<String> first = ...;
IO<Integer> second = ...;

IO<Pr<String, Integer>> result = IO.both(executor, first, second);
```

### Stackless recursion with IO

[Recursion](https://en.wikipedia.org/wiki/Recursion_(computer_science)) is one of the main tools in functional programmer's arsenal, but unfortunately its usage in languages like Java is very limited for a simple reason: each method call takes a [stack](https://en.wikipedia.org/wiki/Call_stack) frame, and stack is limited.

Consider this naive _factorial_ function, implemented using recursion:

```java
BigInteger factorial(BigIteger n) {
  return n == 0
         ? BigInteger.ONE
         : factorial(n - 1).multiply(BigInteger.valueOf(n));
}
```

It works for relatively small `n` values, but when called for big numbers (try calling it for 1,000,000) it fails with [StackOverflowError](https://docs.oracle.com/javase/8/docs/api/java/lang/StackOverflowError.html) for obvious reasons.

Some languages can optimize function calls that are in [tail position](https://en.wikipedia.org/wiki/Tail_call) by not adding a frame to the call stack for them. If Java had tail call elimination, we could rewrite factorial function so that the recursive call is the final action of the function:

```java
BigInteger factorial(long n) {
  return countFactorial(n, BigInteger.ONE);
}

// auxiliary tail-recursive function
BigInteger countFactorial(long n, BigInteger accum) {
  return n == 0
         ? accum
         // recursive call is in tail position:
         : countFactorial(n - 1, accum.multiply(BigInteger.valueOf(n)));
}
```

But Java doesn't optimize tail calls, so this won't change anything. But we could use a technique called trampolining.

**Trampolining** is a technique for writing stack-safe recursive functions in languages with limited stack. We could utilize this technique using `IO`:

```java
BigInteger factorial(long n) {
  return countFactorial(n, BigInteger.ONE).run();
}

IO<BigInteger> countFactorial(long n, BigInteger accum) {
  return IO.suspend(() ->
    n == 0
    ? IO.pure(accum)
    : countFactorial(n - 1, accum.multiply(BigInteger.valueOf(n)))
  );
}
```

As you can see, this looks quite similar to the previous example, except that `countFactorial` does not perform the actual computation when called, but suspends it using `IO.suspend` and returns an instance of `IO<BigInteger>` which is just a description of what has to be done. When this `IO` is evaluated using `run` method it will sequentually execute all nested suspended computations without taking stack frames. This is also called 'trading stack for heap', because in this case we use heap to store all intermediate `IO` objects.

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
