package org.elasticsearch;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;

import static org.objectweb.asm.Opcodes.*;

class InstrumentMethodClassVisitor extends ClassVisitor {

    private final String methodName;
    //private final TraceClassVisitor tracer;

    public InstrumentMethodClassVisitor(ClassVisitor cv, String methodName) {
        super(ASM7, cv);
        this.methodName = methodName;
        //this.tracer = new TraceClassVisitor(cv, new PrintWriter(System.out));
    }

//    @Override
//    public void visit(int version, int access, String name,
//                      String signature, String superName, String[] interfaces) {
//        System.out.println("[Agent] Calling visit");
//        cv.visit(version, access, name, signature, superName, interfaces);
//    }

    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String desc,
                                     String signature,
                                     String[] exceptions) {

        //System.out.println("[Agent] visiting method " + name);
        if (name.equals(methodName)) {
            var methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);

            //System.out.println("[Agent] method " + name + " instrumenting: " + (methodVisitor == null ? "no" : "yes"));
            return new InstrumentingMethodVisitor(
                    //new TraceMethodVisitor(methodVisitor, InstrumentMethodClassVisitor.this.tracer.p)
                    methodVisitor
            );

        }
        return cv.visitMethod(access, name, desc, signature, exceptions);
    }

//    @Override
//    public void visitEnd() {
//        super.visitEnd();
//        System.out.println(tracer.p.getText());
//    }

    static class InstrumentingMethodVisitor extends MethodVisitor {
        public InstrumentingMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM7, mv);
            //System.out.println("Instrumenting");
        }

        @Override
        public void visitCode() {
            //System.out.println("InstrumentingMethodVisitor#visitCode");
            prologue(this);
            mv.visitCode();
        }


        static void prologue(MethodVisitor mv) {
            Type checkerClassType = Type.getType(EntitlementChecker.class);
            String handleClass = checkerClassType.getInternalName() + "Handle";
            String getCheckerClassMethodDescriptor = Type.getMethodDescriptor(checkerClassType);

            // pushEntitlementChecker
            mv.visitMethodInsn(INVOKESTATIC, handleClass, "instance", getCheckerClassMethodDescriptor, false);
            // pushCallerClass
            mv.visitMethodInsn(
                    INVOKESTATIC,
                    Type.getInternalName(Util.class),
                    "getCallerClass",
                    Type.getMethodDescriptor(Type.getType(Class.class)),
                    false
            );
            // invokeInstrumentationMethod
            mv.visitMethodInsn(
                    INVOKEINTERFACE,
                    checkerClassType.getInternalName(),
                    "check",
                    Type.getMethodDescriptor(
                            Type.VOID_TYPE,
                            Type.getType(Class.class)
                    ),
                    true
            );
        }

//        @Override
//        public void visitEnd() {
//            System.out.println("InstrumentingMethodVisitor#visitEnd");
//            mv.visitEnd();
//        }
    }
}
