package com.github.lpld.jeff.functions;

import com.github.lpld.jeff.Unit;

/**
 * @author leopold
 * @since 5/10/18
 */
public interface Run {

  void run() throws Throwable;

  default F0<Unit> toF0() {
    return () -> {
      run();
      return Unit.unit;
    };
  }
}
