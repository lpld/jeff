package com.github.lpld.jeff;

import org.junit.Test;

import static com.github.lpld.jeff.Trampoline.done;
import static com.github.lpld.jeff.Trampoline.more;

/**
 * @author leopold
 * @since 5/10/18
 */
public class TrampolineTest {

  private Trampoline<Long> fib(int n, long a, long b) {
    final long b2 = a + b;

    if (n > 0) {
      return more(() -> fib(n - 1, b, b2));
    } else {
      return done(b2);
    }
  }

  private long fibUnsafe(int n, long a, long b) {
    final long b2 = a + b;

    if (n > 0) {
      return fibUnsafe(n - 1, b, b2);
    } else {
      return b2;
    }
  }

  @Test
  public void testFib() {
    // fibUnsafe(100000, 0, 1);
    final Long fib = fib(100000, 0, 1).eval();
    System.out.println(fib);
  }
}
