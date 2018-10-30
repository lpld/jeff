package com.github.lpld.jeff.generators;

import com.github.lpld.jeff.IO;
import com.github.lpld.jeff.Resources;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Generators;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.util.Optional;

/**
 * @author leopold
 * @since 22/10/18
 */
class IOGenHelper {

  private final Generators generators;
  private final Generator<?> compGen;
  private final SourceOfRandomness random;
  private final GenerationStatus status;
  private final boolean canFork;

  IOGenHelper(Generators generators, Generator<?> compGen, SourceOfRandomness random,
              GenerationStatus status, boolean canFork) {
    if (canFork && Resources.size() <= 0) {
      throw new IllegalStateException("Cannot perform fork: no executors initialized");
    }
    this.generators = generators;
    this.compGen = compGen;
    this.random = random;
    this.status = status;
    this.canFork = canFork;
  }

  IO<Object> randomIO(int maxSize, boolean canFail, boolean canRecover) {

    IO<Object> io = IO.pure(randomObject());
    for (int i = 0; i < random.nextInt(maxSize); i++) {
      io = chainRandom(io, canFail, canRecover, maxSize - i);
    }
    return io;
  }

  private IO<Object> chainRandom(IO<Object> io, boolean canFail, boolean canRecover,
                                 int maxNestedSize) {
    switch (random.nextInt(7)) {
      case 0:
        final Object mappedValue = randomObject();
        return io.map(fn -> mappedValue);

      case 1:
        if (canFail) {
          return io.chain(IO.fail(randomError()));
        } else {
          return io;
        }

      case 2:
        if (canRecover) {
          final Object recValue = randomObject();
          return io.recover(err -> Optional.of(IO.pure(recValue)));
        } else {
          final TestException failWith = randomError();
          return io.recoverWith(err -> Optional.of(IO.fail(failWith)));
        }

      case 3:
        final Object delayValue = randomObject();
        return io.chain(IO.delay(() -> delayValue));

      case 4:
        final IO<Object> finalIo = io;
        return IO.suspend(() -> finalIo);

      case 5:
        final IO<Object> nestedIO = randomIO(maxNestedSize, canFail, canRecover);
        return io.flatMap(any -> nestedIO);

      case 6:
        if (canFork) {
          return io
              .fork(Resources.executor(random.nextInt(Resources.size())))
              .chain(IO.pure(randomObject()));
        } else {
          return io;
        }
      default:
        throw new IllegalStateException();
    }
  }

  Object randomObject() {
    return compGen.generate(random, status);
  }

  TestException randomError() {
    final String message = generators.type(String.class).generate(random, status);
    return new TestException(message);
  }
}
