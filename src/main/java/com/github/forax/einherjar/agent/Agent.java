package com.github.forax.einherjar.agent;

import com.github.forax.einherjar.agent.runtime.AgentRuntime;
import com.github.forax.einherjar.api.ValueType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class Agent {
  static final String AGENT_RUNTIME_NAME = AgentRuntime.class.getName().replace('.', '/');

  private static void copy(InputStream input, OutputStream output) throws IOException {
    byte[] buffer = new byte[8_192];
    int read;
    while ((read = input.read(buffer)) != -1) {
      output.write(buffer, 0, read);
    }
  }

  private static void addEntryToBootstrapJarFile(JarOutputStream jarOutputStream, String entryClassName) throws IOException {
    try(InputStream input = AgentRuntime.class.getResourceAsStream("/" + entryClassName + ".class")) {
      if (input == null) {
        throw new AssertionError("can not find " + entryClassName + " bytecode");
      }
      jarOutputStream.putNextEntry(new JarEntry(entryClassName + ".class"));
      copy(input, jarOutputStream);
      jarOutputStream.closeEntry();
    }
  }

  private static Path createBootstrapJarFile() throws IOException {
    Path bootstrapJarFile = Files.createTempFile("--agent-runtime-jar--", "");
    try(OutputStream output = Files.newOutputStream(bootstrapJarFile);
        JarOutputStream jarOutputStream = new JarOutputStream(output)) {
      addEntryToBootstrapJarFile(jarOutputStream, AGENT_RUNTIME_NAME);
      addEntryToBootstrapJarFile(jarOutputStream, AgentRuntime.Cache.class.getName().replace('.', '/'));
      addEntryToBootstrapJarFile(jarOutputStream, ValueType.class.getName().replace('.', '/'));
    }
    return bootstrapJarFile;
  }

  public static void premain(String agentArgs, Instrumentation instrumentation) throws IOException {
    Path bootstrapJarFile = createBootstrapJarFile();
    instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(bootstrapJarFile.toFile()));

    instrumentation.addTransformer(new ClassFileTransformer() {
      public byte[] transform(ClassLoader loader,
                  String className,
                  Class<?> classBeingRedefined,
                  ProtectionDomain protectionDomain,
                  byte[] classfileBuffer)
          throws IllegalClassFormatException {

        try {
          if (className.equals(AGENT_RUNTIME_NAME)) {
            //System.err.println("bailout " + className);
            return null;
          }

          ClassReader reader = new ClassReader(classfileBuffer);
          ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
          ValueTypeInstrRewriter valueTypeInstrRewriter = new ValueTypeInstrRewriter(writer);
          reader.accept(valueTypeInstrRewriter, 0);

          if (valueTypeInstrRewriter.isTransformed()) {
            //System.err.println("transform " + className);

            //if (className.equals("org/opentest4j/AssertionFailedError")) {  // DEBUG
            //  CheckClassAdapter.verify(new ClassReader(writer.toByteArray()), true, new PrintWriter(System.err));
            //}

            return writer.toByteArray();
          }
          return null;
        } catch (Throwable t) {
          t.printStackTrace(System.err);
          throw t;
        }
      }
    }, false);
  }
}
