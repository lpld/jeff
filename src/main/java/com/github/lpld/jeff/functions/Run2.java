package com.github.lpld.jeff.functions;

import com.github.lpld.jeff.data.Unit;

/**
 * @author leopold
 * @since 5/10/18
 */
public interface Run2<T1, T2> {

  void run(T1 t1, T2 t2);

  default Fn2<T1, T2, Unit> toFn2() {
    return (t1, t2) -> {
      run(t1, t2);
      return Unit.unit;
    };
  }
}
