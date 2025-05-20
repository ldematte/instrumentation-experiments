package org.elasticsearch;

import java.util.Locale;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

public interface EntitlementChecker {

    ScopedValue<Boolean> ALREADY_CHECKED = ScopedValue.newInstance();

    static boolean isCurrentCallAlreadyChecked() {
        return ALREADY_CHECKED.orElse(false);
    }

    void check(Class<?> callerClass);

    /**
     * Check variant that implements context-aware checks via stack frames
     */
    void check(Class<?> callerClass, Runnable originalMethodRunnable);

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
    public void check(Class<?> callerClass, Runnable originalMethodRunnable) {
        var classToValidate = findClassToValidate(callerClass);
        System.out.printf(
                Locale.ROOT,
                "Caller class: %s in %s%n",
                classToValidate.getName(),
                classToValidate.getModule()
        );

        var alreadyChecked = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .skip(2) // Skip this method and its caller
                        .filter(f -> f.getDeclaringClass().equals(EntitlementCheckerImpl.class) && f.getMethodName().equals("check"))
                ).findFirst().isPresent();

        if (alreadyChecked == false) {
            if (allowed == false) {
                throw new SecurityException(classToValidate + " not allowed");
            }
        }
        originalMethodRunnable.run();
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

