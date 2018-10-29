package com.github.lpld.jeff.data;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * @author leopold
 * @since 5/10/18
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Unit {
  public static final Unit unit = new Unit();

  @Override
  public String toString() {
    return "Unit";
  }
}
