# einherjar
A Java tool that transforms a Java jar to an [Einherjar](https://en.wikipedia.org/wiki/Einherjar) to go to Valhalla.

einherjar is a simple Java tool that takes a jar with classes annotated with the annotation
[@ValueType](src/main/java/com/github/forax/einherjar/api/ValueType.java) (or your own annotation),
checks that the annotated classes can be value classes and generate an Einherjar.

An Einherjar, the generated jar, is a Multi-Release jar that
- run as usual with any VM able to run the input jar,
- run the annotated classes seen as value classes with a Valhalla enabled VM (and '--enable-preview').

### To build einherjar, use maven
With any Java 21+ distribution.

```bash
  mvn package
```

generates a file named `einherjar.jar` in `target`.

### To run einherjar
With any Java 8+ distribution.

Running einherjar with no action, prints the help
```bash
  java -jar target/einherjar.jar
```

einherjar provides several actions:
- `find` that helps you find the potential value classes,
- `check` that verifies that the annotated classes can be transformed to value classes,
- `enhance` that generate the Einherjar.

and several options:
- `--annotation name` to set the name of the annotation (e.g. com.github.forax.einherjar.api.ValueType)
- `--classes names` to set the name of classes to be checked/enhanced (e.g. com.acme.Foo,com.acme.Bar)
- `--output path` to set the name of the generated Einherjar
- `--version version` to set the classfile version of the generated value classes (always in preview)
