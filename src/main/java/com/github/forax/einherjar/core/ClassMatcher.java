package com.github.forax.einherjar.core;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;

import java.util.function.Predicate;

import static org.objectweb.asm.Opcodes.ASM9;

public class ClassMatcher extends ClassVisitor {
  private final Predicate<? super String> classNameMatcher;
  private final Predicate<? super String> annotationDescriptorMatcher;
  private boolean match;

  public ClassMatcher(Predicate<? super String> classNameMatcher, Predicate<? super String> annotationDescriptorMatcher) {
    super(ASM9, null);
    this.classNameMatcher = classNameMatcher;
    this.annotationDescriptorMatcher = annotationDescriptorMatcher;
  }

  public boolean isMatching() {
    return match;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    if (classNameMatcher.test(name)) {
      match = true;
    }
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    if (annotationDescriptorMatcher.test(descriptor)) {
      match = true;
    }
    return null;
  }
}
