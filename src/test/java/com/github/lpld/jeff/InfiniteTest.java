package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Unit;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author leopold
 * @since 27/10/18
 */
public class InfiniteTest {

  public static void main(String[] args) {
    final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    final Stream<Unit> printMem = Stream.awakeEvery(scheduler, 1000)
        .chain(IO.Delay(() -> Runtime.getRuntime().freeMemory()))
        .mapEval(Console::printLine);

    final Stream<Unit> repeat = Stream.of(Unit.unit).repeat();

    Stream
        .zip(scheduler, Stream.of(1).repeat(), Stream.of(2).repeat())
        .map(any -> Unit.unit)
        .merge(scheduler, printMem)
        .drain()
        .run();

  }
}
