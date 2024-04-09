# einherjar
A Java tool that transforms a Java jar to an [Einherjar](https://en.wikipedia.org/wiki/Einherjar) to go to Valhalla.

einherjar is a simple Java tool that takes a jar with classes annotated with the annotation
[@ValueType](src/main/java/com/github/forax/einherjar/api/ValueType.java) (or your own annotation),
checks that the annotated class can be value classes and generate a Multi-Release jar.

The generated jar, the Einherjar, is a Multi-Release jar that
- run as usual with any VM able to run the input jar,
- run with the annotated classes being seen as value classes with a Valhalla enabled VM.

### To build einherjar, use maven
Any Java 21 works

```bash
  mvn package
```

### To run einherjar
Any Java 8+ works

Running einherjar with no action, will give you the help
```bash
  java -jar target/einherjar.jar
```

einherjar provides several actions:
- `find` that helps you to find the potential value classes,
- `check` that verifies that the annotated classes can be transformed to value classes,
- `enhence` that generate an einherjar.

and several options:
- `--annotation name` to set the name of the annotation (e.g. com.github.forax.einherjar.api.ValueType)
- `--output path` to set the name of the generated Einherjar
- `--version version` to set the classfile version of the generated value classes






