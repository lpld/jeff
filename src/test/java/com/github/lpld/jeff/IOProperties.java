package com.github.lpld.jeff;

import com.github.lpld.jeff.functions.Fn;
import com.github.lpld.jeff.generators.IOGen;
import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;

/**
 * @author leopold
 * @since 21/10/18
 */
@RunWith(JUnitQuickcheck.class)
public class IOProperties {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @BeforeClass
  public static void init() {
    Resources.initExecutors(20);
  }

  @AfterClass
  public static void cleanUp() {
    Resources.shutdownAll();
  }

  @Property
  public void fail(@IOGen(shouldFail = true) IO<?> io) {
    thrown.expect(RuntimeException.class);
    io.run();
  }

  @Property
  public void recover(@IOGen(canFail = false) IO<?> io) {
    io.run();
  }

  @Property
  public <T> void functorLaw_Identity(IO<T> io) {
    checkSameIO(io.map(Fn.id()), io);
  }

  @Property
  public <T, U, V> void functorLaw_Associativity(IO<T> io, Fn<T, U> f1, Fn<U, V> f2) {
    checkSameIO(
        io.map(f1).map(f2),
        io.map(f1.andThen(f2))
    );
  }

  @Property
  public <T, U> void monadLaw_LeftIdentity(T value, Fn<T, IO<U>> fn) {
    checkSameIO(
        IO.Pure(value).flatMap(fn),
        fn.ap(value)
    );
  }

  @Property
  public <T> void monadLaw_RightIdentity(IO<T> value) {
    checkSameIO(
        value.flatMap(IO::Pure),
        value
    );
  }

  @Property
  public <T, U, V> void monadLaw_Associativity(IO<T> io, Fn<T, IO<U>> f1, Fn<U, IO<V>> f2) {
    checkSameIO(
        io.flatMap(f1).flatMap(f2),
        io.flatMap(t -> f1.ap(t).flatMap(f2))
    );
  }

  @Property
  public <T, U> void map(IO<T> io, Fn<T, U> fn) {
    resultsShouldBeEqual(
        () -> io.map(fn).run(),
        () -> fn.ap(io.run())
    );
  }

  @Property
  public <T, U> void flatMap(IO<T> io, Fn<T, IO<U>> f) {
    resultsShouldBeEqual(
        () -> io.flatMap(f).run(),
        () -> f.ap(io.run()).run()
    );
  }

  @Property
  public <T, U> void mapPureFlatMap(IO<T> io, Fn<T, U> fn) {
    checkSameIO(
        io.map(fn),
        io.flatMap(fn.andThen(IO::Pure))
    );
  }

  private static <T> void resultsShouldBeEqual(Supplier<T> s1, Supplier<T> s2) {

    Exception err1 = null;
    Exception err2 = null;

    T res1 = null;
    T res2 = null;
    try {
      res1 = s1.get();
    } catch (Exception e) {
      err1 = e;
    }

    try {
      res2 = s2.get();
    } catch (Exception e) {
      err2 = e;
    }

    if (err1 != null || err2 != null) {
      assertEquals(err1, err2);
    } else {
      assertEquals(res1, res2);
    }
  }

  private static <T> void checkSameIO(IO<T> io1, IO<T> io2) {
    resultsShouldBeEqual(io1::run, io2::run);
  }
}
