package org.elasticsearch;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public class EntitlementCheckTransformer implements ClassFileTransformer {

    private final String targetClassName;
    private final String methodName;
    private final ClassLoader targetClassLoader;

    public EntitlementCheckTransformer(String targetClassName, ClassLoader targetClassLoader, String methodName) {
        this.targetClassName = targetClassName.replaceAll("\\.", "/");
        this.methodName = methodName;
        this.targetClassLoader = targetClassLoader;
    }

    @Override
    public byte[] transform(
            ClassLoader loader,
            String className,
            Class<?> c,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
    ) {
        //System.out.println("[Agent] transform called for " + className);
        if (className.equals(targetClassName)) { //&& loader.equals(targetClassLoader)) {
            System.out.println("[Agent] Transforming class");
            try {
                var rewriter = new ClassRewriter(classfileBuffer);
                System.out.println("[Agent] Rewriter created");
                return rewriter.instrumentMethod(methodName);
            } catch (Throwable t) {
                System.out.println("[Agent] error " + t);
                t.printStackTrace();
            }
        }
        return classfileBuffer;
    }
}
