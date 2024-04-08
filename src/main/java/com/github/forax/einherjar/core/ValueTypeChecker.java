package com.github.forax.einherjar.core;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Checks that
 * <ul>
 *   <li>the super class is either j.l.Object, j.l.Number or j.l.Record
 *   <li>all fields are final
 *   <li>"this" does not escape the constructor
 * </ul>
 */
public final class ValueTypeChecker extends ClassVisitor {
  private static final BasicValue THIS = new BasicValue(Type.getObjectType("java/lang/Object"));

  public enum Issue {
    UNKNOWN_SUPER,
    THIS_ESCAPE,
    NON_FINAL_FIELD
  }
  @FunctionalInterface
  public interface IssueReporter {
    void report(Issue issue, String className, String message);
  }

  private int scanBackwardToFindLineNumber(AbstractInsnNode node) {
    for(AbstractInsnNode insn = node; insn != null; insn = insn.getPrevious()) {
      if (insn instanceof LineNumberNode) {
        LineNumberNode lineNumberNode = (LineNumberNode) insn;
        return lineNumberNode.line;
      }
    }
    return -1;
  }

  private final IssueReporter issueReporter;
  private String ownerClassName;
  private String superClassName;
  private String sourceName;

  public ValueTypeChecker(IssueReporter issueReporter, ClassVisitor classVisitor) {
    super(ASM9, classVisitor);
    this.issueReporter = issueReporter;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);
    ownerClassName = name;
    superClassName = superName;
    switch (superName) {
      case "java/lang/Object":
      case "java/lang/Number":
      case "java/lang/Record":
        break;
      default:
        issueReporter.report(Issue.UNKNOWN_SUPER, name , "super class " + superName + " is unknown");
    }
  }

  @Override
  public void visitSource(String source, String debug) {
    super.visitSource(source, debug);
    sourceName = source;
  }

  @Override
  public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
    // all fields should be final
    if ((access & Opcodes.ACC_FINAL) == 0) {
      issueReporter.report(Issue.NON_FINAL_FIELD, ownerClassName , "field " + name + descriptor + " is not final");
    }
    return super.visitField(access, name, descriptor, signature, value);
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
    if (!name.equals("<init>")) {
      return mv;
    }

    return new MethodNode(ASM9, access, name, descriptor, signature, exceptions) {
      @Override
      public void visitEnd() {
        super.visitEnd();

        BasicInterpreter interpreter = new BasicInterpreter(ASM9) {
          private boolean zeroContainsThis;

          @Override
          public BasicValue copyOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
            // ALOAD_0 contains "this" once the super constructor is called
            // and, we check that the slot 0 is not rewritten
            BasicValue result = super.copyOperation(insn, value);
            if (zeroContainsThis && insn instanceof VarInsnNode) {
              VarInsnNode varInsnNode = (VarInsnNode) insn;
              int opcode = varInsnNode.getOpcode();
              if (opcode == ALOAD && varInsnNode.var == 0) {
                return THIS;
              }
              if (opcode >= ISTORE && opcode <= ASTORE && varInsnNode.var == 0) {
                zeroContainsThis = false;
              }
            }
            return result;
          }

          @Override
          public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
            BasicValue result = super.naryOperation(insn, values);
            int opcode = insn.getOpcode();
            if (opcode >= INVOKEVIRTUAL && opcode <= INVOKEDYNAMIC) {
              MethodInsnNode methodInsnNode;
              if (opcode == INVOKESPECIAL && "<init>".equals((methodInsnNode = ((MethodInsnNode) insn)).name) && superClassName.equals(methodInsnNode.owner)) {
                // call to the super constructor
                zeroContainsThis = true;
                return result;
              }

              if (values.stream().anyMatch(v -> v == THIS)) {
                int lineNumber = scanBackwardToFindLineNumber(insn);
                String location = (sourceName == null ? ownerClassName: sourceName) + ":" + lineNumber;
                issueReporter.report(Issue.THIS_ESCAPE, ownerClassName , "constructor " + descriptor + " leaks this at " + location);
              }
            }
            return result;
          }
        };
        Analyzer<BasicValue> analyzer = new Analyzer<>(interpreter);
        try {
          analyzer.analyze(ownerClassName, this);
        } catch (AnalyzerException e) {
          throw new IllegalStateException(e);
        }
      }
    };
  }
}
