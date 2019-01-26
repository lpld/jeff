package com.github.lpld.jeff;

import com.github.lpld.jeff.data.Unit;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.github.lpld.jeff.IO.IO;
import static java.lang.System.console;
import static java.lang.System.out;

/**
 * @author leopold
 * @since 11/10/18
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Console {

  public static IO<Unit> printLine(long longVal) {
    return printLine(Long.toString(longVal));
  }

  public static IO<Unit> printLine(String str) {
    return IO(() -> out.println(str));
  }

  public static IO<Unit> print(String str) {
    return IO(() -> out.print(str));
  }

  public static IO<String> readLine() {
    return IO(() -> console().readLine());
  }
}
