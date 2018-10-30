package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Or;
import com.github.lpld.jeff.data.Pr;
import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff.functions.Fn;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.data.Or.Left;
import static com.github.lpld.jeff.data.Or.Right;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class IOTest extends IOTestBase {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void pure() {
    final IO<String> pure = IO.pure("123");
    assertThat(pure.run(), equalTo("123"));
    assertThat(pure.run(), equalTo("123"));
  }

  @Test
  public void delayRepeat() {
    final AtomicInteger i = new AtomicInteger();

    final IO<String> io = IO.delay(() -> {
      i.incrementAndGet();
      return "234";
    });

    assertThat(i.get(), is(0));
    assertThat(io.run(), is("234"));
    assertThat(i.get(), is(1));
    assertThat(io.run(), is("234"));
    assertThat(i.get(), is(2));
  }

  @Test
  public void fail() {
    final IO<?> fail = IO.fail(() -> new RuntimeException("error"));
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("error");
    fail.run();
  }

  @Test
  public void pureRecover() {

    final String result = IO.pure("777")
        .recover(err -> Optional.of("666"))
        .run();

    assertThat(result, is("777"));
  }

  @Test
  public void failRecover() {

    final String result = IO.<String>fail(RuntimeException::new)
        .recover(err -> Optional.of("333"))
        .run();

    assertThat(result, is("333"));
  }

  @Test
  public void failRecoverFail() {
    final IO<?> failed = IO.<String>fail(() -> new RuntimeException("error1"))
        .recover(err -> Optional.of("OK"))
        .chain(IO.fail(() -> new RuntimeException("error2")));
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("error2");
    failed.run();
  }

  @Test
  public void failRecoverFail2() {
    IO<?> failed = IO.fail(() -> new RuntimeException("error1"))
        .recoverWith(err -> Optional.of(IO.fail(() -> new RuntimeException("error2"))));
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("error2");
    failed.run();
  }

  @Test
  public void testAsync() {
    final CompletableFuture<Integer> future =
        CompletableFuture.supplyAsync(() -> {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            throw new IllegalArgumentException();
          }
          return 21;
        }, Resources.executor(0));

    final Integer res = IO.<Integer>async(onFinish -> future.whenComplete(
        (result, err) -> onFinish.run(Or.of(err, result))
    ))
        .map(i -> i * 2)
        .run();

    assertThat(res, equalTo(42));
  }

  @Test
  public void testFork() {

    final List<String> exepectedNames = Resources.getSinglePools()
        .stream()
        .limit(3)
        .map(ex -> {
          try {
            return ex.submit(() -> Thread.currentThread().getName()).get();
          } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException();
          }
        })
        .collect(Collectors.toList());

    final String mainThread = Thread.currentThread().getName();

    Fn<String, IO<Unit>> verifyThreadName =
        name -> IO.delay(() -> assertThat(Thread.currentThread().getName(), equalTo(name)));

    verifyThreadName.ap(mainThread)
        .flatMap(x -> verifyThreadName.ap(mainThread)
            .fork(Resources.executor(0))
            .chain(verifyThreadName.ap(exepectedNames.get(0)))
            .fork(Resources.executor(1))
            .chain(verifyThreadName.ap(exepectedNames.get(1)))
            .fork(Resources.executor(2))
        )
        .chain(verifyThreadName.ap(exepectedNames.get(2)))
        .run();
  }

  @Test
  public void testSleep() {

    final long now = System.currentTimeMillis();
    final Integer result = IO.sleep(Resources.getScheduler(), 1000)
        .chain(IO.pure(44))
        .run();

    assertThat(System.currentTimeMillis() - now > 1000, is(true));
    assertThat(result, is(44));
  }

  @Test
  public void testRaceSleeping() {
    final IO<Integer> first = IO.sleep(Resources.getScheduler(), 500).map(u -> 22);
    final IO<String> second = IO.sleep(Resources.getScheduler(), 200).map(u -> "abc");

    final Or<Integer, String> result = IO.race(Resources.getSinglePool(), first, second).run();

    assertThat(result, equalTo(Right("abc")));
  }

  @Test
  public void testRaceBlocking() {
    final IO<Integer> first = IO.delay(() -> Thread.sleep(500)).map(u -> 22);
    final IO<String> second = IO.delay(() -> Thread.sleep(200)).map(u -> "abc");

    final Or<Integer, String> result = IO.race(Resources.getMultiPool(), first, second).run();

    assertThat(result, equalTo(Right("abc")));
  }

  @Test
  public void testSeq() {

    final IO<Integer> first = IO.sleep(Resources.getScheduler(), 300).map(u -> 22);
    final IO<String> second = IO.sleep(Resources.getScheduler(), 200).map(u -> "abc");

    final Or<Pr<Integer, IO<String>>, Pr<String, IO<Integer>>> result =
        IO.seq(Resources.getSinglePool(), first, second).run();

    assertThat(result.isRight(), is(true));
    final Pr<String, IO<Integer>> res = result.getRight();

    assertThat(res._1, is("abc"));
    assertThat(res._2.run(), is(22));
  }

  @Test
  public void cancelSleep() {
    final AtomicInteger state = new AtomicInteger();

    final IO<Integer> io1 =
        IO.sleep(Resources.getScheduler(), 300)
            .chain(IO.delay(() -> state.updateAndGet(i -> i + 2)));

    final IO<Integer> io2 =
        IO.sleep(Resources.getScheduler(), 600)
            .chain(IO.delay(() -> state.updateAndGet(i -> i + 1)));

    final Or<Integer, Integer> result = IO
        .race(Resources.getMultiPool(), io1, io2)
        .then(IO.sleep(Resources.getScheduler(), 400))
        .run();

    assertThat(result, is(Left(2)));
    assertThat(state.get(), is(2));
  }

  @Test
  public void cancelAsync() {
    final AtomicInteger state = new AtomicInteger();
    // instead of IO.sleep we use Thread.sleep, which is uncancellable.
    // but the cancellation still must happen because there is a fork after sleep.

    final IO<Integer> io1 = IO.delay(() -> Thread.sleep(300))
        .fork()
        .chain(IO.delay(() -> state.updateAndGet(i -> i + 2)));

    final IO<Integer> io2 = IO.delay(() -> Thread.sleep(600))
        .fork()
        .chain(IO.delay(() -> state.updateAndGet(i -> i + 1)));

    final Or<Integer, Integer> result = IO
        .race(Resources.getMultiPool(), io1, io2)
        .then(IO.sleep(Resources.getScheduler(), 400))
        .run();

    assertThat(result, is(Left(2)));
    assertThat(state.get(), is(2));


  }

  @Test
  public void stackSafety() {

    IO<Unit> io = IO.unit;

    for (int i = 1; i < 100000; i++) {
      int finalI = i;
      io = io
          .map(x -> finalI)
          .flatMap(ii -> IO(() -> System.out.println(ii)));
    }

    io.run();
  }
}
