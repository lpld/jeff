package com.github.lpld.jeff.generators;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

/**
 * @author leopold
 * @since 23/10/18
 */
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class TestException extends RuntimeException {

  private final String value;
}

