package com.github.lpld.jeff.generators;

import com.github.lpld.jeff.IO;
import com.pholser.junit.quickcheck.generator.ComponentizedGenerator;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.util.Optional;

import static com.github.lpld.jeff.IO.Pure;

/**
 * @author leopold
 * @since 23/10/18
 */
public class IOGenerator extends ComponentizedGenerator<IO> {

  private int pools = 20;
  private double failureProbability = 0.3;
  private int depth = 100;

  public IOGenerator() {
    super(IO.class);
  }

  public void configure(IOGen conf) {
    this.pools = conf.pools();
    this.depth = conf.depth();
    if (conf.shouldFail()) {
      failureProbability = 1.0;
    } else if (conf.canFail()) {
      failureProbability = 0.3;
    } else {
      failureProbability = 0.0;
    }
  }

  public int numberOfNeededComponents() {
    return 1;
  }

  @Override
  public IO generate(SourceOfRandomness random, GenerationStatus status) {

    if (pools > 0) {
      Resources.initExecutors(pools);
    }

    final int depth1 = (int) Math.round(depth * .7);
    final int depth2 = depth - depth1;
    final IOGenHelper generator =
        new IOGenHelper(gen(), componentGenerators().get(0), random, status, pools);
    final IO<Object> io1 = generator.randomIO(depth1, true, true);

    final boolean willFail = random.nextDouble() < failureProbability;

    if (willFail) {
      return io1
          .chain(IO.Fail(generator.randomError()))
          .chain(generator.randomIO(depth2, true, false));
    } else {
      final Object r = generator.randomObject();
      return io1
          .recover(err -> Optional.of(Pure(r)))
          .chain(generator.randomIO(depth2, false, true));
    }
  }
}
