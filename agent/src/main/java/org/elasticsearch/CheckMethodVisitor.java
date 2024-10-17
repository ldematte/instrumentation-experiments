package org.elasticsearch;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.objectweb.asm.Opcodes.ASM7;


class CheckMethodVisitor extends MethodVisitor {

    private final List<RecordingMethodVisitor.Instruction> instructionsToMatch;
    private final Consumer<Boolean> matcher;

    private int cursor = 0;
    private boolean failing;

    private final LabelUtil labelUtil = new LabelUtil();

    CheckMethodVisitor(List<RecordingMethodVisitor.Instruction> instructionsToMatch, Consumer<Boolean> matcher) {
        super(ASM7);
        this.instructionsToMatch = instructionsToMatch;
        this.matcher = matcher;
    }

    private void check(int opcode, Predicate<String> arg) {
        var instructionToMatch = instructionsToMatch.get(cursor);
        if (opcode == instructionToMatch.opcode() && arg.test(instructionToMatch.arg())) {
            //System.out.println("Matched " + instructionToMatch + "] at cursor [" + cursor + "]");
            ++cursor;
        } else {
            //System.out.println("Looking for [" + instructionToMatch + "] at cursor [" + cursor + "], got [" + opcode + ":" + arg + "]");
            failing = true;
        }
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        check(opcode, x -> labelUtil.getLabel(label).equals(x));
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        check(opcode, x -> x.equals(type));
    }

    @Override
    public void visitInsn(int opcode) {
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        check(opcode, _ -> true);
    }

    @Override
    public void visitLabel(Label label) {
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        check(-1, x -> labelUtil.getLabel(label).equals(x));
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        check(opcode, x -> x.equals(owner + name + descriptor));
    }

    @Override
    public void visitEnd() {
        var matched = failing == false && cursor >= instructionsToMatch.size();
        matcher.accept(matched);
    }
}
