package org.elasticsearch;

import java.util.Locale;

public interface EntitlementChecker {
    void check(Class<?> callerClass);
}

class EntitlementCheckerImpl implements  EntitlementChecker {
    static boolean allowed = false;

    @Override
    public void check(Class<?> callerClass) {
        var classToValidate = findClassToValidate(callerClass);
        System.out.printf(
                Locale.ROOT,
                "Caller class: %s in %s%n",
                classToValidate.getName(),
                classToValidate.getModule()
        );

        if (allowed == false) {
            throw new SecurityException(classToValidate + " not allowed");
        }
    }

    private Class<?> findClassToValidate(Class<?> callerClass) {
        if (Util.DELEGATE_CHECK_CLASS.isBound()) {
            return Util.DELEGATE_CHECK_CLASS.get();
        }
        return callerClass;
    }
}

