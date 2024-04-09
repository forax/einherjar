package com.github.forax.einherjar.core;

import org.objectweb.asm.ClassVisitor;

import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.V23;

public class ValueTypeRewriter extends ClassVisitor {
  private static final int ACC_IDENTITY = ACC_SUPER;

  private final int enhancedVersion;

  public ValueTypeRewriter(ClassVisitor classVisitor, int enhancedVersion) {
    super(ASM9, classVisitor);
    this.enhancedVersion = enhancedVersion;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    int newAccess = access & ~ACC_IDENTITY;
    super.visit((V23 + enhancedVersion - 23) | 0xFFFF0000, newAccess, name, signature, superName, interfaces);
  }
}
