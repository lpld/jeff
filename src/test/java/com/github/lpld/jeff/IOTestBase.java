package com.github.lpld.jeff;

import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * @author leopold
 * @since 25/10/18
 */
public abstract class IOTestBase {

  @BeforeClass
  public static void init() {
    Resources.initExecutors(10);
  }

  @AfterClass
  public static void cleanUp() {
    Resources.shutdownAll();
  }

}
