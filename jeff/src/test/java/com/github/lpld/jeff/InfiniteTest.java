package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Unit;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author leopold
 * @since 27/10/18
 */
public class InfiniteTest {

  static final AtomicLong time = new AtomicLong(System.currentTimeMillis());
  static final AtomicInteger errors = new AtomicInteger();
  static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  public static void main(String[] args) {

    final Stream<Unit> printMem = printMemory(30);

    final Stream<Unit> printMem2 = printMemory(30);

    final Stream<Unit> printMem3 = printMemory(30);

//    Stream.eval(IO.delay(() -> 1)).repeat().drain().run();

//    final Stream<Unit> repeat = Stream.of(Unit.unit).repeat();
//
    Stream
        .eval(IO.delay(() -> time.set(System.currentTimeMillis())))
        .append(
              Stream
                .zip(scheduler, printMem, printMem2)
                .map(__ -> Unit.unit)
                .merge(scheduler, printMem3)
        )
        .drain()
        .run();

  }

  private static Stream<Unit> printMemory(long delay) {
    return Stream.awakeEvery(scheduler, delay)
        .chain(IO.delay(() -> Runtime.getRuntime().freeMemory()))
        .mapEval(Console::printLine)
        .chain(IO.delay(() -> {
          final long now = System.currentTimeMillis();
          final long prev = time.get();

          System.out.println("------------------------------ err" + (now - prev));
//          if (now - prev > 110 && errors.incrementAndGet() > 3) {
//            System.out.println("err" + (now - prev));
//            throw new RuntimeException();
//          }

          time.set(now);
        }))
        ;
  }
}
