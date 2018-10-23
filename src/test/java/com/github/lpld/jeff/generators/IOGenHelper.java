package com.github.lpld.jeff.generators;

import com.github.lpld.jeff.IO;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.generator.Generators;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.util.Optional;

/**
 * @author leopold
 * @since 22/10/18
 */
@lombok.RequiredArgsConstructor
public class IOGenHelper {

  final Generators generators;
  final Generator<?> compGen;
  final SourceOfRandomness random;
  final GenerationStatus status;
  final int executorsSize;

  IO<Object> randomIO(int maxSize, boolean canFail, boolean canRecover) {

    IO<Object> io = IO.Pure(randomObject());
    for (int i = 0; i < random.nextInt(maxSize); i++) {
      io = chainRandom(io, canFail, canRecover, maxSize - i);
    }
    return io;
  }

  IO<Object> chainRandom(IO<Object> io, boolean canFail, boolean canRecover, int maxNestedSize) {
    switch (random.nextInt(7)) {
      case 0:
        final Object mappedValue = randomObject();
        return io.map(fn -> mappedValue);

      case 1:
        if (canFail) {
          return io.chain(IO.Fail(randomError()));
        } else {
          return io;
        }

      case 2:
        if (canRecover) {
          final Object recValue = randomObject();
          return io.recover(err -> Optional.of(IO.Pure(recValue)));
        } else {
          final TestException failWith = randomError();
          return io.recoverWith(err -> Optional.of(IO.Fail(failWith)));
        }

      case 3:
        final Object delayValue = randomObject();
        return io.chain(IO.Delay(() -> delayValue));

      case 4:
        final IO<Object> finalIo = io;
        return IO.Suspend(() -> finalIo);

      case 5:
        final IO<Object> nestedIO = randomIO(maxNestedSize, canFail, canRecover);
        return io.flatMap(any -> nestedIO);

      case 6:
        if (executorsSize > 0) {
          return io
              .chain(IO.Fork(Resources.executor(random.nextInt(executorsSize))))
              .chain(IO.Pure(randomObject()));
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
