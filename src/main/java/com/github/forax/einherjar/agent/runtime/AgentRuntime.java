package com.github.forax.einherjar.agent.runtime;

import com.github.forax.einherjar.api.ValueType;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;

import static java.lang.invoke.MethodType.methodType;

public class AgentRuntime {
  public static final class Cache extends ClassValue<Field[]> {
    @Override
    protected Field[] computeValue(Class<?> type) {
      ArrayList<Field> fields = new ArrayList<Field>();
      for(Class<?> t = type; t != Object.class; t = t.getSuperclass()) {
        for(Field field : t.getDeclaredFields()) {
          field.setAccessible(true);
          fields.add(field);
        }
      }
      return fields.toArray(new Field[0]);
    }
  }

  private static final ClassValue<Field[]> FIELDS_CACHE = new Cache();

  private static final MethodHandle ACMP, MONITORENTER, IDENTITY_HASHCODE, IDENTITY_CHECK;
  static {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try {
      ACMP = lookup.findStatic(AgentRuntime.class, "acmp", methodType(boolean.class, Object.class, Object.class));
      MONITORENTER = lookup.findStatic(AgentRuntime.class, "monitorenter", methodType(void.class, Object.class));
      IDENTITY_HASHCODE = lookup.findStatic(AgentRuntime.class, "identityHashCode", methodType(int.class, Object.class));
      IDENTITY_CHECK = lookup.findStatic(AgentRuntime.class, "identityCheck", methodType(void.class, Object.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean equalsPrimitive(Object vt1, Object vt2, Field field, Class<?> fieldType) throws IllegalAccessException {
    switch (fieldType.getName()) {
      case "boolean":
        return field.getBoolean(vt1) == field.getBoolean(vt2);
      case "byte":
        return field.getByte(vt1) == field.getByte(vt2);
      case "char":
        return field.getChar(vt1) == field.getChar(vt2);
      case "short":
        return field.getShort(vt1) == field.getShort(vt2);
      case "int":
        return field.getInt(vt1) == field.getInt(vt2);
      case "long":
        return field.getLong(vt1) == field.getLong(vt2);
      case "float":
        return Float.floatToRawIntBits(field.getFloat(vt1)) == Float.floatToRawIntBits(field.getFloat(vt2));
      case "double":
        return Double.doubleToRawLongBits(field.getDouble(vt1)) == Double.doubleToRawLongBits(field.getDouble(vt2));
      default:
        throw new AssertionError();
    }
  }

  private static boolean equalsValue(Object vt1, Object vt2) throws IllegalAccessException {
    Field[] fields = FIELDS_CACHE.get(vt1.getClass());
    for(Field field : fields) {
      Class<?> fieldType = field.getType();
      boolean matches = fieldType.isPrimitive() ?
          equalsPrimitive(vt1, vt2, field, fieldType) :
          acmp(field.get(vt1), field.get(vt2));
      if (!matches) {
        return false;
      }
    }
    return true;
  }

  public static boolean acmp(Object o1, Object o2) throws IllegalAccessException {
    if (o1 == o2) {
      return true;
    }
    if (o1 == null || o2 == null) {
      return false;
    }
    if (o1.getClass() == o2.getClass() && o1.getClass().isAnnotationPresent(ValueType.class)) {
      return equalsValue(o1, o2);
    }
    return false;
  }

  public static void monitorenter(Object lock) {
    if (lock == null) {
      return;  // the NPE will be raised by the real monitor enter
    }
    if (lock.getClass().isAnnotationPresent(ValueType.class)) {
      throw new IllegalMonitorStateException("Cannot create a monitor on a @ValueType");
    }
  }

  private static int hashPrimitive(Object vt, Field field, Class<?> fieldType) throws IllegalAccessException {
    switch (fieldType.getName()) {
      case "boolean":
        return field.getBoolean(vt) ? 1 : 0;
      case "byte":
        return field.getByte(vt) ;
      case "char":
        return field.getChar(vt) ;
      case "short":
        return field.getShort(vt);
      case "int":
        return field.getInt(vt);
      case "long":
        return Long.hashCode(field.getLong(vt));
      case "float":
        return Float.hashCode(field.getFloat(vt));
      case "double":
        return Double.hashCode(field.getDouble(vt));
      default:
        throw new AssertionError();
    }
  }

  private static int valueIdentityHashCode(Object vt) throws IllegalAccessException {
    Field[] fields = FIELDS_CACHE.get(vt.getClass());
    int hash = 1;
    for(Field field : fields) {
      Class<?> fieldType = field.getType();
      int value = fieldType.isPrimitive() ?
          hashPrimitive(vt, field, fieldType) :
          identityHashCode(field.get(vt));
      hash = 31 * hash + value;
    }
    return hash;
  }

  public static int identityHashCode(Object o) throws IllegalAccessException {
    if (o == null) {
      return 0;
    }
    if (o.getClass().isAnnotationPresent(ValueType.class)) {
      return valueIdentityHashCode(o);
    }
    return System.identityHashCode(o);
  }

  public static void identityCheck(Object o) {
    if (o == null) {
      return;
    }
    if (o.getClass().isAnnotationPresent(ValueType.class)) {
      throw new RuntimeException("Cannot create a reference on a @ValueType");
    }
  }

  private static MethodHandle target(String name) {
    switch (name) {
      case "acmp":
        return ACMP;
      case "monitorenter":
        return MONITORENTER;
      case "identityHashCode":
        return IDENTITY_HASHCODE;
      case "identityCheck":
        return IDENTITY_CHECK;
      default:
        throw new LinkageError("unknown target " + name);
    }
  }

  public static CallSite bsm(MethodHandles.Lookup lookup, String name, MethodType methodType) {
    // dumb implementation, can be faster if needed using an inlining cache
    return new ConstantCallSite(target(name));
  }
}
