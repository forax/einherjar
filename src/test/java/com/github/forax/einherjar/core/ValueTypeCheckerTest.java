package com.github.forax.einherjar.core;

import com.github.forax.einherjar.core.ValueTypeChecker.Issue;
import com.github.forax.einherjar.core.ValueTypeChecker.IssueReporter;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ValueTypeCheckerTest {
  private static byte[] load(Class<?> clazz) throws IOException {
    var filename = clazz.getName().replace('.', '/') + ".class";
    byte[] code;
    try(var input = clazz.getResourceAsStream('/' + filename)) {
      if (input == null) {
        throw new AssertionError();
      }
      code = input.readAllBytes();
    }
    return code;
  }

  private static String internalName(Class<?> clazz) {
    return clazz.getName().replace('.', '/');
  }

  @Test
  public void testEscapeThis() throws IOException {
    class EscapeThis {
      public EscapeThis() {
        System.out.println(this);
      }
    }

    var issueReporterCalled = new Object() { boolean called; };
    IssueReporter issueReporter = (issue, className, message) -> {
      assertEquals(Issue.THIS_ESCAPE, issue);
      assertEquals(internalName(EscapeThis.class), className);
      issueReporterCalled.called = true;
    };
    var reader = new ClassReader(load(EscapeThis.class));
    reader.accept(new ValueTypeChecker(issueReporter, null), 0);
    assertTrue(issueReporterCalled.called);
  }

  @Test
  public void testNoFinalField() throws IOException {
    class NoFinalField {
      private int x;
    }

    var issueReporterCalled = new Object() { boolean called; };
    IssueReporter issueReporter = (issue, className, message) -> {
      assertEquals(Issue.NON_FINAL_FIELD, issue);
      assertEquals(internalName(NoFinalField.class), className);
      issueReporterCalled.called = true;
    };
    var reader = new ClassReader(load(NoFinalField.class));
    reader.accept(new ValueTypeChecker(issueReporter, null), 0);
    assertTrue(issueReporterCalled.called);
  }
}