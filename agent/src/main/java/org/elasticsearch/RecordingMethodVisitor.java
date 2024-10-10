package org.elasticsearch;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.util.List;

import static org.objectweb.asm.Opcodes.ASM7;

class RecordingMethodVisitor extends MethodVisitor {

    private final TraceMethodVisitor tracer;


    protected RecordingMethodVisitor() {
        this(new TraceMethodVisitor(new Textifier()));
    }

    private RecordingMethodVisitor(TraceMethodVisitor tracer) {
        super(ASM7, tracer);
        this.tracer = tracer;
    }

    public List<String> getRecordedInstructions() {
        var instructions = tracer.p.getText().stream().map(Object::toString).toList();
        System.out.println("Recorded instructions: ");
        for (int j = 0; j < instructions.size(); j++) {
            var i = instructions.get(j);
            System.out.print(j + ": " + i);
        }
        return instructions;
    }
}
