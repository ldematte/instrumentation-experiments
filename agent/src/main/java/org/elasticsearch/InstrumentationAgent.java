package org.elasticsearch;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.jar.JarFile;

public class InstrumentationAgent {

    public static void premain(String agentArgs, Instrumentation instrumentation) throws IOException {
        System.out.println("[Agent] In premain method");

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

        transformClass("java.lang.Shutdown", instrumentation, "exit");
        // sun.nio.fs.UnixNativeDispatcher
        // private static native int open0(long pathAddress, int flags, int mode) throws UnixException;
        //transformNativeClass("sun.nio.fs.UnixNativeDispatcher", instrumentation, "open0", "(JII)I");
        System.out.println("[Agent] completed");
    }

    private static void transformClass(String className, Instrumentation instrumentation, String methodName) {
        Class<?> targetCls = null;
        ClassLoader targetClassLoader = null;
        // see if we can get the class using forName
        try {
            targetCls = Class.forName(className);
            targetClassLoader = targetCls.getClassLoader();
            transform(targetCls, targetClassLoader, instrumentation, methodName);
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
                    transform(targetCls, targetClassLoader, instrumentation, methodName);
                    return;
                }
            }
        } catch (Exception ex) {
            System.out.println("Class [{}] not found with Class.forName");
            ex.printStackTrace();
        }
        throw new RuntimeException("Failed to find class [" + className + "]");
    }

    private static void transform(Class<?> clazz, ClassLoader classLoader, Instrumentation instrumentation, String methodName) {
        instrumentation.addTransformer(new EntitlementCheckTransformer(clazz.getName(), classLoader, methodName), true);
        try {
            instrumentation.retransformClasses(clazz);
        } catch (Exception ex) {
            throw new RuntimeException("Transform failed for class: [" + clazz.getName() + "]", ex);
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