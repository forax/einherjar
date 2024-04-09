package com.github.forax.einherjar.core;

import org.objectweb.asm.ClassVisitor;

import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.V23;

public class ValueTypeRewriter extends ClassVisitor {
  private static final int ACC_IDENTITY = ACC_SUPER;

  private final int version;

  public ValueTypeRewriter(ClassVisitor classVisitor, int version) {
    super(ASM9, classVisitor);
    this.version = version;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    int newAccess = access & ~ACC_IDENTITY;
    super.visit(/*(version - 23 + V23)*/ /*| (65_535 << 16)*/ V23, newAccess, name, signature, superName, interfaces);
  }
}
