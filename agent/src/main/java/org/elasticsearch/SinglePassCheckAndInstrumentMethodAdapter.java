package org.elasticsearch;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.util.function.BooleanSupplier;

import static org.objectweb.asm.Opcodes.*;

class SinglePassCheckAndInstrumentMethodAdapter extends ClassVisitor {

    private final String methodName;
    private final BooleanSupplier checkMethod;

    private final TraceClassVisitor tracer;

    public SinglePassCheckAndInstrumentMethodAdapter(ClassVisitor cv, String methodName, BooleanSupplier checkMethod) {
        super(ASM7, cv);
        this.methodName = methodName;
        this.checkMethod = checkMethod;
        this.tracer = new TraceClassVisitor(cv, new PrintWriter(System.out));
    }

    @Override
    public void visit(int version, int access, String name,
                      String signature, String superName, String[] interfaces) {
        System.out.println("[Agent] Calling visit");

        cv.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String desc,
                                     String signature,
                                     String[] exceptions) {

        System.out.println("[Agent] visiting method " + name);
        if (name.equals(methodName)) {
            var methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);

            System.out.println("[Agent] method " + name + " instrumenting: " + (methodVisitor == null ? "no" : "yes"));

            var recordingVisitor = new RecordingMethodVisitor();
            InstrumentingMethodVisitor.prologue(recordingVisitor);

            return new CheckAndReplayMethodVisitor(
                    new InstrumentingMethodVisitor(
                            new TraceMethodVisitor(methodVisitor, SinglePassCheckAndInstrumentMethodAdapter.this.tracer.p)
                    ),
                    new MethodVisitor(ASM7,
                            new TraceMethodVisitor(methodVisitor, SinglePassCheckAndInstrumentMethodAdapter.this.tracer.p)
                    ) {
                    },
                    recordingVisitor.getRecordedInstructions()
            );

        }
        return cv.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        System.out.println(tracer.p.getText());
    }

    private static class InstrumentingMethodVisitor extends MethodVisitor {
        public InstrumentingMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM7, mv);
            System.out.println("Instrumenting");
        }

        @Override
        public void visitCode() {
            System.out.println("InstrumentingMethodVisitor#visitCode");
            mv.visitCode();
            prologue(this);
        }

        static void prologue(MethodVisitor mv) {
            System.out.println("Prologue");
            mv.visitMethodInsn(INVOKESTATIC, "org/elasticsearch/EntitlementChecker",
                    "check", "()Z", false);
            Label end = new Label();
            mv.visitJumpInsn(IFNE, end);
            //mv.visitFrame(F_SAME, 0, null, 0, null);
            mv.visitTypeInsn(NEW, "java/lang/UnsupportedOperationException");
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL,
                    "java/lang/UnsupportedOperationException", "<init>", "()V", false);
            mv.visitInsn(ATHROW);
            mv.visitLabel(end);
        }

        @Override
        public void visitEnd() {
            System.out.println("InstrumentingMethodVisitor#visitEnd");
            mv.visitEnd();
        }
    }
}
