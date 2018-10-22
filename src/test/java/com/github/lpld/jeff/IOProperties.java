package com.github.lpld.jeff;

import com.github.lpld.jeff.generators.IOGen;
import com.github.lpld.jeff.generators.Resources;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;

import org.junit.After;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

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

  @After
  public void cleanUp() {
    Resources.shutdownAll();
  }

  @Property
  public <T> void pureValues(T v) {
    final IO<T> pure = IO.Pure(v);
    assertThat(pure.run(), equalTo(v));
    assertThat(pure.run(), equalTo(v));
  }

  @Property
  public <T> void delayValues(T v) {
    final AtomicInteger i = new AtomicInteger();

    final IO<T> io = IO.Delay(() -> {
      i.incrementAndGet();
      return v;
    });

    assertThat(i.get(), is(0));
    assertThat(io.run(), is(v));
    assertThat(i.get(), is(1));
    assertThat(io.run(), is(v));
    assertThat(i.get(), is(2));
  }

  @Property
  public void fail(String message) {
    final IO<?> fail = IO.Fail(new RuntimeException(message));
    thrown.expect(RuntimeException.class);
    thrown.expectMessage(message);
    fail.run();
  }

  @Property
  public <T> void pureRecover(T v1, T v2) {

    T result = IO.Pure(v1)
        .recover(err -> Optional.of(v2))
        .run();

    assertThat(result, is(v1));
  }

  @Property
  public <T> void failRecover(T v) {

    T result = IO.<T>Fail(new RuntimeException())
        .recover(err -> Optional.of(v))
        .run();

    assertThat(result, is(v));
  }

  @Property
  public <T> void failRecoverFail(T v) {
    IO<?> failed = IO.<T>Fail(new RuntimeException("1"))
        .recover(err -> Optional.of(v))
        .chain(IO.Fail(new RuntimeException("2")));
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("2");
    failed.run();
  }

  @Property
  public void failRecoverFail2() {
    IO<?> failed = IO.Fail(new RuntimeException("1"))
        .recoverWith(err -> Optional.of(IO.Fail(new RuntimeException("2"))));
    thrown.expect(RuntimeException.class);
    thrown.expectMessage("2");
    failed.run();
  }

  @Property
  public void fail2(@IOGen(pools = 20, shouldFail = true) IO<Object> io) {
    thrown.expect(RuntimeException.class);
    io.run();
  }

  @Property
  public void recover2(@IOGen(pools = 20) IO<?> io) {
    io.run();
  }

  @Property
  public <T, U> void map(T s, Function<T, U> fn) {
    assertThat(IO.Pure(s).map(fn::apply).run(), equalTo(fn.apply(s)));
  }

  @Property
  public <T, U> void map2(@IOGen(pools = 20) IO<T> io, Function<T, U> fn) {
    assertThat(io.map(fn::apply).run(), equalTo(fn.apply(io.run())));
  }
}
