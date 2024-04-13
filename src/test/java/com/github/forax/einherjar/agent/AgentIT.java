package com.github.forax.einherjar.agent;

import com.github.forax.einherjar.api.ValueType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AgentIT {

  @Nested
  class Acmp {

    @Test
    public void testPointSame() {
      @ValueType
      record Point(int x, int y) {
      }

      assertSame(new Point(1, 2), new Point(1, 2));
    }

    @Test
    public void testPointNotTheSame() {
      @ValueType
      record Point(int x, int y) {
      }

      assertNotSame(new Point(1, 2), new Point(2, 2));
    }

    @Test
    public void testPrimitiveNumeric() {
      @ValueType
      record Value(boolean z, byte b, short s, char c, int i, long l) {
      }

      assertSame(
          new Value(true, (byte) 1, (short) 2, '3', 4, 5L),
          new Value(true, (byte) 1, (short) 2, '3', 4, 5L));
    }

    @Test
    public void testFloat() {
      @ValueType
      record Value(float f) {
      }

      assertSame(new Value(2.0f), new Value(2.0f));
      assertSame(new Value(Float.NaN), new Value(Float.NaN));
    }

    @Test
    public void testDouble() {
      @ValueType
      record Value(double d) {
      }

      assertSame(new Value(2.0), new Value(2.0));
      assertSame(new Value(Double.NaN), new Value(Double.NaN));
    }

    @Test
    public void testReference() {
      @ValueType
      record Value(Object o) {
      }

      var ref = new Object();
      assertSame(new Value(ref), new Value(ref));
    }

    @Test
    public void testReferenceNotTheSame() {
      @ValueType
      record Value(Object o) {
      }

      var ref = new Object();
      var ref2 = new Object();
      assertNotSame(new Value(ref), new Value(ref2));
    }

    @Test
    public void testRecursion() {
      @ValueType
      record A(int v) {
      }
      @ValueType
      record B(A a) {
      }

      var b1 = new B(new A(42));
      var b2 = new B(new A(42));
      assertSame(b1, b2);
    }

    @Test
    public void testRecursionNotTheSame() {
      @ValueType
      record A(int v) {
      }
      @ValueType
      record B(A a) {
      }

      var b1 = new B(new A(42));
      var b2 = new B(new A(43));
      assertNotSame(b1, b2);
    }
  }

  @Nested
  class MonitorEnter {
    @Test
    public void monitorEnter() {
      @ValueType
      record Value() { }

      var lock = new Value();
      assertThrows(IllegalMonitorStateException.class, () -> {
        synchronized (lock) {
          // do nothing here
        }
      });
    }

    @Test
    public void monitorEnterNoException() {
      var lock = new Object();
      synchronized (lock) {
        // do nothing here
      }
    }

    @Test
    public void monitorEnterNullValue() {
      @ValueType
      record Value() { }

      var lock = (Value) null;
      assertThrows(NullPointerException.class, () -> {
        synchronized (lock) {
          // do nothing here
        }
      });
    }
  }

  @Nested
  class IdentityHashCode {
    @Test
    public void identityHashCode() {
      @ValueType
      record Point(int x, int y) { }

      assertEquals(
          System.identityHashCode(new Point(1, 2)),
          System.identityHashCode(new Point(1, 2)));
    }

    @Test
    public void identityHashCodeNotEquals() {
      @ValueType
      record Point(int x, int y) { }

      assertNotEquals(
          System.identityHashCode(new Point(1, 2)),
          System.identityHashCode(new Point(2, 1)));
    }

    @Test
    public void identityHashCodeNumeric() {
      @ValueType
      record Value(boolean z, byte b, short s, char c, int i, long l) { }

      assertEquals(
          System.identityHashCode(new Value(true, (byte) 1, (short) 2, '3', 4, 5L)),
          System.identityHashCode(new Value(true, (byte) 1, (short) 2, '3', 4, 5L)));
    }

    @Test
    public void identityHashCodeFloat() {
      @ValueType
      record Value(float f) { }

      assertEquals(
          System.identityHashCode(new Value(5f)),
          System.identityHashCode(new Value(5f)));
      assertEquals(
          System.identityHashCode(new Value(Float.NaN)),
          System.identityHashCode(new Value(Float.NaN)));
    }

    @Test
    public void identityHashCodeDouble() {
      @ValueType
      record Value(double d) { }

      assertEquals(
          System.identityHashCode(new Value(5.)),
          System.identityHashCode(new Value(5.)));
      assertEquals(
          System.identityHashCode(new Value(Double.NaN)),
          System.identityHashCode(new Value(Double.NaN)));
    }
  }

  @Nested
  class IdentityCheck {
    @Test
    public void phantomRef() {
      @ValueType
      record Value() { }

      var value = new Value();
      assertThrows(RuntimeException.class, () -> new PhantomReference<>(value, new ReferenceQueue<>()));
    }

    @Test
    public void softRef() {
      @ValueType
      record Value() { }

      var value = new Value();
      assertAll(
          () -> assertThrows(RuntimeException.class, () -> new SoftReference<>(value)),
          () -> assertThrows(RuntimeException.class, () -> new SoftReference<>(value, new ReferenceQueue<>()))
      );
    }

    @Test
    public void weakRef() {
      @ValueType
      record Value() { }

      var value = new Value();
      assertAll(
          () -> assertThrows(RuntimeException.class, () -> new WeakReference<>(value)),
          () -> assertThrows(RuntimeException.class, () -> new WeakReference<>(value, new ReferenceQueue<>()))
      );
    }
  }
}
