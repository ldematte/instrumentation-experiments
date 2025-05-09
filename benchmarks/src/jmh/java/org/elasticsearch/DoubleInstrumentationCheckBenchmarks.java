package org.elasticsearch;

import org.objectweb.asm.Type;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@State(Scope.Benchmark)
public class DoubleInstrumentationCheckBenchmarks {

    public static class ClassToInstrument {
        public static void methodToInstrument() {
        }
    }

    private static final byte[] originalBytecodes = loadClassBytecodes(ClassToInstrument.class);

    private static final String CLASS_NAME = ClassToInstrument.class.getName();

    static byte[] loadClassBytecodes(Class<?> clazz) {
        String internalName = Type.getInternalName(clazz);
        String fileName = "/" + internalName + ".class";

        byte[] originalBytecodes;
        try (InputStream classStream = clazz.getResourceAsStream(fileName)) {
            if (classStream == null) {
                throw new IllegalStateException("Classfile not found in jar: " + fileName);
            }
            originalBytecodes = classStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return originalBytecodes;
    }

    @Benchmark
    public byte[] noCheck() {
        var classRewriter = new ClassRewriter(CLASS_NAME, originalBytecodes);
        return classRewriter.instrumentMethodNoChecks(Set.of("methodToInstrument"));
    }

    @Benchmark
    public byte[] noCheckCalledTwice() {
        var classRewriter = new ClassRewriter(CLASS_NAME, originalBytecodes);
        var instrumentedBytes = classRewriter.instrumentMethodNoChecks(Set.of("methodToInstrument"));

        classRewriter = new ClassRewriter(CLASS_NAME, instrumentedBytes);
        return classRewriter.instrumentMethodNoChecks(Set.of("methodToInstrument"));
    }

    @Benchmark
    public byte[] checkWithAnnotation() {
        var classRewriter = new ClassRewriter(CLASS_NAME, originalBytecodes);
        return classRewriter.instrumentMethodWithAnnotation("methodToInstrument");
    }

    @Benchmark
    public byte[] checkWithAnnotationCalledTwice() {
        var classRewriter = new ClassRewriter(CLASS_NAME, originalBytecodes);
        var instrumentedBytes = classRewriter.instrumentMethodWithAnnotation("methodToInstrument");

        classRewriter = new ClassRewriter(CLASS_NAME, instrumentedBytes);
        return classRewriter.instrumentMethodWithAnnotation("methodToInstrument");
    }

    @Benchmark
    public byte[] completeCheckWithTwoPasses() {
        var classRewriter = new ClassRewriter(CLASS_NAME, originalBytecodes);
        return classRewriter.checkAndInstrumentMethodTwoPasses("methodToInstrument");
    }

    @Benchmark
    public byte[] completeCheckWithTwoPassesCalledTwice() {
        var classRewriter = new ClassRewriter(CLASS_NAME, originalBytecodes);
        var instrumentedBytes = classRewriter.checkAndInstrumentMethodTwoPasses("methodToInstrument");

        classRewriter = new ClassRewriter(CLASS_NAME, instrumentedBytes);
        return classRewriter.checkAndInstrumentMethodTwoPasses("methodToInstrument");
    }

    @Benchmark
    public byte[] completeCheckWithOnePass() {
        var classRewriter = new ClassRewriter(CLASS_NAME, originalBytecodes);
        return classRewriter.checkAndInstrumentMethodSinglePass("methodToInstrument");
    }

    @Benchmark
    public byte[] completeCheckWithOnePassCalledTwice() {
        var classRewriter = new ClassRewriter(CLASS_NAME, originalBytecodes);
        var instrumentedBytes = classRewriter.checkAndInstrumentMethodSinglePass("methodToInstrument");

        classRewriter = new ClassRewriter(CLASS_NAME, instrumentedBytes);
        return classRewriter.checkAndInstrumentMethodSinglePass("methodToInstrument");
    }
}
