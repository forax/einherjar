package com.github.forax.einherjar.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

public final class Facade {
  private Facade() {
    throw new AssertionError();
  }

  public static void check(String annotationName, Path path, ValueTypeChecker.IssueReporter issueReporter) throws IOException {
    Objects.requireNonNull(annotationName);
    Objects.requireNonNull(path);
    String annotationDescriptor = Type.getObjectType(annotationName.replace('.', '/')).getDescriptor();
    try(JarFile jarFile = new JarFile(path.toFile())) {
      for (JarEntry entry : Collections.list(jarFile.entries())) {
        if (!entry.getName().endsWith(".class")) {
          continue;  // skip entry
        }
        ClassReader reader;
        try (InputStream input = jarFile.getInputStream(entry)) {
          reader = new ClassReader(input);
        }

        IsClassAnnotated isClassAnnotated = new IsClassAnnotated(annotationDescriptor);
        reader.accept(isClassAnnotated, ClassReader.SKIP_CODE);
        if (isClassAnnotated.isAnnotated()) {
          ValueTypeChecker valueTypeChecker = new ValueTypeChecker(issueReporter, null);
          reader.accept(valueTypeChecker, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
      }
    }
  }

  public static void find(Path path, Consumer<String> potentialValueTypeConsumer) throws IOException {
    Objects.requireNonNull(path);
    try(JarFile jarFile = new JarFile(path.toFile())) {
      for (JarEntry entry : Collections.list(jarFile.entries())) {
        if (!entry.getName().endsWith(".class")) {
          continue;  // skip entry
        }
        ClassReader reader;
        try (InputStream input = jarFile.getInputStream(entry)) {
          reader = new ClassReader(input);
        }

        class IssueChecker implements ValueTypeChecker.IssueReporter {
          boolean hasIssue;

          @Override
          public void report(ValueTypeChecker.Issue issue, String className, String message) {
            hasIssue = true;
          }
        }
        IssueChecker issueChecker = new IssueChecker();
        ValueTypeChecker valueTypeChecker = new ValueTypeChecker(issueChecker, null);
        reader.accept(valueTypeChecker, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!issueChecker.hasIssue) {
          potentialValueTypeConsumer.accept(reader.getClassName());
        }
      }
    }
  }

  private static void copy(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[8_192];
    int read;
    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
  }

  private static final String MANIFEST_NAME = "META-INF/MANIFEST.MF";

  public static void enhance(String annotationName, Path path, Path toPath, int version, ValueTypeChecker.IssueReporter issueReporter) throws IOException {
    Objects.requireNonNull(annotationName);
    Objects.requireNonNull(path);
    String annotationDescriptor = Type.getObjectType(annotationName.replace('.', '/')).getDescriptor();
    class DelegatingIssueChecker implements ValueTypeChecker.IssueReporter {
      boolean hasIssue;

      @Override
      public void report(ValueTypeChecker.Issue issue, String className, String message) {
        issueReporter.report(issue, className, message);
        hasIssue = true;
      }
    }
    DelegatingIssueChecker delegatingIssueChecker = new DelegatingIssueChecker();
    LinkedHashMap<String, byte[]> valueTypeMap = new LinkedHashMap<>();
    try(OutputStream output = Files.newOutputStream(toPath);
        JarOutputStream jarOutput = new JarOutputStream(output)) {
      Manifest manifest;
      try(JarFile jarFile = new JarFile(path.toFile())) {
        manifest = jarFile.getManifest();
        for (JarEntry entry : Collections.list(jarFile.entries())) {
          String entryName = entry.getName();
          if (entryName.equals(MANIFEST_NAME)) {
            continue;  // skip
          }

          // // copy the entry
          jarOutput.putNextEntry(entry);
          try(InputStream input = jarFile.getInputStream(entry)) {
            copy(input, jarOutput);
          }
          jarOutput.closeEntry();

          if (!entryName.endsWith(".class")) {
            continue;  // skip
          }
          ClassReader reader;
          try (InputStream input = jarFile.getInputStream(entry)) {
            reader = new ClassReader(input);
          }

          IsClassAnnotated isClassAnnotated = new IsClassAnnotated(annotationDescriptor);
          reader.accept(isClassAnnotated, ClassReader.SKIP_CODE);
          if (isClassAnnotated.isAnnotated()) {
            ClassWriter writer = new ClassWriter(reader, 0);
            ValueTypeRewriter rewriter = new ValueTypeRewriter(writer, version);
            ValueTypeChecker valueTypeChecker = new ValueTypeChecker(delegatingIssueChecker, rewriter);
            reader.accept(valueTypeChecker, 0);
            if (!delegatingIssueChecker.hasIssue) {
              valueTypeMap.put(entryName, writer.toByteArray());
            }
          }
        }
      }

      if (delegatingIssueChecker.hasIssue) {
        return;
      }

      // add manifest
      if (manifest == null) {
        manifest = new Manifest();
      }
      Attributes multiReleaseAttributes = manifest.getAttributes("Multi-Release");
      if (multiReleaseAttributes == null) {
        multiReleaseAttributes = new Attributes();
        manifest.getEntries().put("Multi-Release", multiReleaseAttributes);
      }
      multiReleaseAttributes.putValue("Multi-Release", "true");

      jarOutput.putNextEntry(new JarEntry(MANIFEST_NAME));
      OutputStreamWriter manifestWriter = new OutputStreamWriter(jarOutput, StandardCharsets.UTF_8);
      Attributes mainAttributes = manifest.getMainAttributes();
      if (mainAttributes != null) {
        manifestWriter.write("Main: " + mainAttributes.getValue(Attributes.Name.MAIN_CLASS) + "\n");
      }
      for(Attributes attrs : manifest.getEntries().values()) {
        for (Map.Entry<Object, Object> entry : attrs.entrySet()) {
          Object key = entry.getKey();
          Object value = entry.getValue();
          manifestWriter.write(key + ": " + value + "\n");
        }
      }
      manifestWriter.flush();
      jarOutput.closeEntry();

      // add versioned entry
      for(Entry<String, byte[]> mapEntry : valueTypeMap.entrySet()) {
        String entryName = mapEntry.getKey();
        byte[] code = mapEntry.getValue();
        String versionedName = "META-INF/versions/" + version + "/" + entryName;
        jarOutput.putNextEntry(new JarEntry(versionedName));
        jarOutput.write(code);
        jarOutput.closeEntry();
      }

    } finally {
      if (delegatingIssueChecker.hasIssue) {
        Files.delete(toPath);
      }
    }
  }
}