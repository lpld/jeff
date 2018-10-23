package com.github.lpld.jeff.generators;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author leopold
 * @since 22/10/18
 */
public class Resources {

  private static List<ExecutorService> executors = null;

  public static int size() {
    return executors == null ? 0 : executors.size();
  }

  public static void initExecutors(int number) {
    if (executors != null) {
      throw new IllegalStateException();
    }
    executors = IntStream
        .range(0, number)
        .mapToObj(i -> Executors.newSingleThreadExecutor())
        .collect(Collectors.toList());
  }

  public static void shutdownAll() {
    if (executors != null) {
      executors.forEach(ExecutorService::shutdownNow);
      executors = null;
    }
  }

  public static ExecutorService executor(int idx) {
    return executors.get(idx);
  }
}
