package org.elasticsearch;

import java.lang.invoke.MethodHandle;

/**
 * We need something that will create us a Runnable calling "original_" + originalMethodName;
 * For the purpose of this PoC, a class implementing Runnable and accepting a MethodHandle is easier to produce ASM code
 * for than i.e. a lambda; we might want to use {@code LambdaMetafactory.metafactory} in the final form, and/or
 * create these classes dynamically (via ASM too), and/or make this vararg
 */
public class OriginalMethodRunnable implements Runnable {
    private final MethodHandle mh;
    private final int arg;

    // TODO: vararg to accept different arguments
    public OriginalMethodRunnable(MethodHandle mh, int arg) {
        this.mh = mh;
        this.arg = arg;
    }

    @Override
    public void run() {
        try {
            mh.invokeExact(arg);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

