package org.elasticsearch;

import java.util.Locale;

public interface EntitlementChecker {
    void check(Class<?> callerClass);
}

class EntitlementCheckerImpl implements  EntitlementChecker {
    static boolean allowed = false;

    @Override
    public void check(Class<?> callerClass) {
        var classToValidate = callerClass;
        // TODO: delegation?

        System.out.printf(
                Locale.ROOT,
                "Caller class: %s in %s%n",
                callerClass.getName(),
                callerClass.getProtectionDomain().getCodeSource().getLocation()
        );

        if (allowed == false) {
            throw new SecurityException();
        }
    }
}

