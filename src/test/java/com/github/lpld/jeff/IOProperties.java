package com.github.lpld.jeff;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;

import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author leopold
 * @since 21/10/18
 */
@RunWith(JUnitQuickcheck.class)
public class IOProperties {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Property
  public void pureValues(String s) {
    final IO<String> pure = IO.Pure(s);
    assertThat(pure.run(), equalTo(s));
    assertThat(pure.run(), equalTo(s));
  }

  @Property
  public void delayValues(String s) {
    final AtomicInteger i = new AtomicInteger();

    final IO<String> io = IO.Delay(() -> {
      i.incrementAndGet();
      return s;
    });

    assertThat(i.get(), is(0));
    assertThat(io.run(), is(s));
    assertThat(i.get(), is(1));
    assertThat(io.run(), is(s));
    assertThat(i.get(), is(2));
  }

  @Property
  public void fail(String message) {
    IO<Object> fail = IO.Fail(new RuntimeException(message));
    thrown.expect(RuntimeException.class);
    thrown.expectMessage(message);
    fail.run();
  }
}
