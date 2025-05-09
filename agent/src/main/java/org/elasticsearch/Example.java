package org.elasticsearch;

public class Example {
    private static void original_method(int arg) {
        System.out.println(arg);
    }

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
}
