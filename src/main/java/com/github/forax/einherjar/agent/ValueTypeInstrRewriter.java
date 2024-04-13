package com.github.forax.einherjar.agent;

import com.github.forax.einherjar.agent.runtime.AgentRuntime;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static com.github.forax.einherjar.agent.Agent.AGENT_RUNTIME_NAME;
import static java.lang.invoke.MethodType.methodType;
import static org.objectweb.asm.Opcodes.ASM9;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.DUP2;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.objectweb.asm.Opcodes.IF_ACMPNE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.MONITORENTER;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.V1_6;
import static org.objectweb.asm.Opcodes.V1_7;

class ValueTypeInstrRewriter extends ClassVisitor {
  private static final Handle BSM;
  static {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodHandle mh;
    try {
      mh = lookup.findStatic(AgentRuntime.class, "bsm",
          methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
    MethodHandleInfo mhInfo = lookup.revealDirect(mh);
    BSM = new Handle(mhInfo.getReferenceKind(),
        mhInfo.getDeclaringClass().getName().replace('.', '/'),
        mhInfo.getName(),
        mhInfo.getMethodType().toMethodDescriptorString(),
        false);
  }

  private boolean doNotUseInvokedynamic;
  private boolean transformed;

  public ValueTypeInstrRewriter(ClassVisitor classVisitor) {
    super(ASM9, classVisitor);
  }

  public boolean isTransformed() {
    return transformed;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    super.visit(version, access, name, signature, superName, interfaces);
    this.doNotUseInvokedynamic = /*version < V1_7 */ true;
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
    return new MethodVisitor(ASM9, mv) {
      @Override
      public void visitInsn(int opcode) {
        if (opcode == MONITORENTER) {
          mv.visitInsn(DUP);
          if (doNotUseInvokedynamic) {
            mv.visitMethodInsn(INVOKESTATIC, AGENT_RUNTIME_NAME, "monitorenter", "(Ljava/lang/Object;)V", false);
          } else {
            mv.visitInvokeDynamicInsn("monitorenter", "(Ljava/lang/Object;)V", BSM);
          }
          transformed = true;
        }
        super.visitInsn(opcode);
      }

      @Override
      public void visitJumpInsn(int opcode, Label label) {
        if (opcode == IF_ACMPEQ || opcode == IF_ACMPNE) {
          if (doNotUseInvokedynamic) {
            mv.visitMethodInsn(INVOKESTATIC, AGENT_RUNTIME_NAME, "acmp", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false);
          } else {
            mv.visitInvokeDynamicInsn( "acmp", "(Ljava/lang/Object;Ljava/lang/Object;)Z", BSM);
          }
          mv.visitJumpInsn(opcode == IF_ACMPEQ ? IFNE : IFEQ, label);
          transformed = true;
          return;
        }
        super.visitJumpInsn(opcode, label);
      }

      @Override
      public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (opcode == INVOKESTATIC && owner.equals("java/lang/System") && name.equals("identityHashCode") && descriptor.equals("(Ljava/lang/Object;)I")) {
          if (doNotUseInvokedynamic) {
            mv.visitMethodInsn(INVOKESTATIC, AGENT_RUNTIME_NAME, "identityHashCode", "(Ljava/lang/Object;)I", false);
          } else {
            mv.visitInvokeDynamicInsn( "identityHashCode", "(Ljava/lang/Object;)I", BSM);
          }
          transformed = true;
          return;
        }
        if (opcode == INVOKESPECIAL && name.equals("<init>")) {
          switch (owner) {
            case "java/lang/ref/PhantomReference":
            case "java/lang/ref/SoftReference":
            case "java/lang/ref/WeakReference":
              switch (descriptor) {
                case "(Ljava/lang/Object;Ljava/lang/ref/ReferenceQueue;)V":
                  mv.visitInsn(DUP2);
                  mv.visitInsn(POP);
                break;
                case "(Ljava/lang/Object;)V":
                  mv.visitInsn(DUP);
                  break;
                default:
                  throw new AssertionError("invalid descriptor " + descriptor);
              }
              if (doNotUseInvokedynamic) {
                mv.visitMethodInsn(INVOKESTATIC, AGENT_RUNTIME_NAME, "identityCheck", "(Ljava/lang/Object;)V", false);
              } else {
                mv.visitInvokeDynamicInsn( "identityCheck", "(Ljava/lang/Object;)V", BSM);
              }
              transformed = true;
              break;
            default:
              break;
          }
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    };
  }
}
