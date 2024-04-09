package com.github.forax.einherjar.cli;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MainTest {

  @Test
  public void actionCheck() {
    var cmdLine = Main.CmdLine.parse("check foo.jar".split(" "));
    assertAll(
        () -> assertEquals(Main.Action.CHECK, cmdLine.action),
        () -> assertEquals(Map.of(), cmdLine.optionMap),
        () -> assertEquals(Path.of("foo.jar"), cmdLine.jarFile)
    );
  }

  @Test
  public void actionCheck2() {
    var cmdLine = Main.CmdLine.parse("check --annotation bar foo.jar".split(" "));
    assertAll(
        () -> assertEquals(Main.Action.CHECK, cmdLine.action),
        () -> assertEquals(Map.of(Main.Option.Kind.ANNOTATION_NAME, "bar"), cmdLine.optionMap),
        () -> assertEquals(Path.of("foo.jar"), cmdLine.jarFile)
    );
  }

  @Test
  public void actionFind() {
    var cmdLine = Main.CmdLine.parse("find foo.jar".split(" "));
    assertAll(
        () -> assertEquals(Main.Action.FIND, cmdLine.action),
        () -> assertEquals(Map.of(), cmdLine.optionMap),
        () -> assertEquals(Path.of("foo.jar"), cmdLine.jarFile)
    );
  }

  @Test
  public void actionEnhance() {
    var cmdLine = Main.CmdLine.parse("enhance foo.jar".split(" "));
    assertAll(
        () -> assertEquals(Main.Action.ENHANCE, cmdLine.action),
        () -> assertEquals(Map.of(), cmdLine.optionMap),
        () -> assertEquals(Path.of("foo.jar"), cmdLine.jarFile)
    );
  }

  @Test
  public void actionEnhance2() {
    var cmdLine = Main.CmdLine.parse("enhance --annotation bar foo.jar".split(" "));
    assertAll(
        () -> assertEquals(Main.Action.ENHANCE, cmdLine.action),
        () -> assertEquals(Map.of(Main.Option.Kind.ANNOTATION_NAME, "bar"), cmdLine.optionMap),
        () -> assertEquals(Path.of("foo.jar"), cmdLine.jarFile)
    );
  }


  @Test
  public void badNoAction() {
    var exception = assertThrows(IllegalArgumentException.class, () -> Main.CmdLine.parse(new String[0]));
    assertEquals("no action defined", exception.getMessage());
  }

  @Test
  public void badNoJarFile() {
    var exception = assertThrows(IllegalArgumentException.class, () -> Main.CmdLine.parse("check".split(" ")));
    assertEquals("no jar file defined", exception.getMessage());
  }

  @Test
  public void badNoJarFile2() {
    var exception =  assertThrows(IllegalArgumentException.class, () -> Main.CmdLine.parse("check --annotation foo".split(" ")));
    assertEquals("no jar file defined", exception.getMessage());
  }

  @Test
  public void badInvalidOptionName() {
    var exception = assertThrows(IllegalArgumentException.class, () -> Main.CmdLine.parse("check --invalid".split(" ")));
    assertEquals("unknown option --invalid", exception.getMessage());
  }

  @Test
  public void badOptionNoValue() {
    var exception = assertThrows(IllegalArgumentException.class, () -> Main.CmdLine.parse("check --annotation".split(" ")));
    assertEquals("no value defined for option --annotation", exception.getMessage());
  }
}