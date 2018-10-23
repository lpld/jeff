package com.github.lpld.jeff.generators;

import com.pholser.junit.quickcheck.generator.GeneratorConfiguration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author leopold
 * @since 23/10/18
 */
@Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
@GeneratorConfiguration
public @interface IOGen {

  int maxDepth() default Defaults.maxDepth;
  boolean shouldFail() default Defaults.shouldFail;
  boolean canFail() default Defaults.canFail;
  boolean canFork() default Defaults.canFork;

  final class Defaults {

    static final int maxDepth = 100;
    static final boolean shouldFail = false;
    static final boolean canFail = true;
    static final boolean canFork = true;
  }
}
