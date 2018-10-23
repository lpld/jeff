package com.github.lpld.jeff.generators;

import com.github.lpld.jeff.functions.Fn;
import com.pholser.junit.quickcheck.generator.ComponentizedGenerator;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

/**
 * @author leopold
 * @since 23/10/18
 */
public class FnGenerator extends ComponentizedGenerator<Fn> {

  public FnGenerator() {
    super(Fn.class);
  }

  @Override
  public int numberOfNeededComponents() {
    return 2;
  }

  @Override
  public Fn generate(SourceOfRandomness random, GenerationStatus status) {
    final Object returnValue = componentGenerators().get(1).generate(random, status);
    return any -> returnValue;
  }
}
