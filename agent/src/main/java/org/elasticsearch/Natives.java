package org.elasticsearch;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import static java.lang.foreign.ValueLayout.*;

public class Natives {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup SYMBOL_LOOKUP;
    private static final MethodHandles.Lookup MH_LOOKUP = MethodHandles.lookup();

    static {
        // We first check the loader lookup, which contains libs loaded by System.load and System.loadLibrary.
        // If the symbol isn't found there, we fall back to the default lookup, which is "common libraries" for
        // the platform, typically eg libc
        SymbolLookup loaderLookup = SymbolLookup.loaderLookup();
        SYMBOL_LOOKUP = (name) -> loaderLookup.find(name).or(() -> LINKER.defaultLookup().find(name));
    }

    // errno can change between system calls, so we capture it
    private static final StructLayout CAPTURE_ERRNO_LAYOUT = Linker.Option.captureStateLayout();
    static final Linker.Option CAPTURE_ERRNO_OPTION = Linker.Option.captureCallState("errno");
    //private static final VarHandle errno$vh = varHandleWithoutOffset(CAPTURE_ERRNO_LAYOUT, groupElement("errno"));

    static final MemorySegment errnoState = Arena.ofAuto().allocate(CAPTURE_ERRNO_LAYOUT);

//    private static final MethodHandle openWithMode$mh = downcallHandle(
//            "open",
//            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT),
//            CAPTURE_ERRNO_OPTION
//    );

    private static final MethodHandle openWithMode$mh = downcallHandle(
            "open",
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT)
    );

    static MemorySegment functionAddress(String function) {
        return SYMBOL_LOOKUP.find(function).orElseThrow(() -> new LinkageError("Native function " + function + " could not be found"));
    }

    static MethodHandle downcallHandle(String function, FunctionDescriptor functionDescriptor, Linker.Option... options) {
        return LINKER.downcallHandle(functionAddress(function), functionDescriptor, options);
    }

    public static int open0(long pathAddress, int flags, int mode) {
        try {
            return (int) openWithMode$mh.invokeExact(MemorySegment.ofAddress(pathAddress), flags, mode);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }
}
