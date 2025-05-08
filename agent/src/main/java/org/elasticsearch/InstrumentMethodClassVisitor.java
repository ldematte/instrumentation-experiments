package org.elasticsearch;

import org.objectweb.asm.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;

import static org.objectweb.asm.Opcodes.*;

class InstrumentMethodClassVisitor extends ClassVisitor {

    private final Set<String> methodNames;
    //private final TraceClassVisitor tracer;

    public InstrumentMethodClassVisitor(ClassVisitor cv, Set<String> methodNames) {
        super(ASM9, cv);
        this.methodNames = methodNames;
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

        // System.out.println("[Agent] visiting method " + name);
        var methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
        if (methodNames.contains(name)) {
            //System.out.println("[Agent] method " + name + " instrumenting: " + (methodVisitor == null ? "no" : "yes"));
            return new InstrumentingMethodVisitor(
                    //new TraceMethodVisitor(methodVisitor, InstrumentMethodClassVisitor.this.tracer.p)
                    methodVisitor, name
            );
        }
        //System.out.println("[Agent] method " + name + " in interfaces?");
        if (CheckerFactory.methodsToInterfaces.containsKey(name)) {
            //System.out.println("[Agent] inherited method " + name + " instrumenting: " + (methodVisitor == null ? "no" : "yes"));
            return new InstrumentingInheritanceMethodVisitor(
                    //new TraceMethodVisitor(methodVisitor, InstrumentMethodClassVisitor.this.tracer.p)
                    methodVisitor, name
            );
        }
        return methodVisitor;
    }

//    @Override
//    public void visitEnd() {
//        super.visitEnd();
//        System.out.println(tracer.p.getText());
//    }

    static class InstrumentingMethodVisitor extends MethodVisitor {
        public InstrumentingMethodVisitor(MethodVisitor mv, String name) {
            super(Opcodes.ASM9, mv);
            System.out.println("Instrumenting " + name);
        }

        @Override
        public void visitCode() {
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

    static class InstrumentingInheritanceMethodVisitor extends MethodVisitor {
        private final String methodName;

        public InstrumentingInheritanceMethodVisitor(MethodVisitor mv, String methodName) {
            super(Opcodes.ASM9, mv);
            this.methodName = methodName;
            System.out.println("Instrumenting for inheritance " + methodName);
        }

        @Override
        public void visitCode() {
            prologue(this, methodName);
            mv.visitCode();
        }

        static void prologue(MethodVisitor mv, String methodName) {
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

            MethodType mt = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class,
                    MethodType.class, String.class, MethodHandle.class);

            Handle bootstrap = new Handle(H_INVOKESTATIC, Type.getInternalName(CheckerFactory.class), "bootstrap",
                    mt.toMethodDescriptorString(), false);

            // This will be "dynamic", depending on this method signature: the check method is Class + "that" +
            // original params
            var checkMethodDescriptor = Type.getMethodDescriptor(
                    Type.VOID_TYPE,
                    Type.getType(Class.class)
            );
            var dynamicCheckMethodDescriptor = Type.getMethodDescriptor(
                    Type.VOID_TYPE,
                    checkerClassType,
                    Type.getType(Class.class)
            );

            var checkMethodHandle = new Handle(
                    H_INVOKEINTERFACE,
                    checkerClassType.getInternalName(),
                    "check",
                    checkMethodDescriptor,
                    true);

            mv.visitInvokeDynamicInsn(
                    "runCheck",
                    dynamicCheckMethodDescriptor,
                    bootstrap,
                    methodName,
                    checkMethodHandle
            );
        }
    }
}
