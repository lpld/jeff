package com.github.lpld.jeff.generators;

import com.github.lpld.jeff.IO;
import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.util.Optional;

/**
 * @author leopold
 * @since 22/10/18
 */
@lombok.RequiredArgsConstructor
public class IOGenHelper {

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
        io = io.map(fn -> mappedValue);
        break;

      case 1:
        if (canFail) {
          io = io.chain(IO.Fail(new RuntimeException()));
        }
        break;

      case 2:
        if (canRecover) {
          final Object recValue = randomObject();
          io = io.recover(err -> Optional.of(IO.Pure(recValue)));
        } else {
          io = io.recoverWith(err -> Optional.of(IO.Fail(new RuntimeException())));
        }
        break;

      case 3:
        final Object delayValue = randomObject();
        io = io.chain(IO.Delay(() -> delayValue));
        break;

      case 4:
        final IO<Object> finalIo = io;
        io = IO.Suspend(() -> finalIo);
        break;

      case 5:
        final IO<Object> nestedIO = randomIO(maxNestedSize, canFail, canRecover);
        io = io.flatMap(any -> nestedIO);
        break;

      case 6:
        if (executorsSize > 0) {
          io = io
              .chain(IO.Fork(Resources.executor(random.nextInt(executorsSize))))
              .chain(IO.Pure(randomObject()));
        }
        break;
    }
    return io;
  }

  Object randomObject() {
    return compGen.generate(random, status);
  }
}
