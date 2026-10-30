package fr.craft.gabier.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProtocolVersionTest {

  @Nested
  class Validation {

    @Test
    void rejectsNegativeMajor() {
      assertThrows(IllegalArgumentException.class, () -> new ProtocolVersion(-1, 0));
    }

    @Test
    void rejectsNegativeMinor() {
      assertThrows(IllegalArgumentException.class, () -> new ProtocolVersion(0, -1));
    }
  }

  @Nested
  class Rendering {

    @Test
    void rendersAsMajorDotMinor() {
      assertEquals("2.7", new ProtocolVersion(2, 7).toString());
    }

    @Test
    void currentIsZeroPointOne() {
      assertEquals("0.1", ProtocolVersion.CURRENT.toString());
    }
  }
}
