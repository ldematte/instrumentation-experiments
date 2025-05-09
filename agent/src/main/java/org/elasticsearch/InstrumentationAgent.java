package org.elasticsearch;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InstrumentationAgent {

    record MethodKey(String className, String methodName) {}

    public static void premain(String agentArgs, Instrumentation instrumentation) throws IOException {
        System.out.println("[Agent] In premain method");

        // TODO: the key should be name + descriptor, here I used just the name for simplicity
        CheckerFactory.methodsToInterfaces = Map.of("exists", List.of(FileSystemProvider.class));

        try (var stream = Files.list(Paths.get("./agent/build/libs/"))
                .filter(file -> !Files.isDirectory(file))) {
            stream.forEach(path -> {
                try {
                    instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(path.toFile()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        Set<MethodKey> classesToTransform = Set.of(
                transformClass("java.lang.Shutdown", "exit", instrumentation)
                //transformClass("java.nio.file.Files", "exists", instrumentation)
                //transformClass("java.io.File", "exists", instrumentation)
        );
        transform(classesToTransform, CheckerFactory.methodsToInterfaces, instrumentation);
        // sun.nio.fs.UnixNativeDispatcher
        // private static native int open0(long pathAddress, int flags, int mode) throws UnixException;
        //transformNativeClass("sun.nio.fs.UnixNativeDispatcher", instrumentation, "open0", "(JII)I");
        System.out.println("[Agent] completed");
    }

    private static MethodKey transformClass(String className, String methodName, Instrumentation instrumentation) {
        return new MethodKey(className, methodName);
    }

    private static void transform(Set<MethodKey> methodsToTransform, Map<String, List<Class<?>>> inheritanceMethods, Instrumentation instrumentation) {
        instrumentation.addTransformer(new EntitlementCheckTransformer(methodsToTransform), true);
        try {
            Set<Class<?>> inheritanceMethodClasses = inheritanceMethods.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

            var classesToRetransform = Stream.concat(
                    Arrays.stream(instrumentation.getAllLoadedClasses()).filter(x -> methodsToTransform.stream().anyMatch(m -> m.className().equals(x.getName()))),
                    Arrays.stream(instrumentation.getAllLoadedClasses()).filter(x -> inheritanceMethodClasses.stream().anyMatch(base -> base.isAssignableFrom(x)))
            ).toArray(Class[]::new);

            System.out.println("classesToRetransform: " + Arrays.stream(classesToRetransform).map(Class::getSimpleName).collect(Collectors.joining(";")));

            //instrumentation.retransformClasses(classesToRetransform);
        } catch (Exception ex) {
            throw new RuntimeException("Retransform failed", ex);
        }
    }

    private static void transformNativeClass(String className, Instrumentation instrumentation, String methodName, String descriptor)  {
        Class<?> targetCls = null;
        ClassLoader targetClassLoader = null;
        // see if we can get the class using forName
        try {
            targetCls = Class.forName(className);
            targetClassLoader = targetCls.getClassLoader();
            transformNative(targetCls, targetClassLoader, instrumentation, methodName, descriptor);
            return;
        } catch (Exception ex) {
            System.out.println("Class [{}] not found with Class.forName");
            ex.printStackTrace();
        }
        // otherwise iterate all loaded classes and find what we want
        try {
            for(Class<?> clazz: instrumentation.getAllLoadedClasses()) {
                if(clazz.getName().equals(className)) {
                    targetCls = clazz;
                    targetClassLoader = targetCls.getClassLoader();
                    transformNative(targetCls, targetClassLoader, instrumentation, methodName, descriptor);
                    return;
                }
            }
        } catch (Exception ex) {
            System.out.println("Class [{}] not found with Class.forName");
            ex.printStackTrace();
        }
        throw new RuntimeException("Failed to find class [" + className + "]");
    }

    private static void transformNative(Class<?> clazz, ClassLoader classLoader, Instrumentation instrumentation, String methodName, String descriptor) {
        var transformer = new EntitlementCheckNativeTransformer(clazz.getName(), classLoader, methodName, descriptor);
        instrumentation.addTransformer(transformer, true);
        //instrumentation.setNativeMethodPrefix(transformer, ClassRewriter.NATIVE_PREFIX);
        try {
            instrumentation.retransformClasses(clazz);
        } catch (Exception ex) {
            throw new RuntimeException("Transform failed for class: [" + clazz.getName() + "]", ex);
        }
    }
}