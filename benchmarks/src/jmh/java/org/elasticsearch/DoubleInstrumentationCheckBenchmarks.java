package org.elasticsearch;

import org.objectweb.asm.Type;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.io.IOException;
import java.io.InputStream;

@State(Scope.Benchmark)
public class DoubleInstrumentationCheckBenchmarks {

    public static class ClassToInstrument {
        public static void methodToInstrument() {
        }
    }

    byte[] loadClassBytecodes(Class<?> clazz) throws IOException {
        String internalName = Type.getInternalName(clazz);
        String fileName = "/" + internalName + ".class";

        byte[] originalBytecodes;
        try (InputStream classStream = clazz.getResourceAsStream(fileName)) {
            if (classStream == null) {
                throw new IllegalStateException("Classfile not found in jar: " + fileName);
            }
            originalBytecodes = classStream.readAllBytes();
        }
        return originalBytecodes;
    }

    @Benchmark
    public void noCheck() throws IOException {
        var classRewriter = new ClassRewriter(loadClassBytecodes(ClassToInstrument.class));
        var instrumentedBytes = classRewriter.instrumentMethodNoChecks("methodToInstrument");
    }

    @Benchmark
    public void noCheckCalledTwice() throws IOException {
        var classRewriter = new ClassRewriter(loadClassBytecodes(ClassToInstrument.class));
        var instrumentedBytes = classRewriter.instrumentMethodNoChecks("methodToInstrument");

        classRewriter = new ClassRewriter(instrumentedBytes);
        var reInstrumentedBytes = classRewriter.instrumentMethodNoChecks("methodToInstrument");
    }

    @Benchmark
    public void checkWithAnnotation() throws IOException {
        var classRewriter = new ClassRewriter(loadClassBytecodes(ClassToInstrument.class));
        var instrumentedBytes = classRewriter.instrumentMethodWithAnnotation("methodToInstrument");
    }

    @Benchmark
    public void checkWithAnnotationCalledTwice() throws IOException {
        var classRewriter = new ClassRewriter(loadClassBytecodes(ClassToInstrument.class));
        var instrumentedBytes = classRewriter.instrumentMethodWithAnnotation("methodToInstrument");

        classRewriter = new ClassRewriter(instrumentedBytes);
        var reInstrumentedBytes = classRewriter.instrumentMethodWithAnnotation("methodToInstrument");
    }

    @Benchmark
    public void completeCheckWithTwoPasses() throws IOException {
        var classRewriter = new ClassRewriter(loadClassBytecodes(ClassToInstrument.class));
        var instrumentedBytes = classRewriter.checkAndInstrumentMethodTwoPasses("methodToInstrument");
    }

    @Benchmark
    public void completeCheckWithTwoPassesCalledTwice() throws IOException {
        var classRewriter = new ClassRewriter(loadClassBytecodes(ClassToInstrument.class));
        var instrumentedBytes = classRewriter.checkAndInstrumentMethodTwoPasses("methodToInstrument");

        classRewriter = new ClassRewriter(instrumentedBytes);
        var reInstrumentedBytes = classRewriter.checkAndInstrumentMethodTwoPasses("methodToInstrument");
    }

    @Benchmark
    public void completeCheckWithOnePass() throws IOException {
        var classRewriter = new ClassRewriter(loadClassBytecodes(ClassToInstrument.class));
        var instrumentedBytes = classRewriter.checkAndInstrumentMethodSinglePass("methodToInstrument");
    }

    @Benchmark
    public void completeCheckWithOnePassCalledTwice() throws IOException {
        var classRewriter = new ClassRewriter(loadClassBytecodes(ClassToInstrument.class));
        var instrumentedBytes = classRewriter.checkAndInstrumentMethodSinglePass("methodToInstrument");

        classRewriter = new ClassRewriter(instrumentedBytes);
        var reInstrumentedBytes = classRewriter.checkAndInstrumentMethodSinglePass("methodToInstrument");
    }
}
