package com.github.lpld.jeff;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.Recovery.on;
import static com.github.lpld.jeff.Recovery.rules;

public class AppTest {

  @Test
  @Ignore("stack overflow")
  public void flatMapTest() {

    IO<Unit> io = IO.unit;

    for (int i = 1; i < 1000000; i++) {
      int finalI = i;
      io = io
          .then(() -> IO(() -> finalI))
          .flatMap(ii -> IO(() -> System.out.println(ii)));
    }

    io.run();
  }

  @Test
  public void shouldAnswerWithTrue() {

    final IO<Integer> io = IO.delay(() -> {
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
}
