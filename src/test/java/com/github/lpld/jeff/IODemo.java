package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Or;
import com.github.lpld.jeff.data.Pr;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.IO.fail;
import static com.github.lpld.jeff.Recovery.on;
import static com.github.lpld.jeff.Recovery.rules;

/**
 * @author leopold
 * @since 11/10/18
 */
public class IODemo {

  public static void main(String[] args) {
    IO.<Integer>fail(() -> new IllegalArgumentException("1"))
        .map(i -> i * 55)
        .recover(rules(on(IllegalArgumentException.class).doReturn(4)))
        .chain(fail(() -> new IllegalArgumentException("2")))
        .recover(rules(on(IllegalArgumentException.class).doReturn(66)))
        .map(Object::toString)
        .flatMap(Console::printLine)
        .run();

  }

  public static void main1(String[] args) {

    final ExecutorService tp1 = Executors.newSingleThreadExecutor();
    final ExecutorService tp2 = Executors.newSingleThreadExecutor();
    final ExecutorService tp3 = Executors.newSingleThreadExecutor();

    IO.forked(tp1)
        .chain(fail(() -> new IllegalArgumentException("3")))
        .recover(rules(on(IllegalArgumentException.class).doReturn(66)))
        .run();

    IO.<Integer>fail(() -> new IllegalArgumentException("1"))
        .fork(tp1)
        .map(i -> i * 55)
        .recover(rules(on(IllegalArgumentException.class).doReturn(4)))
        .fork(tp2)
        .chain(fail(() -> new IllegalArgumentException("2")))
        .recover(rules(on(IllegalArgumentException.class).doReturn(66)))
        .map(Object::toString)
        .flatMap(Console::printLine)
        .run();

    tp1.shutdown();
    tp2.shutdown();
    tp3.shutdown();
  }

  public void asdf () {
    IO<Integer> first = IO.sleep(Resources.getScheduler(), 500).map(u -> 42);
    IO<String> second = IO.sleep(Resources.getScheduler(), 200).map(u -> "42");

    IO<Or<Pr<Integer, IO<String>>, Pr<String, IO<Integer>>>> seq =
        IO.seq(Resources.getMultiPool(), first, second);
  }

  public static void main2(String[] args) {
    Stream
        .eval(IO(() -> Files.newBufferedReader(Paths.get("/home/lpld/.vimrc"))))
        .flatMap(reader -> Stream.iterate(() -> Optional.ofNullable(reader.readLine())))
        .foldLeft("", (l1, l2) -> l1 + "\n" + l2)
        .recover(rules(on(NoSuchFileException.class).doReturn("-- No such file --")))
        .flatMap(Console::printLine)
        .run();
  }
}
