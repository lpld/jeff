package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Unit;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.lpld.jeff.IO.Fork;
import static com.github.lpld.jeff.IO.IO;

/**
 * @author leopold
 * @since 11/10/18
 */
public class IOTest {

  public static void main(String[] args) {

    IO<Unit> printThreadName =
        IO(() -> Thread.currentThread().getName()).flatMap(Console::printLine);

    final ExecutorService tp = Executors.newFixedThreadPool(2);

//    printThreadName
//        .chain(Fork(tp))
//        .chain(printThreadName)
//        .run();

    printThreadName
        .flatMap(x -> printThreadName
            .chain(Fork(tp))
            .chain(printThreadName))
        .chain(printThreadName)
        .run();

//    Console.readLine()
//        .flatMap(l -> Fork(tp)
//        .chain(Console.printLine(l))
//    ).run();

  }
}
