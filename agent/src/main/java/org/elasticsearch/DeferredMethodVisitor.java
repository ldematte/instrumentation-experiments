package org.elasticsearch;

import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class DeferredMethodVisitor extends MethodVisitor {

    private final List<Consumer<MethodVisitor>> actions = new ArrayList<>();

    DeferredMethodVisitor() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visitParameter(String name, int access) {
        actions.add(m -> m.visitParameter(name, access));
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        var annotationVisitor = new DeferredAnnotationVisitor(actions);
        actions.add(m -> annotationVisitor.init(m.visitAnnotationDefault()));
        return annotationVisitor;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        var annotationVisitor = new DeferredAnnotationVisitor(actions);
        actions.add(m -> annotationVisitor.init(m.visitAnnotation(descriptor, visible)));
        return annotationVisitor;
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        var annotationVisitor = new DeferredAnnotationVisitor(actions);
        actions.add(m -> annotationVisitor.init(m.visitTypeAnnotation(typeRef, typePath, descriptor, visible)));
        return annotationVisitor;
    }

    @Override
    public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
        actions.add(m -> m.visitAnnotableParameterCount(parameterCount, visible));
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
        var annotationVisitor = new DeferredAnnotationVisitor(actions);
        actions.add(m -> annotationVisitor.init(m.visitParameterAnnotation(parameter, descriptor, visible)));
        return annotationVisitor;
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        actions.add(m -> m.visitAttribute(attribute));
    }

    @Override
    public void visitCode() {
        actions.add(MethodVisitor::visitCode);
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        actions.add(m -> m.visitFrame(type, numLocal, local, numStack, stack));
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        actions.add(m -> m.visitMethodInsn(opcode, owner, name, descriptor, isInterface));
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        actions.add(m -> m.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments));
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        actions.add(m -> m.visitJumpInsn(opcode, label));
    }

    @Override
    public void visitLabel(Label label) {
        actions.add(m -> m.visitLabel(label));
    }

    @Override
    public void visitLdcInsn(Object value) {
        actions.add(m -> m.visitLdcInsn(value));
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
        actions.add(m -> m.visitIincInsn(varIndex, increment));
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        actions.add(m -> m.visitTableSwitchInsn(min, max, dflt, labels));
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        actions.add(m -> m.visitLookupSwitchInsn(dflt, keys, labels));
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        actions.add(m -> m.visitMultiANewArrayInsn(descriptor, numDimensions));
    }

    @Override
    public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        var annotationVisitor = new DeferredAnnotationVisitor(actions);
        actions.add(m -> annotationVisitor.init(m.visitInsnAnnotation(typeRef, typePath, descriptor, visible)));
        return annotationVisitor;
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        actions.add(m -> m.visitTryCatchBlock(start, end, handler, type));
    }

    @Override
    public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        var annotationVisitor = new DeferredAnnotationVisitor(actions);
        actions.add(m -> annotationVisitor.init(m.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible)));
        return annotationVisitor;
    }

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        actions.add(m -> m.visitLocalVariable(name, descriptor, signature, start, end, index));
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
        var annotationVisitor = new DeferredAnnotationVisitor(actions);
        actions.add(m -> annotationVisitor.init(m.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible)));
        return annotationVisitor;
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        actions.add(m -> m.visitLineNumber(line, start));
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        actions.add(m -> m.visitMaxs(maxStack, maxLocals));
    }

    @Override
    public void visitInsn(int opcode) {
        actions.add(m -> m.visitInsn(opcode));
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        actions.add(m -> m.visitIntInsn(opcode, operand));
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
        actions.add(m -> m.visitVarInsn(opcode, varIndex));
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        actions.add(m -> m.visitTypeInsn(opcode, type));
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        actions.add(m -> m.visitFieldInsn(opcode, owner, name, descriptor));
    }

    @SuppressWarnings("deprecation") // we need to overload all method calls to replay them
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
        actions.add(m -> m.visitMethodInsn(opcode, owner, name, descriptor));
    }

    @Override
    public void visitEnd() {
        //System.out.println("Inspection visitEnd");
        for (var a : actions) {
            a.accept(mv);
        }
        mv.visitEnd();
    }

    void setInnerMethodVisitor(MethodVisitor methodVisitor) {
        this.mv = methodVisitor;
    }
}
