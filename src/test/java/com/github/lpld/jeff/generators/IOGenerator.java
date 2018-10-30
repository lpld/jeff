package com.github.lpld.jeff.generators;

import com.github.lpld.jeff.IO;
import com.pholser.junit.quickcheck.generator.ComponentizedGenerator;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.util.Optional;

/**
 * @author leopold
 * @since 23/10/18
 */
public class IOGenerator extends ComponentizedGenerator<IO> {

  private int maxDepth = IOGen.Defaults.maxDepth;
  private boolean shouldFail = IOGen.Defaults.shouldFail;
  private boolean canFail = IOGen.Defaults.canFail;
  private boolean canFork = IOGen.Defaults.canFork;

  public IOGenerator() {
    super(IO.class);
  }

  public void configure(IOGen conf) {
    this.canFork = conf.canFork();
    this.maxDepth = conf.maxDepth();
    this.canFail = conf.canFail();
    this.shouldFail = conf.shouldFail();
  }

  public int numberOfNeededComponents() {
    return 1;
  }

  @Override
  public IO generate(SourceOfRandomness random, GenerationStatus status) {
    final double failureProbability;
    if (shouldFail) {
      failureProbability = 1.0;
    } else if (canFail) {
      failureProbability = 0.25;
    } else {
      failureProbability = 0.0;
    }

    final int depth1 = (int) Math.round(maxDepth * .7);
    final int depth2 = maxDepth - depth1;
    final IOGenHelper generator =
        new IOGenHelper(gen(), componentGenerators().get(0), random, status, canFork);
    final IO<Object> io1 = generator.randomIO(depth1, true, true);

    final boolean willFail = random.nextDouble() < failureProbability;

    if (willFail) {
      return io1
          .chain(IO.fail(generator::randomError))
          .chain(generator.randomIO(depth2, true, false));
    } else {
      final Object r = generator.randomObject();
      return io1
          .recover(err -> Optional.of(IO.pure(r)))
          .chain(generator.randomIO(depth2, false, true));
    }
  }
}
