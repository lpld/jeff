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
public class IOTest {

  public static void main2(String[] args) {
    IO.<Integer>Fail(new IllegalArgumentException("1"))
        .map(i -> i * 55)
        .recover(rules(on(IllegalArgumentException.class).doReturn(4)))
        .chain(Fail(new IllegalArgumentException("2")))
        .recover(rules(on(IllegalArgumentException.class).doReturn(66)))
        .run();

  }

  public static void main3(String[] args) {

    final ExecutorService tp1 = Executors.newSingleThreadExecutor();
    final ExecutorService tp2 = Executors.newSingleThreadExecutor();
    final ExecutorService tp3 = Executors.newSingleThreadExecutor();

    // todo: doesn't work
    Fork(tp1)
        .chain(Fail(new IllegalArgumentException("3")))
        .recover(rules(on(IllegalArgumentException.class).doReturn(66)))
        .run();
//
//    // todo: 2nd recover works in Sync mode (main2 method), but is ignored in async.
//    IO.<Integer>Fail(new IllegalArgumentException("1"))
//        .flatMap(i -> Fork(tp1).chain(Pure(i * 55)))
//        .recover(rules(on(IllegalArgumentException.class).doReturn(4)))
//        .flatMap(i -> Fork(tp2).chain(Fail(new IllegalArgumentException("2"))))
//        .recover(rules(on(IllegalArgumentException.class).doReturn(66)))
//        .run();

    tp1.shutdown();
    tp2.shutdown();
    tp3.shutdown();
  }

  public static void main1(String[] args) {

    IO<Unit> printThreadName =
        IO(() -> Thread.currentThread().getName()).flatMap(Console::printLine);

    final ExecutorService tp1 = Executors.newSingleThreadExecutor();
    final ExecutorService tp2 = Executors.newSingleThreadExecutor();
    final ExecutorService tp3 = Executors.newSingleThreadExecutor();

//    printThreadName
//        .chain(Fork(tp))
//        .chain(printThreadName)
//        .run();

    printThreadName
        .flatMap(x -> printThreadName
            .chain(Fork(tp1))
            .chain(printThreadName)
            .chain(Fork(tp2))
            .chain(printThreadName)
            .chain(Fork(tp3))
        )
        .chain(printThreadName)
        .run();

    tp1.shutdown();
    tp2.shutdown();
    tp3.shutdown();

//    Console.readLine()
//        .flatMap(l -> Fork(tp)
//        .chain(Console.printLine(l))
//    ).run();

  }

  public static void main(String[] args) {
    // 
//    Pure("a")
//        .map(Fn.id())
//        .chain(Fail(new IllegalArgumentException("3")))
//        .recover(rules(on(IllegalArgumentException.class).doReturn("-- No such file --")))
//        .map(Fn.id())
//        .run();

    Stream
        .eval(IO(() -> Files.newBufferedReader(Paths.get("/home/lpld/.vimrc"))))
        .flatMap(reader -> Stream.iterate(() -> Optional.ofNullable(reader.readLine())))
        .foldLeft("", (l1, l2) -> l1 + "\n" + l2)
        .recover(rules(on(NoSuchFileException.class).doReturn("-- No such file --")))
        .flatMap(Console::printLine)
        .run();
  }
}
