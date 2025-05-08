package org.elasticsearch;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.function.Consumer;

import static org.objectweb.asm.Opcodes.ASM9;

class DeferredAnnotationVisitor extends AnnotationVisitor {

    private final List<Consumer<MethodVisitor>> actions;

    DeferredAnnotationVisitor(List<Consumer<MethodVisitor>> actions) {
        super(ASM9);
        this.actions = actions;
    }

    void init(AnnotationVisitor av) {
        this.av = av;
    }

    @Override
    public void visit(String name, Object value) {
        actions.add(_ -> super.visit(name, value));
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
        actions.add(_ -> super.visitEnum(name, descriptor, value));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String descriptor) {
        actions.add(_ -> super.visitAnnotation(name, descriptor));
        return this;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        actions.add(_ -> super.visitArray(name));
        return this;
    }

    @Override
    public void visitEnd() {
        actions.add(_ -> super.visitEnd());
    }
}
