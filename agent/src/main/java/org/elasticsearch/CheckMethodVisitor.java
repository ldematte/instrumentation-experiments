package org.elasticsearch;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.util.Textifier;

import java.util.List;
import java.util.function.Consumer;

import static org.objectweb.asm.Opcodes.ASM7;


class CheckMethodVisitor extends MethodVisitor {

    private final List<String> instructionsToMatch;
    private final Consumer<Boolean> matcher;

    private int cursor = 0;
    private boolean failing;


    CheckMethodVisitor(List<String> instructionsToMatch, Consumer<Boolean> matcher) {
        super(ASM7);
        this.instructionsToMatch = instructionsToMatch;
        this.matcher = matcher;
    }

    private void check(String instruction) {
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
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        var x = new Textifier();
        x.visitJumpInsn(opcode, label);
        check(x.text.getLast().toString());
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        var x = new Textifier();
        x.visitTypeInsn(opcode, type);
        check(x.text.getLast().toString());
    }

    @Override
    public void visitInsn(int opcode) {
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        var x = new Textifier();
        x.visitInsn(opcode);
        check(x.text.getLast().toString());
    }

    @Override
    public void visitLabel(Label label) {
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        var x = new Textifier();
        x.visitLabel(label);
        check(x.text.getLast().toString());
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        var x = new Textifier();
        x.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        check(x.text.getLast().toString());
    }

    @Override
    public void visitEnd() {
        var matched = failing == false && cursor >= instructionsToMatch.size();
        matcher.accept(matched);
    }
}
