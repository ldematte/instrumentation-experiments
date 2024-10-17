package org.elasticsearch;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.ASM7;

public class AlreadyInstrumentedMethodChecker extends ClassVisitor {
    private final String methodName;
    boolean instrumentationNeeded = false;

    public AlreadyInstrumentedMethodChecker(String methodName) {
        super(ASM7);
        this.methodName = methodName;
    }

    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String desc,
                                     String signature,
                                     String[] exceptions) {

        //System.out.println("[Agent] AlreadyInstrumentedMethodChecker visiting method " + name);
        if (name.equals(methodName)) {
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
