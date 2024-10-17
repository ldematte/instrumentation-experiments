package org.elasticsearch;

import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;

import static org.elasticsearch.InstrumentMethodClassVisitor.InstrumentingMethodVisitor.prologue;
import static org.objectweb.asm.Opcodes.*;

class InstrumentAndAnnotateMethodClassVisitor extends ClassVisitor {

    private final String methodName;
    //private final TraceClassVisitor tracer;

    public InstrumentAndAnnotateMethodClassVisitor(ClassVisitor cv, String methodName) {
        super(ASM7, cv);
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
            return new InstrumentingMethodVisitor(
                    //new TraceMethodVisitor(methodVisitor, InstrumentAndAnnotateMethodClassVisitor.this.tracer.p)
                    methodVisitor
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
        private final static String ENTITLEMENT_ANNOTATION = "EntitlementInstrumented";

        private boolean isAnnotationPresent;

        public InstrumentingMethodVisitor(MethodVisitor mv) {
            super(Opcodes.ASM7, mv);
            //System.out.println("Instrumenting");
        }

        @Override public AnnotationVisitor visitAnnotation(String desc,
                                                           boolean visible) {
            if (visible && desc.equals(ENTITLEMENT_ANNOTATION)) {
                isAnnotationPresent = true;
            }
            return mv.visitAnnotation(desc, visible);
        }

        @Override
        public void visitCode() {
            //System.out.println("InstrumentingMethodVisitor#visitCode");
            if (isAnnotationPresent) {
                //System.out.println("Annotation is present, skipping instrumentation");
                mv.visitCode();
            } else {
                //System.out.println("Annotation not present, adding instrumentation");
                AnnotationVisitor av = mv.visitAnnotation(ENTITLEMENT_ANNOTATION, true);
                if (av != null) {
                    av.visitEnd();
                }
                isAnnotationPresent = true;

                mv.visitCode();
                prologue(this);
            }
        }
    }
}
