package org.elasticsearch;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;


public class CliMain {

    static final String SECURITY_MANAGER_INTERNAL_NAME = "java/lang/SecurityManager";

    static final Set<String> excludedClasses = Set.of(SECURITY_MANAGER_INTERNAL_NAME);
    static final Set<String> excludedModules = Set.of("java.desktop");

    private static void identifySMChecksEntryPoints() throws IOException {

        var callers = new HashMap<String, List<SecurityCheckClassVisitor.CallerInfo>>();
        var visitor = new SecurityCheckClassVisitor(
                Set.of(
                        SECURITY_MANAGER_INTERNAL_NAME + "#" + "checkRead",
                        SECURITY_MANAGER_INTERNAL_NAME + "#" + "checkWrite"
                ),
                callers
        );

        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        //Path objClassFilePath = fs.getPath("modules", "java.base", "java/lang/Object.class");
        try (var stream = Files.walk(fs.getPath("modules"))) {
            stream
                    .filter(x -> x.toString().endsWith(".class"))
                    .forEach(x -> {
                        var moduleName = x.subpath(1, 2).toString();
                        if (excludedModules.contains(moduleName) == false) {
                            try {
                                ClassReader cr = new ClassReader(Files.newInputStream(x));
                                visitor.setCurrentModule(moduleName);
                                cr.accept(visitor, 0);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
        }

        for (var kv: callers.entrySet()) {
            System.out.println(kv.getKey() + " used " + kv.getValue().size() + " times");
            for (var e: kv.getValue()) {
                System.out.println(toString(kv.getKey(), e));
            }
        }
    }

    private static String toString(String calleeName, SecurityCheckClassVisitor.CallerInfo callerInfo) {
        var s =  callerInfo.moduleName()  + ";" + callerInfo.source() + ";" + callerInfo.line() + ";" +
                callerInfo.className() + ";" + callerInfo.methodName();

        if (callerInfo.permissionType() != null) {
            s += ";" + callerInfo.permissionType() + ";";
            if (callerInfo.permissionType().equals("java/lang/RuntimePermission")) {
                s += callerInfo.runtimePermissionType();
            }
        } else if (calleeName.equals("checkPermission")) {
            s += ";MISSING;"; // missing information
        } else {
            s += ";;";
        }
        return s;
    }

    record CallChain(FindUsagesClassVisitor.Caller entryPoint, Set<CallChain> callers) {}

    private static void findTransitiveUsages(Collection<CallChain> firstLevelCallers, List<Path> classesToScan) {
        for (var oc: firstLevelCallers) {
            var c = oc.entryPoint;
            var originalEntryPoint = c.moduleName() + ";" + c.source() + ";" + c.line() + ";" + c.className() + ";" +
                    c.methodName();

            var methodsToCheck = new ArrayDeque<>(
                    Set.of(new FindUsagesClassVisitor.Callee(c.className(), c.methodName(), c.methodDescriptor())));
            var methodsSeen = new HashSet<>();

            while (methodsToCheck.isEmpty() == false) {
                var methodToCheck = methodsToCheck.removeFirst();
                var visitor2 = new FindUsagesClassVisitor(
                        methodToCheck,
                        (source, line, className, methodName, methodDescriptor, isPublic) -> {
                            if (isPublic) {
                                var s = source + ";" + line + ";" + className + ";" + methodName;
                                System.out.println(originalEntryPoint + ";" + s);
                            }
                            var newMethodToCheck = new FindUsagesClassVisitor.Callee(
                                    className,
                                    methodName,
                                    methodDescriptor
                            );
                            if (methodsSeen.add(newMethodToCheck)) {
                                methodsToCheck.add(newMethodToCheck);
                            }
                        }
                );

                for (var x : classesToScan) {
                    try {
                        ClassReader cr = new ClassReader(Files.newInputStream(x));
                        cr.accept(visitor2, 0);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private static void identifyTopLevelEntryPoints() throws IOException {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        try (var stream = Files.walk(fs.getPath("modules"))) {
            var modules = stream
                    .filter(x -> x.toString().endsWith(".class"))
                    .collect(Collectors.groupingBy(x -> x.subpath(1, 2).toString()));

            for (var kv: modules.entrySet()) {
                var moduleName = kv.getKey();
                if (excludedModules.contains(moduleName) == false) {
                    var originalCallers = new ArrayList<CallChain>();
                    var visitor = new FindUsagesClassVisitor(
                            new FindUsagesClassVisitor.Callee(SECURITY_MANAGER_INTERNAL_NAME, "checkWrite", null),
                            (source, line, className, methodName, methodDescriptor, isPublic) -> {
                                originalCallers.add(
                                        new CallChain(
                                                new FindUsagesClassVisitor.Caller(
                                                        moduleName,
                                                        source,
                                                        line,
                                                        className,
                                                        methodName,
                                                        methodDescriptor,
                                                        isPublic
                                                ),
                                                new HashSet<>()
                                        )
                                );
                            }
                    );

                    for (var x : kv.getValue()) {
                        try {
                            ClassReader cr = new ClassReader(Files.newInputStream(x));
                            cr.accept(visitor, 0);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    findTransitiveUsages(originalCallers, kv.getValue());
                }
            }
        }
    }

    private static void findDownstreamNatives(Collection<CallChain> firstLevelCallers, List<Path> classesToScan) {
        for (var oc: firstLevelCallers) {
            var c = oc.entryPoint;

            var methodsToCheck = new ArrayDeque<>(
                    Set.of(new CalledMethodsClassVisitor.Caller(c.className(), c.methodName(), c.methodDescriptor())));
            var methodsSeen = new HashSet<>();

            while (methodsToCheck.isEmpty() == false) {
                var methodToCheck = methodsToCheck.removeFirst();
                var visitor2 = new CalledMethodsClassVisitor(
                        methodToCheck,
                        (className, methodName, methodDescriptor) -> {
                            var newMethodToCheck = new CalledMethodsClassVisitor.Caller(
                                    className,
                                    methodName,
                                    methodDescriptor
                            );
                            if (methodsSeen.add(newMethodToCheck)) {
                                methodsToCheck.add(newMethodToCheck);
                            }
                        },
                        (className, methodName, methodDescriptor) -> {
                            var s = className + ";" + methodName;
                            System.out.println(s);
                        }
                );

                for (var x : classesToScan) {
                    try {
                        ClassReader cr = new ClassReader(Files.newInputStream(x));
                        cr.accept(visitor2, 0);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private static void identifyDownstreamNatives() throws IOException {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
        try (var stream = Files.walk(fs.getPath("modules"))) {
            var modules = stream
                    .filter(x -> x.toString().endsWith(".class"))
                    .collect(Collectors.groupingBy(x -> x.subpath(1, 2).toString()));

            for (var kv : modules.entrySet()) {
                var moduleName = kv.getKey();
                if (excludedModules.contains(moduleName) == false) {
                    var originalCallers = new ArrayList<CallChain>();
                    var visitor = new FindUsagesClassVisitor(
                            new FindUsagesClassVisitor.Callee(SECURITY_MANAGER_INTERNAL_NAME, "checkWrite", null),
                            (source, line, className, methodName, methodDescriptor, isPublic) -> {
                                originalCallers.add(
                                        new CallChain(
                                                new FindUsagesClassVisitor.Caller(
                                                        moduleName,
                                                        source,
                                                        line,
                                                        className,
                                                        methodName,
                                                        methodDescriptor,
                                                        isPublic
                                                ),
                                                new HashSet<>()
                                        )
                                );
                            }
                    );

                    for (var x : kv.getValue()) {
                        try {
                            ClassReader cr = new ClassReader(Files.newInputStream(x));
                            cr.accept(visitor, 0);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    findDownstreamNatives(originalCallers, kv.getValue());
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        //identifyTopLevelEntryPoints();
        identifyDownstreamNatives();
    }
}
