package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Unit;
import com.github.lpld.jeff.functions.Fn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.github.lpld.jeff.IO.Fail;
import static com.github.lpld.jeff.IO.Fork;
import static com.github.lpld.jeff.IO.IO;
import static com.github.lpld.jeff.IO.Pure;
import static com.github.lpld.jeff.Recovery.on;
import static com.github.lpld.jeff.Recovery.rules;
import static com.github.lpld.jeff.data.Unit.unit;

/**
 * @author leopold
 * @since 11/10/18
 */
public class IODemo {

  public static void main(String[] args) {
    IO.<Integer>Fail(new IllegalArgumentException("1"))
        .map(i -> i * 55)
        .recover(rules(on(IllegalArgumentException.class).doReturn(4)))
        .chain(Fail(new IllegalArgumentException("2")))
        .recover(rules(on(IllegalArgumentException.class).doReturn(66)))
        .map(Object::toString)
        .flatMap(Console::printLine)
        .run();

  }

  public static void main1(String[] args) {

    final ExecutorService tp1 = Executors.newSingleThreadExecutor();
    final ExecutorService tp2 = Executors.newSingleThreadExecutor();
    final ExecutorService tp3 = Executors.newSingleThreadExecutor();

    Fork(tp1)
        .chain(Fail(new IllegalArgumentException("3")))
        .recover(rules(on(IllegalArgumentException.class).doReturn(66)))
        .run();

    IO.<Integer>Fail(new IllegalArgumentException("1"))
        .flatMap(i -> Fork(tp1).chain(Pure(i * 55)))
        .recover(rules(on(IllegalArgumentException.class).doReturn(4)))
        .flatMap(i -> Fork(tp2).<Integer>chain(Fail(new IllegalArgumentException("2"))))
        .recover(rules(on(IllegalArgumentException.class).doReturn(66)))
        .map(Object::toString)
        .flatMap(Console::printLine)
        .run();

    tp1.shutdown();
    tp2.shutdown();
    tp3.shutdown();
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
