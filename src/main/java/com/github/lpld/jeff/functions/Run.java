package com.github.lpld.jeff.functions;

import com.github.lpld.jeff.data.Unit;

/**
 * @author leopold
 * @since 5/10/18
 */
public interface Run {

  void run() throws Throwable;

  default Xn0<Unit> toXn0() {
    return () -> {
      run();
      return Unit.unit;
    };
  }
}
