package org.elasticsearch;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Example {
    /**
     * The original method, renamed and made private.
     */
    private static void original_method(int arg) {
        System.out.println(arg);
    }

    /**
     * The instrumented method retains the same signature, but it is a just a stub that replaces the implementation
     * with checks and then delegates the work to the renamed method.
     * *
     * This is the {@link ScopedValue} variant.
     */
    static void method(int arg) {
        if (EntitlementChecker.isCurrentCallAlreadyChecked() == false) {
            EntitlementCheckerHandle.instance().check(Util.getCallerClass());
            ScopedValue.runWhere(EntitlementChecker.ALREADY_CHECKED, Boolean.TRUE, new OriginalMethodRunnable(null, arg));
            //ScopedValue.runWhere(EntitlementChecker.ALREADY_CHECKED, Boolean.TRUE, new original_method_runnable(arg));
            //ScopedValue.runWhere(EntitlementChecker.ALREADY_CHECKED, Boolean.TRUE, () -> original_method(arg));
        } else {
            original_method(arg);
        }
    }

    static MethodHandle mh;
    public static MethodType mt = MethodType.methodType(Void.TYPE, Integer.TYPE);

    static {
        try {
            mh = MethodHandles.lookup().findStatic(System.class, "exit", mt);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The instrumented method retains the same signature, but it is a just a stub that replaces the implementation
     * with checks and then delegates the work to the renamed method.
     * *
     * This is the {@link StackWalker} variant.
     */
    static void method_2(int arg) {
        EntitlementCheckerHandle.instance().check(Util.getCallerClass(), new OriginalMethodRunnable(mh, arg));
    }
}
