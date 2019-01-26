package com.github.lpld.jeff.functions;

import com.github.lpld.jeff.data.Unit;

/**
 * @author leopold
 * @since 5/10/18
 */
public interface Run1<T> {

  void run(T run);

  default Fn<T, Unit> toFn() {
    return (t) -> {
      run(t);
      return Unit.unit;
    };
  }
}
