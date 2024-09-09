package org.elasticsearch;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.security.Permission;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

class SecurityCheckClassVisitor extends ClassVisitor {

    record CallerInfo(
            String moduleName,
            String source,
            int line,
            String className,
            String methodName,
            String permissionType,
            String runtimePermissionType
    ) {}

    private final Set<String> methodsToReport;
    private final Map<String, List<CallerInfo>> callerInfoByMethod;
    private String className;
    private String source;
    private String moduleName;

    protected SecurityCheckClassVisitor(Set<String> methodsToReport, Map<String, List<CallerInfo>> callerInfoByMethod) {
        super(ASM9);
        this.methodsToReport = methodsToReport;
        this.callerInfoByMethod = callerInfoByMethod;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.className = name;
    }

    @Override
    public void visitSource(String source, String debug) {
        super.visitSource(source, debug);
        this.source = source;
    }

    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String descriptor,
            String signature,
            String[] exceptions
    ) {
        if (CliMain.excludedClasses.contains(this.className)) {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        return new SecurityCheckMethodVisitor(
                new TraceMethodVisitor(
                        super.visitMethod(access, name, descriptor, signature, exceptions),
                        new Textifier()
                ),
                name
        );
    }

    public void setCurrentModule(String moduleName) {
        this.moduleName = moduleName;
    }

    private class SecurityCheckMethodVisitor extends MethodVisitor {

        private final String methodName;
        private int line;
        private boolean callsTarget;
        private final TraceMethodVisitor traceMethodVisitor;
        private String permissionType;
        private String runtimePermissionType;

        protected SecurityCheckMethodVisitor(TraceMethodVisitor mv, String methodName) {
            super(ASM9, mv);
            this.methodName = methodName;
            this.traceMethodVisitor = mv;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, type);
            if (opcode == NEW) {
                if (type.endsWith("Permission")) {
                    var objectType = Type.getObjectType(type);
                    try {
                        var clazz = Class.forName(objectType.getClassName());
                        if (Permission.class.isAssignableFrom(clazz)) {
                            permissionType = type;
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, owner, name, descriptor);
            if (opcode == GETSTATIC && descriptor.endsWith("Permission;")) {
                var permissionType = Type.getType(descriptor);
                try {
                    var clazz = Class.forName(permissionType.getClassName());
                    if (Permission.class.isAssignableFrom(clazz)) {
                        this.permissionType = permissionType.getInternalName();
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }

                var objectType = Type.getObjectType(owner);
                try {
                    var clazz = Class.forName(objectType.getClassName());
                    Arrays.stream(clazz.getDeclaredFields())
                            .filter(f -> Modifier.isStatic(f.getModifiers()) && Modifier.isFinal(f.getModifiers()))
                            .filter(f -> f.getName().equals(name))
                            .findFirst()
                            .ifPresent(x -> {
                                if (Permission.class.isAssignableFrom(x.getType())) {
                                    try {
                                        x.setAccessible(true);
                                        var p = (Permission) (x.get(null));
                                        this.runtimePermissionType = p.getName();
                                    } catch (IllegalAccessException | InaccessibleObjectException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });

                } catch (ClassNotFoundException | NoClassDefFoundError | UnsatisfiedLinkError e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(value);
            if (permissionType != null && permissionType.equals("java/lang/RuntimePermission")) {
                this.runtimePermissionType = value.toString();
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            if (opcode == INVOKEVIRTUAL ||
                    opcode == INVOKESPECIAL ||
                    opcode == INVOKESTATIC ||
                    opcode == INVOKEINTERFACE ||
                    opcode == INVOKEDYNAMIC) {
                var method = owner + "#" + name;

                if (methodsToReport.contains(method)) {
                    var callers = callerInfoByMethod.computeIfAbsent(method, _ -> new ArrayList<>());
                    callers.add(new CallerInfo(moduleName, source, line, className, methodName, null, null));
                }
                if (name.equals("checkPermission")) {
                    this.callsTarget = true;
                    var callers = callerInfoByMethod.computeIfAbsent(name, _ -> new ArrayList<>());
                    callers.add(new CallerInfo(
                            moduleName,
                            source,
                            line,
                            className,
                            methodName,
                            permissionType,
                            runtimePermissionType
                    ));
                    this.permissionType = null;
                    this.runtimePermissionType = null;
                }
            }
        }

        @Override
        public void visitParameter(String name, int access) {
            if (name != null)
                super.visitParameter(name, access);
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            super.visitLineNumber(line, start);
            this.line = line;
        }

        @Override
        public void visitEnd() {
            super.visitEnd();
            if (callsTarget) {
                //System.out.println(traceMethodVisitor.p.getText());
            }
        }
    }
}
