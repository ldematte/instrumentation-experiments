package org.elasticsearch;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

class FindUsagesClassVisitor extends ClassVisitor {

    private int classAccess;

    record Callee(String className, String methodName, String methodDescriptor) {}

    record Caller(
            String moduleName,
            String source,
            int line,
            String className,
            String methodName,
            String methodDescriptor,
            boolean isPublic
    ) {}

    interface CallerConsumer {
        void accept(String source, int line, String className, String methodName, String methodDescriptor,
                    boolean isPublic);
    }

    private final Callee methodToFind;
    private final CallerConsumer callers;
    private String className;
    private String source;

    protected FindUsagesClassVisitor(Callee methodToFind, CallerConsumer callers) {
        super(ASM9);
        this.methodToFind = methodToFind;
        this.callers = callers;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.className = name;
        this.classAccess = access;
    }

    @Override
    public void visitSource(String source, String debug) {
        super.visitSource(source, debug);
        this.source = source;
    }

    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions
    ) {
        return new FindUsagesMethodVisitor(
                super.visitMethod(access, name, descriptor, signature, exceptions),
                name,
                descriptor,
                access
        );
    }

    private class FindUsagesMethodVisitor extends MethodVisitor {

        private final String methodName;
        private int line;
        private final String methodDescriptor;
        private final int methodAccess;

        protected FindUsagesMethodVisitor(
                MethodVisitor mv,
                String methodName,
                String methodDescriptor,
                int methodAccess
        ) {
            super(ASM9, mv);
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
            this.methodAccess = methodAccess;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

            if (methodToFind.className.equals(owner)) {
                if (methodToFind.methodName.equals(name)) {
                    if (methodToFind.methodDescriptor == null || methodToFind.methodDescriptor.equals(descriptor)) {
                        boolean isPublic =
                                (methodAccess & ACC_PUBLIC) != 0 &&
                                (classAccess & ACC_PUBLIC) != 0 &&
                                (classAccess & ACC_ABSTRACT) == 0;
                        callers.accept(source, line, className, methodName, methodDescriptor, isPublic);
                    }
                }
            }
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            super.visitLineNumber(line, start);
            this.line = line;
        }
    }
}
