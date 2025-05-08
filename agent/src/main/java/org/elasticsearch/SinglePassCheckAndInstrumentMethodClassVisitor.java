package org.elasticsearch;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;

import static org.elasticsearch.InstrumentMethodClassVisitor.InstrumentingMethodVisitor.prologue;
import static org.objectweb.asm.Opcodes.*;

class SinglePassCheckAndInstrumentMethodClassVisitor extends ClassVisitor {

    private final String methodName;
    //private final TraceClassVisitor tracer;

    public SinglePassCheckAndInstrumentMethodClassVisitor(ClassVisitor cv, String methodName) {
        super(ASM9, cv);
        this.methodName = methodName;
        //this.tracer = new TraceClassVisitor(cv, new PrintWriter(System.out));
    }

//    @Override
//    public void visit(int version, int access, String name,
//                      String signature, String superName, String[] interfaces) {
//        System.out.println("[Agent] Calling visit");
//        cv.visit(version, access, name, signature, superName, interfaces);
//    }

    @Override
    public MethodVisitor visitMethod(int access,
                                     String name,
                                     String desc,
                                     String signature,
                                     String[] exceptions) {

        //System.out.println("[Agent] visiting method " + name);
        if (name.equals(methodName)) {
            var methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);

            //System.out.println("[Agent] method " + name + " instrumenting: " + (methodVisitor == null ? "no" : "yes"));

            var recordingVisitor = new RecordingMethodVisitor();
            prologue(recordingVisitor);

            return new CheckAndReplayMethodVisitor(
                    new InstrumentingMethodVisitor(
                            //new TraceMethodVisitor(methodVisitor, SinglePassCheckAndInstrumentMethodClassVisitor.this.tracer.p)
                            methodVisitor
                    ),
                    new MethodVisitor(ASM9,
                            //new TraceMethodVisitor(methodVisitor, SinglePassCheckAndInstrumentMethodClassVisitor.this.tracer.p)
                            methodVisitor
                    ) {
                    },
                    recordingVisitor.getRecordedInstructions()
            );

        }
        return cv.visitMethod(access, name, desc, signature, exceptions);
    }

//    @Override
//    public void visitEnd() {
//        super.visitEnd();
//        System.out.println(tracer.p.getText());
//    }

    private static class InstrumentingMethodVisitor extends MethodVisitor {
        public InstrumentingMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
            //System.out.println("Instrumenting");
        }

        @Override
        public void visitCode() {
            //System.out.println("InstrumentingMethodVisitor#visitCode");
            mv.visitCode();
            prologue(this);
        }
    }
}
