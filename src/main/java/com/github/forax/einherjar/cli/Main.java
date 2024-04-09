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
    return
      "java -jar target/einherjar.jar action [--option optionValue] jarfile\n" +
      "  execute the action on the jarfile\n" +
      "\n" +
      "  action:\n" +
      "    check:   check if the annotated classes can be value types\n" +
      "    find:    find all classes that can be value types\n" +
      "    enhance: rewrite annotated classes to be value types using a multi-release jar\n" +
      "\n" +
      "  option:\n" +
      "    --annotation name: set the qualified name of the annotation\n" +
      "    --output path: path of the enhanced jar\n" +
      "    --version version: classfile version of the generated value class";
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

    static CmdLine parse(String[] args) throws IllegalArgumentException {
      if (args.length == 0) {
        throw new IllegalArgumentException("no action defined");
      }
      Action action = Action.parse(args[0]);
      EnumMap<Option.Kind, Object> optionMap = new EnumMap<>(Option.Kind.class);
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

    // compute default values
    String annotationName = (String) cmdLine.optionMap.computeIfAbsent(Option.Kind.ANNOTATION_NAME, __ -> ValueType.class.getName());
    Path toPath = (Path) cmdLine.optionMap.computeIfAbsent(Option.Kind.OUTPUT, __ -> defaultEnhancedJarName(cmdLine.jarFile));
    int version = (int) cmdLine.optionMap.getOrDefault(Option.Kind.VERSION, 23);

    ValueTypeChecker.IssueReporter issueReporter = (issue, className, message) -> {
      System.err.println(issue + ": class " + className + ", " + message);
    };

    switch (cmdLine.action) {
      case CHECK:
        Facade.check(annotationName, cmdLine.jarFile, issueReporter);
        return;
      case FIND:
        Facade.find(cmdLine.jarFile, className -> {
          System.out.println("found potential value class " + className.replace('/', '.'));
        });
        return;
      case ENHANCE:
        Facade.enhance(annotationName, cmdLine.jarFile, toPath, version, issueReporter);
        return;
    }
  }
}
