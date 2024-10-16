package org.elasticsearch;

import org.objectweb.asm.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

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
        reader.accept(new SinglePassCheckAndInstrumentMethodAdapter(writer, methodName, null), 0);
        return writer.toByteArray();
    }

    public byte[] checkAndInstrumentMethod(String methodName) {
        System.out.println("[Agent] Calling ASM instrumentMethod");
        var checker = new AlreadyInstrumentedMethodChecker(methodName);
        reader.accept(checker, 0);
        if (checker.instrumentationNeeded) {
            reader.accept(new InstrumentMethodClassVisitor(writer, methodName), 0);
            return writer.toByteArray();
        }
        return null;
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
}
