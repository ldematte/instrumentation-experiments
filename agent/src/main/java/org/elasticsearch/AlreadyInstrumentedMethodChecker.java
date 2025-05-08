package org.elasticsearch;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static org.objectweb.asm.Opcodes.ASM9;

public class AlreadyInstrumentedMethodChecker extends ClassVisitor {
    private final String methodName;
    boolean instrumentationNeeded = false;

    public AlreadyInstrumentedMethodChecker(String methodName) {
        super(ASM9);
        this.methodName = methodName;
    }

    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String desc,
                                     String signature,
                                     String[] exceptions) {

        //System.out.println("[Agent] AlreadyInstrumentedMethodChecker visiting method " + name);
        if (methodName.equals(name)) {
            var recordingVisitor = new RecordingMethodVisitor();
            InstrumentMethodClassVisitor.InstrumentingMethodVisitor.prologue(recordingVisitor);

            return new CheckMethodVisitor(
                    recordingVisitor.getRecordedInstructions(),
                    matched -> instrumentationNeeded = (matched == false)
            );
        }
        return null;
    }
}
