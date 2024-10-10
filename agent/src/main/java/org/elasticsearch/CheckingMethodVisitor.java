package org.elasticsearch;

import org.objectweb.asm.*;
import org.objectweb.asm.util.Textifier;

import java.util.List;

import static org.objectweb.asm.Opcodes.ASM7;

class CheckingMethodVisitor extends MethodVisitor {

    private final MethodVisitor matchingVisitor;
    private final MethodVisitor nonMatchingVisitor;
    private final List<String> instructionsToMatch;
    private final DeferredMethodVisitor deferredMethodVisitor;

    private int cursor = 0;
    private boolean failing;

    CheckingMethodVisitor(MethodVisitor matchingVisitor, MethodVisitor nonMatchingVisitor,
                                 List<String> instructionsToMatch) {
        this(new DeferredMethodVisitor(), matchingVisitor, nonMatchingVisitor, instructionsToMatch);
    }

    private CheckingMethodVisitor(DeferredMethodVisitor mv,
                                  MethodVisitor matchingVisitor,
                                  MethodVisitor nonMatchingVisitor,
                                  List<String> instructionsToMatch) {
        super(ASM7, mv);
        this.deferredMethodVisitor = mv;
        this.matchingVisitor = matchingVisitor;
        this.nonMatchingVisitor = nonMatchingVisitor;
        this.instructionsToMatch = instructionsToMatch;
    }

    private void check(String instruction) {
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        if (instruction.equals(instructionsToMatch.get(cursor)) == false) {
            System.out.println("Looking for [" + instructionsToMatch.get(cursor) + "] at cursor [" + cursor + "], got [" + instruction + "]");
            failing = true;
        } else {
            System.out.println("Matched " + instructionsToMatch.get(cursor) + "] at cursor [" + cursor + "] with [" + instruction + "]");
            ++cursor;
        }
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        deferredMethodVisitor.visitJumpInsn(opcode, label);
        var x = new Textifier();
        x.visitJumpInsn(opcode, label);
        check(x.text.getLast().toString());
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        deferredMethodVisitor.visitTypeInsn(opcode, type);
        var x = new Textifier();
        x.visitTypeInsn(opcode, type);
        check(x.text.getLast().toString());
    }

    @Override
    public void visitInsn(int opcode) {
        deferredMethodVisitor.visitInsn(opcode);
        var x = new Textifier();
        x.visitInsn(opcode);
        check(x.text.getLast().toString());
    }

    @Override
    public void visitLabel(Label label) {
        deferredMethodVisitor.visitLabel(label);
        var x = new Textifier();
        x.visitLabel(label);
        check(x.text.getLast().toString());
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        deferredMethodVisitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        var x = new Textifier();
        x.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        check(x.text.getLast().toString());
    }

    @Override
    public void visitEnd() {
        if (failing == false) {
            System.out.println("Matching, continue with pass-through visitor");
            deferredMethodVisitor.setInnerMethodVisitor(nonMatchingVisitor);
        } else {
            System.out.println("Failing, continue with instrumenting visitor");
            deferredMethodVisitor.setInnerMethodVisitor(matchingVisitor);
        }
        deferredMethodVisitor.visitEnd();
    }
}
