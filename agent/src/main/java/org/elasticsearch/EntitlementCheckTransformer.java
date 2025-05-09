package org.elasticsearch;

import java.lang.instrument.ClassFileTransformer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class EntitlementCheckTransformer implements ClassFileTransformer {

    private final Map<String, List<String>> targetClasses;

    static String getInternalClassName(String targetClassName) {
        return targetClassName.replaceAll("\\.", "/");
    }

    EntitlementCheckTransformer(Set<InstrumentationAgent.MethodKey> methodsToTransform) {
        this.targetClasses = methodsToTransform.stream().collect(
                Collectors.groupingBy(
                        m -> getInternalClassName(m.className()),
                        Collectors.mapping(InstrumentationAgent.MethodKey::methodName, Collectors.toList())
                )
        );
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

        //System.out.println("[Agent] Transforming class");
        try {
            var rewriter = new ClassRewriter(className, classfileBuffer);
            //System.out.println("[Agent] Rewriter created");

            Set<String> methods = new HashSet<>();
            var classMethods = targetClasses.get(className);
            if (classMethods != null) {
                methods.addAll(classMethods);
            }
            var instrumentedClassBytes = rewriter.instrumentMethodNoChecks(methods);
            if (instrumentedClassBytes != null) {
                //System.out.println("Instrumented class " + className);
                //var path = Path.of(className + ".class");
                //Files.createDirectories(path.getParent());
                //Files.write(path, instrumentedClassBytes);
                return instrumentedClassBytes;

            }
            //System.out.println("No need to instrument class");

            //return rewriter.instrumentMethod(methodName);
        } catch (Throwable t) {
            System.out.println("[Agent] error " + t);
            t.printStackTrace();
        }

        return null;
    }
}
