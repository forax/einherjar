package com.github.forax.einherjar.core;

import com.github.forax.einherjar.api.ValueType;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.V23;

public class FacadeTest {
  // --- test classes
  static class BadSuperClass extends InputStream {
    public int read() {
      throw new UnsupportedOperationException();
    }
  }
  static class BadFieldNotFinal {
    private String text;
  }
  static class BadThisEscape {
    public BadThisEscape() {
      oops();
    }
    private void oops() {}
  }

  static class GoodClass {
    private final int x, y;

    GoodClass(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }

  // --- annotated classes

  @ValueType
  static class BadSuperClassAnnotated extends InputStream {
    public int read() {
      throw new UnsupportedOperationException();
    }
  }
  @ValueType
  static class BadFieldNotFinalAnnotated {
    private String text;
  }
  @ValueType
  static class BadThisEscapeAnnotated {
    public BadThisEscapeAnnotated() {
      oops();
    }
    private void oops() {}
  }

  @ValueType
  static class GoodClassAnnotated {
    private final int x, y;

    GoodClassAnnotated(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }

  private record Resource(String pathname, byte[] content) {}

  private static Path createTestJar(Resource... resources) throws IOException {
    var directory = Files.createTempDirectory("--einherjar--facade--");
    var testJar = directory.resolve("test.jar");
    try(var output = Files.newOutputStream(testJar);
        var jarOutput = new JarOutputStream(output)) {
      for(var resource: resources) {
        jarOutput.putNextEntry(new JarEntry(resource.pathname));
        jarOutput.write(resource.content);
        jarOutput.closeEntry();
      }
    }
    return testJar;
  }

  private static Resource fromClass(Class<?> clazz) throws IOException {
    var pathname = clazz.getName().replace('.', '/') + ".class";
    byte[] content;
    try(var input = clazz.getResourceAsStream("/" + pathname)) {
      if (input == null) {
        throw new IOException(pathname + " not found");
      }
      content = input.readAllBytes();
    }
    return new Resource(pathname, content);
  }

  // ---

  @Test
  public void testCheck() throws URISyntaxException, IOException {
    var jarFile = createTestJar(
        fromClass(BadSuperClass.class),
        fromClass(BadFieldNotFinal.class),
        fromClass(BadThisEscape.class),
        fromClass(GoodClass.class),
        fromClass(BadSuperClassAnnotated.class),
        fromClass(BadFieldNotFinalAnnotated.class),
        fromClass(BadThisEscapeAnnotated.class),
        fromClass(GoodClassAnnotated.class));
    try {
      var counter = new Object() { int counter; };
      Facade.check(ValueType.class.getName(), Set.of(), jarFile, (issue, className, message) -> {
        switch (className) {
          case "com/github/forax/einherjar/core/FacadeTest$BadSuperClassAnnotated" ->
            assertSame(ValueTypeChecker.Issue.UNKNOWN_SUPER, issue);
          case "com/github/forax/einherjar/core/FacadeTest$BadFieldNotFinalAnnotated" ->
              assertSame(ValueTypeChecker.Issue.NON_FINAL_FIELD, issue);
          case "com/github/forax/einherjar/core/FacadeTest$BadThisEscapeAnnotated" ->
              assertSame(ValueTypeChecker.Issue.THIS_ESCAPE, issue);
          default -> throw new AssertionError(issue + " " + className + " " + message);
        }
        counter.counter++;
      });
      assertEquals(3, counter.counter);
    } finally {
      Files.delete(jarFile);
    }
  }

  @Test
  public void testCheckClassName() throws URISyntaxException, IOException {
    var jarFile = createTestJar(
        fromClass(BadSuperClass.class),
        fromClass(GoodClass.class));
    try {
      var counter = new Object() { int counter; };
      var classSet = Set.of(BadSuperClass.class.getName(), GoodClass.class.getName());
      Facade.check(ValueType.class.getName(), classSet, jarFile, (issue, className, message) -> {
        switch (className) {
          case "com/github/forax/einherjar/core/FacadeTest$BadSuperClass" ->
              assertSame(ValueTypeChecker.Issue.UNKNOWN_SUPER, issue);
          default -> throw new AssertionError(issue + " " + className + " " + message);
        }
        counter.counter++;
      });
      assertEquals(1, counter.counter);
    } finally {
      Files.delete(jarFile);
    }
  }

  @Test
  public void testFind() throws URISyntaxException, IOException {
    var jarFile = createTestJar(
        fromClass(BadSuperClass.class),
        fromClass(BadFieldNotFinal.class),
        fromClass(BadThisEscape.class),
        fromClass(GoodClass.class));
    try {
      Facade.find(jarFile, className -> assertEquals("com/github/forax/einherjar/core/FacadeTest$GoodClass", className));
    } finally {
      Files.delete(jarFile);
    }
  }

  @Test
  public void testEnhance() throws URISyntaxException, IOException {
    var jarFile = createTestJar(
        fromClass(GoodClassAnnotated.class));
    var enhancedJarFile = jarFile.resolveSibling("test-enhanced.jar");
    try {
      Facade.enhance(ValueType.class.getName(), Set.of(), jarFile, enhancedJarFile, 23, (issue, className, message) -> {
        throw new AssertionError(issue + " " + className + " " + message);
      });

      try(var resultJarFile = new JarFile(enhancedJarFile.toFile(), true, ZipFile.OPEN_READ, Runtime.Version.parse("23"))) {
        assertTrue(resultJarFile.isMultiRelease());
        var versionedEntry = resultJarFile.getJarEntry(fromClass(GoodClassAnnotated.class).pathname);
        assertNotNull(versionedEntry);
        try(var input = resultJarFile.getInputStream(versionedEntry)) {
          var reader = new ClassReader(input);
          reader.accept(new ClassVisitor(ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
              assertAll(
                  () -> assertEquals(V23 | 0xFFFF0000, version),
                  () -> assertEquals(0, access & ACC_SUPER)
              );
            }
          }, 0);
        }
      }
    } finally {
      Files.delete(enhancedJarFile);
      Files.delete(jarFile);
    }
  }

  @Test
  public void testEnhanceClassName() throws URISyntaxException, IOException {
    var jarFile = createTestJar(fromClass(GoodClass.class));
    var annotationName = ValueType.class.getName();
    var classSet = Set.of(GoodClass.class.getName());
    var enhancedJarFile = jarFile.resolveSibling("test-enhanced.jar");
    try {
      Facade.enhance(annotationName, classSet, jarFile, enhancedJarFile, 23, (issue, className, message) -> {
        throw new AssertionError(issue + " " + className + " " + message);
      });

      try(var resultJarFile = new JarFile(enhancedJarFile.toFile(), true, ZipFile.OPEN_READ, Runtime.Version.parse("23"))) {
        assertTrue(resultJarFile.isMultiRelease());
        var versionedEntry = resultJarFile.getJarEntry(fromClass(GoodClass.class).pathname);
        assertNotNull(versionedEntry);
        try(var input = resultJarFile.getInputStream(versionedEntry)) {
          var reader = new ClassReader(input);
          reader.accept(new ClassVisitor(ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
              assertAll(
                  () -> assertEquals(V23 | 0xFFFF0000, version),
                  () -> assertEquals(0, access & ACC_SUPER)
              );
            }
          }, 0);
        }
      }
    } finally {
      Files.delete(enhancedJarFile);
      Files.delete(jarFile);
    }
  }
}