// SPDX-FileCopyrightText: © 2026 jkramer5103 <info@jkramertech.com>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import org.junit.Test;

public class SubprocessProcessBuilderTest {
  @Test
  public void createProcessBuilderInheritsAndOverridesEnvironment() {
    Map<String, String> environment =
        SubprocessProcessBuilder.create(
                new String[] {"command"},
                new String[] {
                  "MINESCRIPT_TEST_OVERRIDE=overridden", "MINESCRIPT_TEST_VALUE=one=two"
                })
            .environment();

    for (var inherited : System.getenv().entrySet()) {
      if (!inherited.getKey().equals("MINESCRIPT_TEST_OVERRIDE")
          && !inherited.getKey().equals("MINESCRIPT_TEST_VALUE")) {
        assertEquals(inherited.getValue(), environment.get(inherited.getKey()));
      }
    }
    assertEquals("overridden", environment.get("MINESCRIPT_TEST_OVERRIDE"));
    assertEquals("one=two", environment.get("MINESCRIPT_TEST_VALUE"));
  }
}
