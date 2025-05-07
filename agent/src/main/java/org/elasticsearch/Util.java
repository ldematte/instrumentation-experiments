package org.elasticsearch;

import java.util.Optional;
import java.util.function.Consumer;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

public class Util {
    /**
     * A special value representing the case where a method <em>has no caller</em>.
     * This can occur if it's called directly from the JVM.
     *
     * @see StackWalker#getCallerClass()
     */
    public static final Class<?> NO_CLASS = new Object() {
    }.getClass();

    /**
     * Why would we write this instead of using {@link StackWalker#getCallerClass()}?
     * Because that method throws {@link IllegalCallerException} if called from the "outermost frame",
     * which includes at least some cases of a method called from a native frame.
     *
     * @return the class that called the method which called this; or {@link #NO_CLASS} from the outermost frame.
     */
    @SuppressWarnings("unused") // Called reflectively from InstrumenterImpl
    public static Class<?> getCallerClass() {
        Optional<Class<?>> callerClassIfAny = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)
                .walk(
                        frames -> frames.skip(2) // Skip this method and its caller
                                .findFirst()
                                .map(StackWalker.StackFrame::getDeclaringClass)
                );
        return callerClassIfAny.orElse(NO_CLASS);
    }

    static final ScopedValue<Class<?>> DELEGATE_CHECK_CLASS = ScopedValue.newInstance();

    static void delegate(Consumer<Class<?>> runnable) {
        Class<?> callerClass = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)
                .walk(
                        frames -> frames.skip(2) // Skip this method and its caller
                                .findFirst()
                                .map(StackWalker.StackFrame::getDeclaringClass)
                )
                .orElseThrow();
        ScopedValue.runWhere(DELEGATE_CHECK_CLASS, callerClass, () -> runnable.accept(callerClass));
    }

    static void propagateDelegation(Class<?> c, Runnable r) {
        ScopedValue.runWhere(DELEGATE_CHECK_CLASS, c, r);
    }
}