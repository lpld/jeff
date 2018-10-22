package com.github.lpld.jeff.generators;

import com.github.lpld.jeff.IO;
import com.pholser.junit.quickcheck.generator.ComponentizedGenerator;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.util.Optional;

import static com.github.lpld.jeff.IO.Pure;

/**
 * @author leopold
 * @since 23/10/18
 */
public class IOGenerator extends ComponentizedGenerator<IO> {

  private IOGen conf;

  public IOGenerator() {
    super(IO.class);
  }

  public void configure(IOGen conf) {
    this.conf = conf;
  }

  public int numberOfNeededComponents() {
    return 1;
  }

  @Override
  public IO generate(SourceOfRandomness random, GenerationStatus status) {

    if (conf.pools() > 0) {
      Resources.initExecutors(conf.pools());
    }
    final IOGenHelper generator =
        new IOGenHelper(componentGenerators().get(0), random, status, conf.pools());
    final IO<Object> io1 = generator.randomIO(70, true, true);

    if (conf.shouldFail()) {
      return io1
          .chain(IO.Fail(new RuntimeException()))
          .chain(generator.randomIO(50, true, false));
    } else {
      final Object r = generator.randomObject();
      return io1
          .recover(err -> Optional.of(Pure(r)))
          .chain(generator.randomIO(50, false, true));
    }
  }

}
