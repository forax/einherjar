package com.github.forax.einherjar.cli;

import com.github.forax.einherjar.api.ValueType;
import com.github.forax.einherjar.core.Facade;
import com.github.forax.einherjar.core.ValueTypeChecker;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableSet;

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

  static class Option<T> {
    static final class Kind<T> {
      public static final Kind<String> ANNOTATION_NAME = new Kind<>();
      public static final Kind<Set<String>> CLASS_SET = new Kind<>();
      public static final Kind<Path> OUTPUT = new Kind<>();
      public static final Kind<Integer> VERSION = new Kind<>();

      private Kind() {
      }
    }

    final Kind<T> kind;
    final T value;

    Option(Kind<T> kind, T value) {
      this.kind = kind;
      this.value = value;
    }

    private static Set<String> splitAsClassSet(String classlist) {
      String[] parts = classlist.split(" *, *");
      return unmodifiableSet(new HashSet<>(Arrays.asList(parts)));
    }

    static Option<?> parseOption(String option, Iterator<String> optionValue) {
      try {
        switch (option) {
          case "--annotation":
            return new Option<>(Kind.ANNOTATION_NAME, optionValue.next());
          case "--classes":
            return new Option<>(Kind.CLASS_SET, splitAsClassSet(optionValue.next()));
          case "--output":
            return new Option<>(Kind.OUTPUT, Paths.get(optionValue.next()));
          case "--version":
            return new Option<>(Kind.VERSION, Integer.parseInt(optionValue.next()));
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
      "    --classes nameset: a comma separated set of qualified class names\n" +
      "    --output path: path of the enhanced jar\n" +
      "    --version version: classfile version of the generated value class";
  }

  static final class CmdLine {
    final Action action;
    final Map<Option.Kind<?>, Object> optionMap;
    final Path jarFile;

    CmdLine(Action action, Map<Option.Kind<?>, Object> optionMap, Path jarFile) {
      this.action = action;
      this.optionMap = optionMap;
      this.jarFile = jarFile;
    }

    @SuppressWarnings("unchecked")
    <T> T getOptionValue(Option.Kind<T> kind, Supplier<? extends T> supplier) {
      return (T) optionMap.computeIfAbsent(kind, __ -> supplier.get());
    }

    static CmdLine parse(String[] args) throws IllegalArgumentException {
      if (args.length == 0) {
        throw new IllegalArgumentException("no action defined");
      }
      Action action = Action.parse(args[0]);
      HashMap<Option.Kind<?>, Object> optionMap = new HashMap<>();
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
        Option<?> option = Option.parseOption(optionName, iterator);
        if (optionMap.putIfAbsent(option.kind, option.value) != null) {
          throw new IllegalArgumentException("option " + optionName + " defined twice");
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
    String annotationName = cmdLine.getOptionValue(Option.Kind.ANNOTATION_NAME, ValueType.class::getName);
    Set<String> classSet = cmdLine.getOptionValue(Option.Kind.CLASS_SET, HashSet::new);
    Path toPath = cmdLine.getOptionValue(Option.Kind.OUTPUT, () -> defaultEnhancedJarName(cmdLine.jarFile));
    int version = cmdLine.getOptionValue(Option.Kind.VERSION, () -> 23);

    ValueTypeChecker.IssueReporter issueReporter = (issue, className, message) -> {
      System.err.println(issue + ": class " + className + ", " + message);
    };

    switch (cmdLine.action) {
      case CHECK:
        Facade.check(annotationName, classSet, cmdLine.jarFile, issueReporter);
        return;
      case FIND:
        Facade.find(cmdLine.jarFile, className -> {
          System.out.println("found potential value class " + className.replace('/', '.'));
        });
        return;
      case ENHANCE:
        Facade.enhance(annotationName, classSet, cmdLine.jarFile, toPath, version, issueReporter);
        return;
    }
  }
}
