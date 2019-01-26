package com.github.lpld.jeff;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.Getter;

/**
 * @author leopold
 * @since 22/10/18
 */
public class Resources {

  @Getter
  private static List<ExecutorService> singlePools = null;

  @Getter
  private static ScheduledExecutorService scheduler = null;

  @Getter
  private static ExecutorService multiPool = null;

  public static int size() {
    return singlePools == null ? 0 : singlePools.size();
  }

  public static void initExecutors(int number) {
    if (singlePools != null) {
      throw new IllegalStateException();
    }
    scheduler = Executors.newSingleThreadScheduledExecutor();

    multiPool = Executors.newFixedThreadPool(number);

    singlePools = IntStream
        .range(0, number)
        .mapToObj(i -> Executors.newSingleThreadExecutor())
        .collect(Collectors.toList());
  }

  public static void shutdownAll() {
    if (singlePools != null) {
      singlePools.forEach(ExecutorService::shutdownNow);
      singlePools = null;
    }
    scheduler.shutdownNow();
    scheduler = null;

    multiPool.shutdownNow();
    multiPool = null;
  }

  public static ExecutorService executor(int idx) {
    return singlePools.get(idx);
  }

  public static ExecutorService getSinglePool() {
    return executor(0);
  }
}
