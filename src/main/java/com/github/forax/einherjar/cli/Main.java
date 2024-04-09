package com.github.forax.einherjar.cli;

import com.github.forax.einherjar.api.ValueType;
import com.github.forax.einherjar.core.Facade;
import com.github.forax.einherjar.core.ValueTypeChecker;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class Main {
  enum Action {
    CHECK, FIND, ENHANCE;

    static Action parse(String actionName) {
      switch (actionName) {
        case "check": return Action.CHECK;
        case "find": return Action.FIND;
        case "enhance": return Action.ENHANCE;
        default: throw new IllegalArgumentException("unknown action " + actionName);
      }
    }
  }

  static class Option {
    enum Kind {
      ANNOTATION_NAME,
      OUTPUT,
      VERSION
    }

    final Kind kind;
    final Object value;

    Option(Kind kind, Object value) {
      this.kind = kind;
      this.value = value;
    }


    static Option parseOption(String option, Iterator<String> optionValue) {
      try {
        switch (option) {
          case "--annotation":
            return new Option(Kind.ANNOTATION_NAME, optionValue.next());
          case "--output":
            return new Option(Kind.OUTPUT, Paths.get(optionValue.next()));
          case "--version":
            return new Option(Kind.OUTPUT, optionValue.next());
          default:
            throw new IllegalArgumentException("unknown option " + option);
        }
      } catch (NoSuchElementException e) {
        throw new IllegalArgumentException("no value defined for option " + option);
      }
    }
  }

  private static String help() {
    return String.format(
      "java %s action [--option optionValue] jarfile\n" +
      "  execute the action on the jarfile\n" +
      "\n" +
      "  action:\n" +
      "    check:   check if the annotated classes can be value types\n" +
      "    find:    find all classes that can be value types\n" +
      "    enhance: rewrite annotated classes to be value types using a multi-release jar\n" +
      "\n" +
      "  option:\n" +
      "    --annotation name: set the qualified name of the annotation\n",
        Main.class.getName());
  }

  static final class CmdLine {
    final Action action;
    final EnumMap<Option.Kind, Object> optionMap;
    final Path jarFile;

    CmdLine(Action action, EnumMap<Option.Kind, Object> optionMap, Path jarFile) {
      this.action = action;
      this.optionMap = optionMap;
      this.jarFile = jarFile;
    }

    static private EnumMap<Option.Kind, Object> defaultOptionMap() {
      EnumMap<Option.Kind, Object> optionMap = new EnumMap<>(Option.Kind.class);
      optionMap.put(Option.Kind.ANNOTATION_NAME, ValueType.class.getName());
      optionMap.put(Option.Kind.VERSION, 23);
      return optionMap;
    }

    static CmdLine parse(String[] args) throws IllegalArgumentException {
      if (args.length == 0) {
        throw new IllegalArgumentException("no action defined");
      }
      Action action = Action.parse(args[0]);
      EnumMap<Option.Kind, Object> optionMap = defaultOptionMap();
      Path jarFile = null;

      Iterator<String> iterator = Arrays.asList(args).subList(1, args.length).iterator();
      while(iterator.hasNext()) {
        String optionName = iterator.next();
        if (!optionName.startsWith("--")) {
          if (iterator.hasNext()) {
            throw new IllegalArgumentException("options should start with '--' " + optionName);
          }
          jarFile = Paths.get(optionName);
          break;
        }
        Option option = Option.parseOption(optionName, iterator);
        if (optionMap.putIfAbsent(option.kind, option.value) != null) {
          throw new IllegalArgumentException("option " + option + " defined twice");
        }
      }

      if (jarFile == null) {
        throw new IllegalArgumentException("no jar file defined");
      }
      return new CmdLine(action, optionMap, jarFile);
    }
  }

  private static Path defaultEnhancedJarName(Path jarFile) {
    String filename = jarFile.getFileName().toString();
    int extensionIndex = filename.lastIndexOf('.');
    String enhancedFilename = extensionIndex == 1? filename : filename.substring(0, extensionIndex);
    return jarFile.resolveSibling(enhancedFilename + "-enhanced.jar");
  }

  public static void main(String[] args) throws IOException {
    CmdLine cmdLine;
    try {
      cmdLine = CmdLine.parse(args);
    } catch (IllegalArgumentException e) {
      System.err.println("command line: " + e.getMessage() + "\n");
      System.err.println(help());
      System.exit(1);
      return;
    }

    ValueTypeChecker.IssueReporter issueReporter = (issue, className, message) -> {
      System.err.println(issue + ": class " + className + ", " + message);
    };

    switch (cmdLine.action) {
      case CHECK: {
        String annotationName = (String) cmdLine.optionMap.get(Option.Kind.ANNOTATION_NAME);
        Facade.check(annotationName, cmdLine.jarFile, issueReporter);
        break;
      }
      case FIND: {
        Facade.find(cmdLine.jarFile, className -> {
          System.out.println("found potential value class " + className.replace('/', '.'));
        });
        break;
      }
      case ENHANCE: {
        String annotationName = (String) cmdLine.optionMap.get(Option.Kind.ANNOTATION_NAME);
        Path toPath = (Path) cmdLine.optionMap.getOrDefault(Option.Kind.OUTPUT, defaultEnhancedJarName(cmdLine.jarFile));
        int version = (int) cmdLine.optionMap.get(Option.Kind.VERSION);
        Facade.enhance(annotationName, cmdLine.jarFile, toPath, version, issueReporter);
        break;
      }
    }
  }
}
