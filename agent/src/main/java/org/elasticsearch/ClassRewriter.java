package org.elasticsearch;

import org.objectweb.asm.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.objectweb.asm.Opcodes.*;

public class ClassRewriter {

    public static final String NATIVE_PREFIX = "$$Entitled$$";

    ClassReader reader;
    ClassWriter writer;

    public ClassRewriter(byte[] contents) {
        System.out.println("[Agent] Calling ASM");

        reader = new ClassReader(contents);
        writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    }

    public byte[] instrumentMethod(String methodName) {
        System.out.println("[Agent] Calling ASM instrumentMethod");
        reader.accept(new InstrumentMethodAdapter(writer, methodName, null), 0);
        return writer.toByteArray();
    }

    public byte[] instrumentNativeMethod(String methodName, String descriptor) {
        System.out.println("[Agent] Calling ASM instrumentNativeMethod");
        reader.accept(new RemoveMethodAdapter(writer, methodName, descriptor), 0);

//        writer.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_NATIVE, NATIVE_PREFIX + methodName,
//                descriptor, null, new String[]{"sun/nio/fs/UnixException"});

        var stubMethodVisitor = writer.visitMethod(ACC_PRIVATE | ACC_STATIC, methodName, descriptor, null,
                new String[]{"sun/nio/fs/UnixException"});

        var printer = new Textifier();
        var mv = new TraceMethodVisitor(stubMethodVisitor, printer);
        mv.visitCode();
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


        mv.visitVarInsn(ILOAD, 0);
        mv.visitVarInsn(ILOAD, 1);
        mv.visitVarInsn(ILOAD, 2);
//        stubMethodVisitor.visitMethodInsn(INVOKESTATIC, "sun/nio/fs/UnixNativeDispatcher",
//                NATIVE_PREFIX + methodName, descriptor, false);
        mv.visitMethodInsn(INVOKESTATIC, "org/elasticsearch/Natives",
                methodName, descriptor, false);
        mv.visitInsn(IRETURN);
        mv.visitEnd();

        System.out.println(printer.getText());
        return writer.toByteArray();
    }

    static class RemoveMethodAdapter extends ClassVisitor {
        private final String methodName;
        private final String descriptor;
        public RemoveMethodAdapter(
                ClassVisitor cv, String methodName, String descriptor) {
            super(ASM9, cv);
            this.methodName = methodName;
            this.descriptor = descriptor;
        }
        @Override
        public MethodVisitor visitMethod(int access, String name,
                                         String desc, String signature, String[] exceptions) {
            if (name.equals(methodName) && desc.equals(descriptor)) {
                // do not delegate to next visitor -> this removes the method
                return null;
            }
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    static class InstrumentMethodAdapter extends ClassVisitor {

        private final String methodName;
        private final BooleanSupplier checkMethod;

        private final TraceClassVisitor tracer;

        public InstrumentMethodAdapter(ClassVisitor cv, String methodName, BooleanSupplier checkMethod) {
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

                System.out.println("[Agent] method " + name + " instrumenting: " + (methodVisitor == null ? "no": "yes"));

                var recordingVisitor = new RecordingMethodVisitor();
                InstrumentingMethodVisitor.prologue(recordingVisitor);

                return new CheckingMethodVisitor(
                        new InstrumentingMethodVisitor(
                                new TraceMethodVisitor(methodVisitor, InstrumentMethodAdapter.this.tracer.p)
                        ),
                        new MethodVisitor(ASM7,
                                new TraceMethodVisitor(methodVisitor, InstrumentMethodAdapter.this.tracer.p)
                        ) {},
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

        private static class DeferredAnnotationVisitor extends AnnotationVisitor {

            private final List<Consumer<MethodVisitor>> actions;

            protected DeferredAnnotationVisitor(List<Consumer<MethodVisitor>> actions) {
                super(ASM7);
                this.actions = actions;
            }

            void init(AnnotationVisitor av) {
                this.av = av;
            }

            @Override
            public void visit(String name, Object value) {
                actions.add(m -> super.visit(name, value));
            }

            @Override
            public void visitEnum(String name, String descriptor, String value) {
                actions.add(m -> super.visitEnum(name, descriptor, value));
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String descriptor) {
                actions.add(m -> super.visitAnnotation(name, descriptor));
                return this;
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                actions.add(m -> super.visitArray(name));
                return this;
            }

            @Override
            public void visitEnd() {
                actions.add(m -> super.visitEnd());
            }
        }

        private static class DeferredMethodVisitor extends MethodVisitor {

            final List<Consumer<MethodVisitor>> actions = new ArrayList<>();

            public DeferredMethodVisitor() {
                super(Opcodes.ASM7);
            }

            @Override
            public void visitParameter(String name, int access) {
                actions.add(m -> m.visitParameter(name, access));
            }

            @Override
            public AnnotationVisitor visitAnnotationDefault() {
                var annotationVisitor = new DeferredAnnotationVisitor(actions);
                actions.add(m -> annotationVisitor.init(m.visitAnnotationDefault()));
                return annotationVisitor;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                var annotationVisitor = new DeferredAnnotationVisitor(actions);
                actions.add(m -> annotationVisitor.init(m.visitAnnotation(descriptor, visible)));
                return annotationVisitor;
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                var annotationVisitor = new DeferredAnnotationVisitor(actions);
                actions.add(m -> annotationVisitor.init(m.visitTypeAnnotation(typeRef, typePath, descriptor, visible)));
                return annotationVisitor;
            }

            @Override
            public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
                actions.add(m -> m.visitAnnotableParameterCount(parameterCount, visible));
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                var annotationVisitor = new DeferredAnnotationVisitor(actions);
                actions.add(m -> annotationVisitor.init(m.visitParameterAnnotation(parameter, descriptor, visible)));
                return annotationVisitor;
            }

            @Override
            public void visitAttribute(Attribute attribute) {
                actions.add(m -> m.visitAttribute(attribute));
            }

            @Override
            public void visitCode() {
                actions.add(MethodVisitor::visitCode);
            }

            @Override
            public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
                actions.add(m -> m.visitFrame(type, numLocal, local, numStack, stack));
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                actions.add(m -> m.visitMethodInsn(opcode, owner, name, descriptor, isInterface));
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                actions.add(m -> m.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments));
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                actions.add(m -> m.visitJumpInsn(opcode, label));
            }

            @Override
            public void visitLabel(Label label) {
                actions.add(m -> m.visitLabel(label));
            }

            @Override
            public void visitLdcInsn(Object value) {
                actions.add(m -> m.visitLdcInsn(value));
            }

            @Override
            public void visitIincInsn(int varIndex, int increment) {
                actions.add(m -> m.visitIincInsn(varIndex, increment));
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                actions.add(m -> m.visitTableSwitchInsn(min, max, dflt, labels));
            }

            @Override
            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                actions.add(m -> m.visitLookupSwitchInsn(dflt, keys, labels));
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                actions.add(m -> m.visitMultiANewArrayInsn(descriptor, numDimensions));
            }

            @Override
            public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                var annotationVisitor = new DeferredAnnotationVisitor(actions);
                actions.add(m -> annotationVisitor.init(m.visitInsnAnnotation(typeRef, typePath, descriptor, visible)));
                return annotationVisitor;
            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                actions.add(m -> m.visitTryCatchBlock(start, end, handler, type));
            }

            @Override
            public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                var annotationVisitor = new DeferredAnnotationVisitor(actions);
                actions.add(m -> annotationVisitor.init(m.visitTryCatchAnnotation(typeRef, typePath, descriptor, visible)));
                return annotationVisitor;
            }

            @Override
            public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                actions.add(m -> m.visitLocalVariable(name, descriptor, signature, start, end, index));
            }

            @Override
            public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
                var annotationVisitor = new DeferredAnnotationVisitor(actions);
                actions.add(m -> annotationVisitor.init(m.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, descriptor, visible)));
                return annotationVisitor;
            }

            @Override
            public void visitLineNumber(int line, Label start) {
                actions.add(m -> m.visitLineNumber(line, start));
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                actions.add(m -> m.visitMaxs(maxStack, maxLocals));
            }

            @Override
            public void visitInsn(int opcode) {
                actions.add(m -> m.visitInsn(opcode));
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                actions.add(m -> m.visitIntInsn(opcode, operand));
            }

            @Override
            public void visitVarInsn(int opcode, int varIndex) {
                actions.add(m -> m.visitVarInsn(opcode, varIndex));
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                actions.add(m -> m.visitTypeInsn(opcode, type));
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                actions.add(m -> m.visitFieldInsn(opcode, owner, name, descriptor));
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
                actions.add(m -> m.visitMethodInsn(opcode, owner, name, descriptor));
            }

            @Override
            public void visitEnd() {
                System.out.println("Inspection visitEnd");
                for(var a: actions) {
                    a.accept(mv);
                }
                mv.visitEnd();
            }

            public void setInnerMethodVisitor(MethodVisitor methodVisitor) {
                this.mv = methodVisitor;
            }
        }

        private static class RecordingMethodVisitor extends MethodVisitor {

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

        private static class CheckingMethodVisitor extends MethodVisitor {

            private final MethodVisitor matchingVisitor;
            private final MethodVisitor nonMatchingVisitor;
            private final List<String> instructionsToMatch;
            private final DeferredMethodVisitor deferredMethodVisitor;

            private int cursor = 0;
            private boolean failing;

            public CheckingMethodVisitor(MethodVisitor matchingVisitor, MethodVisitor nonMatchingVisitor,
                                         List<String> instructionsToMatch) {
                this(new DeferredMethodVisitor(), matchingVisitor, nonMatchingVisitor, instructionsToMatch);
            }

            private CheckingMethodVisitor(DeferredMethodVisitor mv,
                                          MethodVisitor matchingVisitor,
                                          MethodVisitor nonMatchingVisitor,
                                          List<String> instructionsToMatch) {
                super(ASM7, mv);
                this.deferredMethodVisitor = mv;
                this.matchingVisitor = matchingVisitor;
                this.nonMatchingVisitor = nonMatchingVisitor;
                this.instructionsToMatch = instructionsToMatch;
            }

            private void check(String instruction) {
                if (failing || cursor >= instructionsToMatch.size()) {
                    return;
                }
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
                deferredMethodVisitor.visitJumpInsn(opcode, label);
                var x = new Textifier();
                x.visitJumpInsn(opcode, label);
                check(x.text.getLast().toString());
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                deferredMethodVisitor.visitTypeInsn(opcode, type);
                var x = new Textifier();
                x.visitTypeInsn(opcode, type);
                check(x.text.getLast().toString());
            }

            @Override
            public void visitInsn(int opcode) {
                deferredMethodVisitor.visitInsn(opcode);
                var x = new Textifier();
                x.visitInsn(opcode);
                check(x.text.getLast().toString());
            }

            @Override
            public void visitLabel(Label label) {
                deferredMethodVisitor.visitLabel(label);
                var x = new Textifier();
                x.visitLabel(label);
                check(x.text.getLast().toString());
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                deferredMethodVisitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                var x = new Textifier();
                x.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                check(x.text.getLast().toString());
            }

            @Override
            public void visitEnd() {
                if (failing == false) {
                    System.out.println("Matching, continue with pass-through visitor");
                    deferredMethodVisitor.setInnerMethodVisitor(nonMatchingVisitor);
                } else {
                    System.out.println("Failing, continue with instrumenting visitor");
                    deferredMethodVisitor.setInnerMethodVisitor(matchingVisitor);
                }
                deferredMethodVisitor.visitEnd();
            }
        }
    }
}
