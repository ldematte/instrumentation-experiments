package org.elasticsearch;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.function.Predicate;

import static org.objectweb.asm.Opcodes.ASM9;

class CheckAndReplayMethodVisitor extends MethodVisitor {

    private final MethodVisitor matchingVisitor;
    private final MethodVisitor nonMatchingVisitor;
    private final List<RecordingMethodVisitor.Instruction> instructionsToMatch;
    private final DeferredMethodVisitor deferredMethodVisitor;

    private int cursor = 0;
    private boolean failing;

    private final LabelUtil labelUtil = new LabelUtil();

    CheckAndReplayMethodVisitor(MethodVisitor matchingVisitor, MethodVisitor nonMatchingVisitor,
                                List<RecordingMethodVisitor.Instruction> instructionsToMatch) {
        this(new DeferredMethodVisitor(), matchingVisitor, nonMatchingVisitor, instructionsToMatch);
    }

    private CheckAndReplayMethodVisitor(DeferredMethodVisitor mv,
                                        MethodVisitor matchingVisitor,
                                        MethodVisitor nonMatchingVisitor,
                                        List<RecordingMethodVisitor.Instruction> instructionsToMatch) {
        super(ASM9, mv);
        this.deferredMethodVisitor = mv;
        this.matchingVisitor = matchingVisitor;
        this.nonMatchingVisitor = nonMatchingVisitor;
        this.instructionsToMatch = instructionsToMatch;
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
        deferredMethodVisitor.visitJumpInsn(opcode, label);
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        check(opcode, x -> labelUtil.getLabel(label).equals(x));
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        deferredMethodVisitor.visitTypeInsn(opcode, type);
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        check(opcode, x -> x.equals(type));
    }

    @Override
    public void visitInsn(int opcode) {
        deferredMethodVisitor.visitInsn(opcode);
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        check(opcode, _ -> true);
    }

    @Override
    public void visitLabel(Label label) {
        deferredMethodVisitor.visitLabel(label);
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        check(-1,  x -> labelUtil.getLabel(label).equals(x));
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        deferredMethodVisitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        if (failing || cursor >= instructionsToMatch.size()) {
            return;
        }
        check(opcode, x -> x.equals(owner + name + descriptor));
    }

    @Override
    public void visitEnd() {
        if (failing == false) {
            //System.out.println("Matching, continue with pass-through visitor");
            deferredMethodVisitor.setInnerMethodVisitor(nonMatchingVisitor);
        } else {
            //System.out.println("Failing, continue with instrumenting visitor");
            deferredMethodVisitor.setInnerMethodVisitor(matchingVisitor);
        }
        deferredMethodVisitor.visitEnd();
    }
}
