package com.github.forax.einherjar.core;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;

import static org.objectweb.asm.Opcodes.ASM9;

public class IsClassAnnotated extends ClassVisitor {
  private final String annotationDescriptor;
  private boolean annotated;

  public IsClassAnnotated(String annotationDescriptor) {
    super(ASM9, null);
    this.annotationDescriptor = annotationDescriptor;
  }

  public boolean isAnnotated() {
    return annotated;
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    if (annotationDescriptor.equals(descriptor)) {
      annotated = true;
    }
    return super.visitAnnotation(descriptor, visible);
  }
}
