package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Unit;

import org.junit.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
  public void flatMapTest() {

    IO<Unit> io = IO.unit;

    for (int i = 1; i < 10000; i++) {
      int finalI = i;
      io = io
          .map(x -> finalI)
          .flatMap(ii -> IO(() -> System.out.println(ii)));
    }

    io.run();
  }

  @Test
  public void shouldAnswerWithTrue() {

    final IO<Integer> io = IO.Delay(() -> {
      System.out.println();
      return 5;
    });

    io.recover(rules(
        on(IOException.class)
            .doReturn(22),

        on(IllegalStateException.class).and(is -> is.getMessage().contains("abc"))
            .doReturn(() -> 77 + 12)
    ));

    io.recoverWith(rules(
        on(IOException.class).doReturn(IO(() -> 44))
    ));

    io.recover((th) -> th instanceof IOException ? Optional.of(22) : Optional.empty());
  }

  @Test
  public void testFork() {


  }

}
