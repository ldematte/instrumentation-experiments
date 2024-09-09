package org.elasticsearch;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

class CalledMethodsClassVisitor extends ClassVisitor {

    record Caller(String className, String methodName, String methodDescriptor) {}

    interface MethodConsumer {
        void accept(String className, String methodName, String methodDescriptor);
    }

    private final Caller methodToFind;
    private final MethodConsumer calledConsumer;
    private final MethodConsumer nativeDefinitionConsumer;
    private String className;

    protected CalledMethodsClassVisitor(
            Caller methodToFind,
            MethodConsumer calledConsumer,
            MethodConsumer nativeDefinitionConsumer
    ) {
        super(ASM9);
        this.methodToFind = methodToFind;
        this.calledConsumer = calledConsumer;
        this.nativeDefinitionConsumer = nativeDefinitionConsumer;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.className = name;
    }

    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions
    ) {
        if (className.equals(methodToFind.className) &&
            name.equals(methodToFind.methodName) &&
            descriptor.equals(methodToFind.methodDescriptor)) {

            boolean isNative = (access & ACC_NATIVE) != 0;

            if (isNative) {
                nativeDefinitionConsumer.accept(className, name, descriptor);
            } else {
                return new CalledMethodsMethodVisitor(
                        super.visitMethod(access, name, descriptor, signature, exceptions)
                );
            }
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    private class CalledMethodsMethodVisitor extends MethodVisitor {
        protected CalledMethodsMethodVisitor(MethodVisitor mv) {
            super(ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            calledConsumer.accept(owner, name, descriptor);
        }
    }
}
