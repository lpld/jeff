package com.github.lpld.jeff;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.github.lpld.jeff.IO.Fork;

/**
 * @author leopold
 * @since 11/10/18
 */
public class IOTest {

  public static void main(String[] args) {
    final ExecutorService tp = Executors.newFixedThreadPool(2);

    Console.readLine()
        .flatMap(l -> Fork(tp)
        .chain(Console.printLine(l))
    ).run();

  }
}
