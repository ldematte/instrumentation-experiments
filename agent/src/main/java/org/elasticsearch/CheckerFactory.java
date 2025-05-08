package org.elasticsearch;

import java.lang.invoke.*;
import java.util.List;
import java.util.Map;

public class CheckerFactory {

    public static Map<String, List<Class<?>>> methodsToInterfaces;

    static MethodHandle nullCheck$mh;

    static {
        try {
            // TODO: this should be dynamic too, passed to the bootstrap function, as we have different signatures in reality
            nullCheck$mh = MethodHandles.lookup().findStatic(CheckerFactory.class, "nullCheck", MethodType.methodType(void.class, EntitlementChecker.class, Class.class));
            // Or alternatively, use a nullCheck method defined on EntitlementChecker (less complex, less desirable)
            //nullCheck$mh = MethodHandles.lookup().findVirtual(EntitlementChecker.class, "nullCheck", MethodType.methodType(void.class, Class.class));

        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static void nullCheck(EntitlementChecker instance, Class<?> callerClass) {}

    public static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type, String methodName,
                                     MethodHandle targetMH) {

        System.out.println("Inside boostrap");
        final Class<?> callerClass = caller.lookupClass();
        var candidates = methodsToInterfaces.get(methodName);
        for (var candidateClass: candidates) {
            if (candidateClass.isAssignableFrom(callerClass)) {
                System.out.println("Inheritance check YES");
                // This method is one of those we want to instrument via inheritance of checks

                return new ConstantCallSite(targetMH.asType(type));
            }
        }

        // No, we are not interested in checking this
        System.out.println("Inheritance check NO");
        // TODO: instead of this, we should generate an static method + MH with the same signature as targetMH
        return new ConstantCallSite(nullCheck$mh.asType(type));
    }
}
