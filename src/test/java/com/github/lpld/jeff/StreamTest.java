package com.github.lpld.jeff;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.github.lpld.jeff.Stream.Concat;
import static com.github.lpld.jeff.Stream.Cons;
import static com.github.lpld.jeff.Stream.Nil;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author leopold
 * @since 8/10/18
 */
public class StreamTest {

  private static List<Stream<Integer>> streams = Arrays.asList(
      Cons(1, Cons(2, Cons(3, Cons(4, Cons(5, Nil()))))),

      Concat(Cons(1, Cons(2, Nil())),
             Cons(3, Cons(4, Cons(5, Nil())))),

      Concat(Nil(),
             Concat(Cons(1, Cons(2, Nil())),
                    Cons(3, Cons(4, Cons(5, Nil()))))),

      Concat(Concat(Cons(1, Cons(2, Nil())),
                    Concat(Cons(3, Cons(4, Nil())),
                           Concat(Cons(5, Nil()),
                                  Nil()))),
             Nil())
  );

  @Test
  public void testFoldRight() {

    for (Stream<Integer> stream : streams) {
      final LList<Integer> result = stream
          .foldRight(LNil.<Integer>instance(), (el, ll) -> ll.prepend(el))
          .run();

      assertThat(result, is(equalTo(LList.of(1, 2, 3, 4, 5))));
    }
  }

  @Test
  public void testFoldLeft() {

    for (Stream<Integer> stream : streams) {
      final LList<Integer> result = stream
          .foldLeft(LNil.<Integer>instance(), LList::prepend)
          .run();

      assertThat(result, is(equalTo(LList.of(5, 4, 3, 2, 1))));
    }
  }
}
