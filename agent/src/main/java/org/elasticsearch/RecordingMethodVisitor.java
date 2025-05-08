package org.elasticsearch;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.objectweb.asm.Opcodes.ASM9;

class LabelUtil {
    private final HashMap<Label, String> labels = new HashMap<>();

    String getLabel(Label label) {
        var l = labels.get(label);
        if (l == null) {
            l = Integer.toString(labels.size());
            labels.put(label, l);
        }
        return l;
    }
}

class RecordingMethodVisitor extends MethodVisitor {

    private final LabelUtil labelUtil = new LabelUtil();

    static final class Instruction {
        private final int opcode;
        private final String arg;

        Instruction(int opcode, String arg) {
            this.opcode = opcode;
            this.arg = arg;
        }

        public int opcode() {
            return opcode;
        }

        public String arg() {
            return arg;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Instruction) obj;
            return this.opcode == that.opcode &&
                    Objects.equals(this.arg, that.arg);
        }

        @Override
        public int hashCode() {
            return Objects.hash(opcode, arg);
        }

        @Override
        public String toString() {
            return "Instruction[" +
                    "opcode=" + opcode + ", " +
                    "arg=" + arg + ']';
        }
    }

    private final List<Instruction> instructions = new ArrayList<>();

    RecordingMethodVisitor() {
        super(ASM9);
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        instructions.add(new Instruction(opcode, labelUtil.getLabel(label)));
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        instructions.add(new Instruction(opcode, type));
    }

    @Override
    public void visitInsn(int opcode) {
        instructions.add(new Instruction(opcode, ""));
    }

    @Override
    public void visitLabel(Label label) {
        instructions.add(new Instruction(-1, labelUtil.getLabel(label)));
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        instructions.add(new Instruction(opcode, owner + name + descriptor));
    }

    public List<Instruction> getRecordedInstructions() {
        return instructions;
    }
}
