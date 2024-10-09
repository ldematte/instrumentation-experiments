package org.elasticsearch;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class CliMain {

    static final String SECURITY_MANAGER_INTERNAL_NAME = "java/lang/SecurityManager";
    static final Set<String> excludedModules = Set.of("java.desktop");

    private static void identifySMChecksEntryPoints() throws IOException {

        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));

        var moduleExports = findModuleExports(fs);

        var callers = new HashMap<String, List<SecurityCheckClassVisitor.CallerInfo>>();
        var visitor = new SecurityCheckClassVisitor(callers);

        //Path objClassFilePath = fs.getPath("modules", "java.base", "java/lang/Object.class");
        try (var stream = Files.walk(fs.getPath("modules"))) {
            stream
                    .filter(x -> x.toString().endsWith(".class"))
                    .forEach(x -> {
                        var moduleName = x.subpath(1, 2).toString();
                        if (excludedModules.contains(moduleName) == false) {
                            try {
                                ClassReader cr = new ClassReader(Files.newInputStream(x));
                                visitor.setCurrentModule(moduleName, moduleExports.get(moduleName));
                                var path = x.getNameCount() > 3 ? x.subpath(2, x.getNameCount() - 1).toString() : "";
                                visitor.setCurrentSourcePath(path);
                                cr.accept(visitor, 0);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
        }

        for (var kv: callers.entrySet()) {
            //System.out.println(kv.getKey() + " used " + kv.getValue().size() + " times");
            for (var e: kv.getValue()) {
                System.out.println(toString(kv.getKey(), e));
            }
        }
    }

    private static String toString(String calleeName, SecurityCheckClassVisitor.CallerInfo callerInfo) {
        var s =  callerInfo.moduleName()  + ";" + callerInfo.source() + ";" + callerInfo.line() + ";" +
                callerInfo.className() + ";" + callerInfo.methodName() + ";" + callerInfo.isPublic();

        if (callerInfo.runtimePermissionType() != null) {
            s += ";" + callerInfo.runtimePermissionType();
        } else if (calleeName.equals("checkPermission")) {
            s += ";MISSING"; // missing information
        } else {
            s += ";" + calleeName;
        }

        if (callerInfo.permissionType() != null) {
            s += ";" + callerInfo.permissionType();
        } else if (calleeName.equals("checkPermission")) {
            s += ";MISSING"; // missing information
        } else {
            s += ";";
        }
        return s;
    }

    record CallChain(FindUsagesClassVisitor.EntryPoint entryPoint, CallChain next) {}

    interface UsageConsumer {
        void usageFound(CallChain originalEntryPoint, CallChain newMethod);
    }

    private static void findTransitiveUsages(
            Collection<CallChain> firstLevelCallers,
            List<Path> classesToScan,
            Set<String> moduleExports,
            boolean bubbleUpFromPublic,
            UsageConsumer usageConsumer) {
        for (var oc: firstLevelCallers) {
            var methodsToCheck = new ArrayDeque<>(Set.of(oc));
            var methodsSeen = new HashSet<>();

            while (methodsToCheck.isEmpty() == false) {
                var methodToCheck = methodsToCheck.removeFirst();
                var m = methodToCheck.entryPoint();
                var visitor2 = new FindUsagesClassVisitor(
                        moduleExports,
                        new FindUsagesClassVisitor.MethodDescriptor(m.className(), m.methodName(), m.methodDescriptor()),
                        (source, line, className, methodName, methodDescriptor, isPublic) -> {
                            var newMethod = new CallChain(
                                    new FindUsagesClassVisitor.EntryPoint(m.moduleName(), source, line, className, methodName, methodDescriptor, isPublic),
                                    methodToCheck
                            );

                            var notSeenBefore = methodsSeen.add(newMethod.entryPoint());
                            if (notSeenBefore) {
                                if (isPublic) {
                                    usageConsumer.usageFound(oc.next(), newMethod);
                                }
                                if (isPublic == false || bubbleUpFromPublic) {
                                    methodsToCheck.add(newMethod);
                                }
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

    private static Map<String, Set<String>> findModuleExports(FileSystem fs) throws IOException {
        var modulesExports = new HashMap<String, Set<String>>();
        try (var stream = Files.walk(fs.getPath("modules"))) {
            stream
                    .filter(p -> p.getFileName().toString().equals("module-info.class"))
                    .forEach(x -> {
                        try (var is = Files.newInputStream(x)) {
                            var md = ModuleDescriptor.read(is);
                            modulesExports.put(md.name(), md.exports()
                                    .stream()
                                    .filter(e -> e.isQualified() == false)
                                    .map(ModuleDescriptor.Exports::source)
                                    .collect(Collectors.toSet())
                            );
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        return modulesExports;
    }

    private static void identifyTopLevelEntryPoints(FindUsagesClassVisitor.MethodDescriptor methodToFind, boolean bubbleUpFromPublic) throws IOException {
        FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));

        var moduleExports = findModuleExports(fs);
        final var separator = '\t';

        try (var stream = Files.walk(fs.getPath("modules"))) {
            var modules = stream
                    .filter(x -> x.toString().endsWith(".class"))
                    .collect(Collectors.groupingBy(x -> x.subpath(1, 2).toString()));

            for (var kv: modules.entrySet()) {
                var moduleName = kv.getKey();
                if (excludedModules.contains(moduleName) == false) {
                    var thisModuleExports = moduleExports.get(moduleName);
                    var originalCallers = new ArrayList<CallChain>();
                    var visitor = new FindUsagesClassVisitor(
                            thisModuleExports,
                            methodToFind,
                            (source, line, className, methodName, methodDescriptor, isPublic) -> {
                                originalCallers.add(
                                        new CallChain(
                                                new FindUsagesClassVisitor.EntryPoint(
                                                        moduleName,
                                                        source,
                                                        line,
                                                        className,
                                                        methodName,
                                                        methodDescriptor,
                                                        isPublic
                                                ),
                                                new CallChain(
                                                        new FindUsagesClassVisitor.EntryPoint(
                                                            moduleName,
                                                            "",
                                                            0,
                                                            methodToFind.className(),
                                                            methodToFind.methodName(),
                                                            methodToFind.methodDescriptor(),
                                                            true
                                                        ),
                                                        null
                                                )
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

                    originalCallers.stream()
                            .filter(c -> c.entryPoint().isPublic())
                            .forEach(c -> {
                                var oc = c.next();
                                var originalEntryPoint = oc.entryPoint().moduleName() + separator +
                                        oc.entryPoint().className() + separator + oc.entryPoint().methodName();
                                var e = c.entryPoint();
                                var entryPoint = e.moduleName() + separator + e.source() + separator + e.line()
                                        + separator + e.className() + separator + e.methodName() + separator
                                        + e.methodDescriptor();
                                System.out.println(entryPoint + separator + originalEntryPoint);
                    });
                    var firstLevelCallers = bubbleUpFromPublic
                            ? originalCallers
                            : originalCallers.stream().filter(c -> c.entryPoint().isPublic() == false).toList();

                    if (firstLevelCallers.isEmpty() == false) {
                        findTransitiveUsages(firstLevelCallers, kv.getValue(), thisModuleExports, bubbleUpFromPublic,
                                (oc, callChain) -> {
                                    var originalEntryPoint = oc.entryPoint().moduleName() + separator +
                                            oc.entryPoint().className() + separator + oc.entryPoint().methodName();
                                    var s = moduleName + separator + callChain.entryPoint().source() + separator
                                            + callChain.entryPoint().line() + separator + callChain.entryPoint().className()
                                            + separator + callChain.entryPoint().methodName() + separator
                                            + callChain.entryPoint().methodDescriptor();
                                    System.out.println(s + separator + originalEntryPoint);
                                });
                    }
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

        var moduleExports = findModuleExports(fs);

        try (var stream = Files.walk(fs.getPath("modules"))) {
            var modules = stream
                    .filter(x -> x.toString().endsWith(".class"))
                    .collect(Collectors.groupingBy(x -> x.subpath(1, 2).toString()));

            for (var kv : modules.entrySet()) {
                var moduleName = kv.getKey();
                if (excludedModules.contains(moduleName) == false) {
                    var originalCallers = new ArrayList<CallChain>();
                    var visitor = new FindUsagesClassVisitor(
                            moduleExports.get(moduleName),
                            new FindUsagesClassVisitor.MethodDescriptor(SECURITY_MANAGER_INTERNAL_NAME, "checkWrite", null),
                            (source, line, className, methodName, methodDescriptor, isPublic) -> {
                                originalCallers.add(
                                        new CallChain(
                                                new FindUsagesClassVisitor.EntryPoint(
                                                        moduleName,
                                                        source,
                                                        line,
                                                        className,
                                                        methodName,
                                                        methodDescriptor,
                                                        isPublic
                                                ),
                                                null
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

    interface MethodDescriptorConsumer {
        void accept(FindUsagesClassVisitor.MethodDescriptor methodDescriptor) throws IOException;
    }

    private static void parseCsv(Path csvPath, boolean bubbleUpFromPublic, MethodDescriptorConsumer methodConsumer)
            throws IOException {
        var lines = Files.readAllLines(csvPath);
        for (var l: lines) {
            var tokens = l.split(";");
            var className = tokens[3];
            var methodName = tokens[4];
            var isPublic = Boolean.parseBoolean(tokens[5]);
            if (isPublic == false || bubbleUpFromPublic) {
                methodConsumer.accept(new FindUsagesClassVisitor.MethodDescriptor(className, methodName, null));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        boolean bubbleUpFromPublic = false;
        parseCsv(Path.of(args[0]), bubbleUpFromPublic, x -> identifyTopLevelEntryPoints(x, bubbleUpFromPublic));
        //identifyDownstreamNatives();
        //identifySMChecksEntryPoints();
    }
}
