package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff.functions.Fn;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.IO.Pure;
import static com.github.lpld.jeff.Recovery.on;
import static com.github.lpld.jeff.Recovery.rules;
import static com.github.lpld.jeff.data.Or.Left;
import static com.github.lpld.jeff.data.Or.Right;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class IOTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void pure() {
    final IO<String> pure = IO.Pure("123");
    assertThat(pure.run(), equalTo("123"));
    assertThat(pure.run(), equalTo("123"));
  }

  @Test
  public void delayRepeat() {
    final AtomicInteger i = new AtomicInteger();

    final IO<String> io = IO.Delay(() -> {
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
    final IO<?> fail = IO.Fail(new RuntimeException("error"));
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("error");
    fail.run();
  }

  @Test
  public  void pureRecover() {

    final String result = IO.Pure("777")
        .recover(err -> Optional.of("666"))
        .run();

    assertThat(result, is("777"));
  }

  @Test
  public void failRecover() {

    final String result = IO.<String>Fail(new RuntimeException())
        .recover(err -> Optional.of("333"))
        .run();

    assertThat(result, is("333"));
  }

  @Test
  public void failRecoverFail() {
    final IO<?> failed = IO.<String>Fail(new RuntimeException("error1"))
        .recover(err -> Optional.of("OK"))
        .chain(IO.Fail(new RuntimeException("error2")));
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("error2");
    failed.run();
  }

  @Test
  public void failRecoverFail2() {
    IO<?> failed = IO.Fail(new RuntimeException("error1"))
        .recoverWith(err -> Optional.of(IO.Fail(new RuntimeException("error2"))));
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("error2");
    failed.run();
  }

  @Test
  public void testAsync() {
    final ExecutorService ex = Executors.newSingleThreadExecutor();
    try {
      final CompletableFuture<Integer> future =
          CompletableFuture.supplyAsync(() -> {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              throw new IllegalArgumentException();
            }
            return 21;
          });

      final Integer res = IO.<Integer>Async(onFinish -> future.whenComplete(
          (result, error) -> {
            if (error == null) {
              onFinish.run(Right(result));
            } else {
              onFinish.run(Left(error));
            }
          }
      ))
          .map(i -> i * 2)
          .run();

      assertThat(res, equalTo(42));

    } finally {
      ex.shutdown();
    }
  }

  @Test
  public void testFork() {
    final List<ExecutorService> executors = Arrays.asList(
        Executors.newSingleThreadExecutor(),
        Executors.newSingleThreadExecutor(),
        Executors.newSingleThreadExecutor());

    final List<String> exepectedNames = executors.stream()
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
        name -> IO.Delay(() -> assertThat(Thread.currentThread().getName(), equalTo(name)));

    verifyThreadName.ap(mainThread)
        .flatMap(x -> verifyThreadName.ap(mainThread)
            .chain(IO.Fork(executors.get(0)))
            .chain(verifyThreadName.ap(exepectedNames.get(0)))
            .chain(IO.Fork(executors.get(1)))
            .chain(verifyThreadName.ap(exepectedNames.get(1)))
            .chain(IO.Fork(executors.get(2)))
        )
        .chain(verifyThreadName.ap(exepectedNames.get(2)))
        .run();
  }

  @Test
  public void testSleep() {

    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    try {
      final long now = System.currentTimeMillis();
      final Integer result = IO.sleep(scheduler, 1000)
          .chain(Pure(44))
          .run();

      assertThat(System.currentTimeMillis() - now > 1000, is(true));
      assertThat(result, is(44));
    } finally {
      scheduler.shutdown();
    }
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
