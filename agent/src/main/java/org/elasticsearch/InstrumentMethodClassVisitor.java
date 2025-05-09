package org.elasticsearch;

import org.objectweb.asm.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.elasticsearch.EntitlementCheckTransformer.getInternalClassName;
import static org.objectweb.asm.Opcodes.*;

class InstrumentMethodClassVisitor extends ClassVisitor {

    private final Set<String> methodNames;
    private final String owner;
    //private final TraceClassVisitor tracer;

    public InstrumentMethodClassVisitor(ClassVisitor cv, String className, Set<String> methodNames) {
        super(ASM9, cv);
        this.methodNames = methodNames;
        this.owner = getInternalClassName(className);
        //this.tracer = new TraceClassVisitor(cv, new PrintWriter(System.out));
    }

//    @Override
//    public void visit(int version, int access, String name,
//                      String signature, String superName, String[] interfaces) {
//        System.out.println("[Agent] Calling visit");
//        cv.visit(version, access, name, signature, superName, interfaces);
//    }

    private List<Supplier<MethodVisitor>> methodsToAdd = new ArrayList<>();

    /**
     * Instrumenting with ScopedValue proved to be challenging; we need to rename the original method (and make it
     * private), plus we need to emit a "new" method, with the original signature, containing the checks and calling the
     * original (now renamed) method.
     * This _does not work_ with retransform (hence why I'm testing it with Shutdown#exit) - we will need static
     * instrumentation for that.
     * The result looks like this (taken from decompiled instrumented java/lang/Shutdown.class):
     *
     * private static void original_exit(int status) {
     *     logRuntimeExit(status);
     *     synchronized(Shutdown.class) {
     *         beforeHalt();
     *         runHooks();
     *         halt(status);
     *     }
     * }
     *
     * static void exit(int var0) {
     *     if (!EntitlementChecker.isCurrentCallAlreadyChecked()) {
     *         EntitlementCheckerHandle.instance().check(Util.getCallerClass());
     *         ScopedValue.runWhere(EntitlementChecker.ALREADY_CHECKED, Boolean.TRUE, new OriginalMethodRunnable("original_exit", var0));
     *     } else {
     *         original_exit(var0);
     *     }
     * }
     */
    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String desc,
                                     String signature,
                                     String[] exceptions) {

        // System.out.println("[Agent] visiting method " + name);
        if (methodNames.contains(name)) {
            var newAccess = (access &~ACC_PUBLIC) | ACC_PRIVATE;
            var methodVisitor = cv.visitMethod(newAccess, "original_" + name, desc, signature, exceptions);
            //System.out.println("[Agent] method " + name + " instrumenting: " + (methodVisitor == null ? "no" : "yes"));

            ;
            methodsToAdd.add(() -> new InstrumentingMethodVisitor(
                    //new TraceMethodVisitor(methodVisitor, InstrumentMethodClassVisitor.this.tracer.p)
                    cv.visitMethod(access, name, desc, signature, exceptions), owner, name, desc
            ));
            return methodVisitor;
        }
        //System.out.println("[Agent] method " + name + " in interfaces?");
        if (CheckerFactory.methodsToInterfaces.containsKey(name)) {
            var methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
            //System.out.println("[Agent] inherited method " + name + " instrumenting: " + (methodVisitor == null ? "no" : "yes"));
            return new InstrumentingInheritanceMethodVisitor(
                    //new TraceMethodVisitor(methodVisitor, InstrumentMethodClassVisitor.this.tracer.p)
                    methodVisitor, name
            );
        }
        return cv.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        for (var mvs : methodsToAdd) {
            var mv = mvs.get();
            mv.visitCode();
            mv.visitEnd();
        }
        super.visitEnd();
    }

    static class InstrumentingMethodVisitor extends MethodVisitor {

        private final String owner;
        private final String originalMethodName;
        private final String originalMethodDescriptor;

        public InstrumentingMethodVisitor(MethodVisitor mv, String owner, String originalMethodName, String originalMethodDescriptor) {
            super(Opcodes.ASM9, mv);
            this.owner = owner;
            this.originalMethodName = originalMethodName;
            this.originalMethodDescriptor = originalMethodDescriptor;
            System.out.println("Instrumenting " + owner + "#" + originalMethodName);
        }

        @Override
        public void visitCode() {
            deepCheckPrologue(this);
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

        /**
         * This will produce the following bytecode; it's largely equivalent to what you see happening in the
         * {@link Example} class, but we take advantage of the fact we can generate MethodHandles directly with
         * ASM.
         *
         *     INVOKESTATIC org/elasticsearch/EntitlementChecker.isCurrentCallAlreadyChecked ()Z (itf)
         *     IFNE L0
         *     INVOKESTATIC org/elasticsearch/EntitlementCheckerHandle.instance ()Lorg/elasticsearch/EntitlementChecker;
         *     INVOKESTATIC org/elasticsearch/Util.getCallerClass ()Ljava/lang/Class;
         *     INVOKEINTERFACE org/elasticsearch/EntitlementChecker.check (Ljava/lang/Class;)V (itf)
         *     GETSTATIC org/elasticsearch/EntitlementChecker.ALREADY_CHECKED : Ljava/lang/ScopedValue;
         *     GETSTATIC java/lang/Boolean.TRUE : Ljava/lang/Boolean;
         *     NEW org/elasticsearch/OriginalMethodRunnable
         *     DUP
         *     LDC java/lang/Shutdown.original_exit(I)V (6)
         *     ILOAD 0
         *     INVOKESPECIAL org/elasticsearch/OriginalMethodRunnable.<init> (Ljava/lang/invoke/MethodHandle;I)V
         *     INVOKESTATIC java/lang/ScopedValue.runWhere (Ljava/lang/ScopedValue;Ljava/lang/Object;Ljava/lang/Runnable;)V
         *     GOTO L1
         *    L0
         *     ILOAD 0
         *     INVOKESTATIC java/lang/Shutdown.original_exit (I)V
         *    L1
         *     RETURN
         *     MAXSTACK = 0
         *     MAXLOCALS = 1
         *
         */
        void deepCheckPrologue(MethodVisitor mv) {
            Type checkerClassType = Type.getType(EntitlementChecker.class);
            String handleClass = checkerClassType.getInternalName() + "Handle";
            String getCheckerClassMethodDescriptor = Type.getMethodDescriptor(checkerClassType);

            var l1 = new Label();
            var end = new Label();

            // if (ALREADY_CHECKED) return;
            mv.visitMethodInsn(INVOKESTATIC, checkerClassType.getInternalName(), "isCurrentCallAlreadyChecked", "()Z", true);
            mv.visitJumpInsn(IFNE, l1);

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

            // push runWhere args
            mv.visitFieldInsn(GETSTATIC, checkerClassType.getInternalName(), "ALREADY_CHECKED", Type.getDescriptor(ScopedValue.class));
            mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TRUE", Type.getDescriptor(Boolean.class));

            // Create the runnable:
            // create the object
            mv.visitTypeInsn(NEW, "org/elasticsearch/OriginalMethodRunnable");
            mv.visitInsn(DUP);
            // the MH to call
            Handle originalMethodHandle = new Handle(
                    H_INVOKESTATIC, // TODO: pass down the right one
                    owner,
                    "original_" + originalMethodName,
                    originalMethodDescriptor,
                    false // TODO: pass down the right one
            );
            mv.visitLdcInsn(originalMethodHandle);
            // forward args to ctor
            int localVarIndex = 0;
            for (Type type : Type.getArgumentTypes(originalMethodDescriptor)) {
                mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), localVarIndex);
                localVarIndex += type.getSize();
            }
            // call ctor
            mv.visitMethodInsn(INVOKESPECIAL, "org/elasticsearch/OriginalMethodRunnable", "<init>", "(Ljava/lang/invoke/MethodHandle;I)V");


            mv.visitMethodInsn(INVOKESTATIC, "java/lang/ScopedValue", "runWhere", "(Ljava/lang/ScopedValue;Ljava/lang/Object;Ljava/lang/Runnable;)V", false);
            mv.visitJumpInsn(GOTO, end);
            mv.visitLabel(l1);

            localVarIndex = 0;
            for (Type type : Type.getArgumentTypes(originalMethodDescriptor)) {
                mv.visitVarInsn(type.getOpcode(Opcodes.ILOAD), localVarIndex);
                localVarIndex += type.getSize();
            }
            mv.visitMethodInsn(INVOKESTATIC, owner, "original_" + originalMethodName, originalMethodDescriptor);

            mv.visitLabel(end);
            mv.visitInsn(RETURN);
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
