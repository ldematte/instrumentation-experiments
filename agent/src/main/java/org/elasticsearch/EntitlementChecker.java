package org.elasticsearch;

import java.util.Locale;

public interface EntitlementChecker {
    void check(Class<?> callerClass);

    // TODO: this should be auto-generated, maybe even directly on the impl?
    void nullCheck(Class<?> callerClass);
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

    @Override
    public void nullCheck(Class<?> callerClass) {
        System.out.printf(
                Locale.ROOT,
                "nullCheck for %s",
                callerClass.getName());
    }

    private Class<?> findClassToValidate(Class<?> callerClass) {
        if (Util.DELEGATE_CHECK_CLASS.isBound()) {
            return Util.DELEGATE_CHECK_CLASS.get();
        }
        return callerClass;
    }
}

