/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package uk.ac.manchester.tornado.drivers.metal.ffm;

import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_INT;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_LONG;
import static uk.ac.manchester.tornado.runtime.ffm.FFMSupport.C_POINTER;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import uk.ac.manchester.tornado.runtime.ffm.FFMSupport;

/**
 * The Objective-C runtime, reached through {@code java.lang.foreign}.
 *
 * <p>
 * Metal has no C API. {@code MTLDevice}, {@code MTLCommandQueue} and the rest are Objective-C
 * protocols, and the only way to call them without a compiled shim is the Objective-C runtime,
 * which <em>is</em> a C ABI: {@code objc_getClass} to find a class, {@code sel_registerName} to
 * intern a selector, and {@code objc_msgSend} to send one. This class is that layer; everything
 * Metal-specific is in {@link MetalAPI}.
 *
 * <h2>The one rule that matters</h2>
 *
 * {@code objc_msgSend} is declared variadic in the headers but is not a variadic function: it is a
 * trampoline that must be called through a prototype matching the <em>target method's</em>
 * signature exactly, so the arguments land in the registers the method expects. In C you cast the
 * symbol per call site. Here, the equivalent is a separate downcall handle per signature, which is
 * what {@link #msgSend(FunctionDescriptor)} hands out (cached, since the set of signatures is
 * small and fixed). Reusing a handle whose descriptor does not match the selector being sent is
 * undefined behaviour, not a type error, so each wrapper in {@link MetalAPI} names its descriptor
 * next to the selector it goes with.
 *
 * <h2>Architecture caveats, for the person finishing this on a Mac</h2>
 *
 * <ul>
 * <li><b>arm64 only, as written.</b> On Apple silicon every message send goes through
 * {@code objc_msgSend}, including ones returning a struct or a float. On x86_64 the ABI splits
 * these out: large struct returns use {@code objc_msgSend_stret} and floating-point returns
 * {@code objc_msgSend_fpret}. {@link #IS_APPLE_SILICON} records which host this is;
 * {@link #msgSendStret(FunctionDescriptor)} exists for the x86_64 path and returns the plain
 * handle on arm64, where no split applies.</li>
 * <li><b>No ARC.</b> Objective-C reference counting is compiler-inserted, and there is no compiler
 * here. Anything from a method whose name begins {@code new}, {@code alloc}, {@code copy} or
 * {@code mutableCopy} is owned by the caller and must be {@link #release}d; anything else is
 * autoreleased and must be {@link #retain}ed to outlive the enclosing pool. {@link AutoreleasePool}
 * brackets a scope so that autoreleased temporaries do not accumulate.</li>
 * <li><b>Struct arguments are passed by value.</b> {@code MTLSize} is three {@code NSUInteger}s,
 * and {@code dispatchThreadgroups:threadsPerThreadgroup:} takes two of them by value. Panama
 * handles that natively when the descriptor names {@link #MTL_SIZE} rather than a pointer.</li>
 * </ul>
 */
public final class ObjCRuntime {

    /** {@code MTLSize}: three {@code NSUInteger} (64-bit) fields, passed and returned by value. */
    public static final MemoryLayout MTL_SIZE = MemoryLayout.structLayout(C_LONG.withName("width"), C_LONG.withName("height"), C_LONG.withName("depth"));

    /**
     * Whether this is Apple silicon, which decides whether the {@code _stret} and {@code _fpret}
     * message-send variants exist at all.
     */
    public static final boolean IS_APPLE_SILICON = isAppleSilicon();

    private static final SymbolLookup LIBOBJC = FFMSupport.loadLibrary("libobjc.A.dylib", "/usr/lib/libobjc.A.dylib");

    /**
     * Frameworks are opened for their side effect: loading one registers its Objective-C classes
     * with the runtime, which is what makes {@code objc_getClass("MTLCompileOptions")} resolve.
     * Foundation brings in {@code NSString} and {@code NSAutoreleasePool}.
     */
    private static final SymbolLookup FOUNDATION = FFMSupport.loadLibrary("/System/Library/Frameworks/Foundation.framework/Foundation");

    private static final SymbolLookup METAL = FFMSupport.loadLibrary("/System/Library/Frameworks/Metal.framework/Metal");

    private static final MethodHandle OBJC_GET_CLASS;
    private static final MethodHandle SEL_REGISTER_NAME;
    private static final MethodHandle OBJC_MSG_SEND_SYMBOL_PROBE;

    /** One downcall handle per message-send signature; see the class comment for why. */
    private static final Map<FunctionDescriptor, MethodHandle> MSG_SEND = new ConcurrentHashMap<>();

    private static final Map<FunctionDescriptor, MethodHandle> MSG_SEND_STRET = new ConcurrentHashMap<>();

    /** Interned classes and selectors: both are process-wide and never change. */
    private static final Map<String, Long> CLASSES = new ConcurrentHashMap<>();

    private static final Map<String, Long> SELECTORS = new ConcurrentHashMap<>();

    static {
        if (LIBOBJC == null) {
            OBJC_GET_CLASS = null;
            SEL_REGISTER_NAME = null;
            OBJC_MSG_SEND_SYMBOL_PROBE = null;
        } else {
            OBJC_GET_CLASS = FFMSupport.downcall(LIBOBJC, FunctionDescriptor.of(C_POINTER, C_POINTER), "objc_getClass");
            SEL_REGISTER_NAME = FFMSupport.downcall(LIBOBJC, FunctionDescriptor.of(C_POINTER, C_POINTER), "sel_registerName");
            // Only to confirm the symbol is there; real sends get their own typed handles.
            OBJC_MSG_SEND_SYMBOL_PROBE = FFMSupport.downcall(LIBOBJC, FunctionDescriptor.ofVoid(C_POINTER, C_POINTER), "objc_msgSend");
        }
    }

    private ObjCRuntime() {
    }

    private static boolean isAppleSilicon() {
        String architecture = System.getProperty("os.arch", "");
        return architecture.equals("aarch64") || architecture.equals("arm64");
    }

    /**
     * Whether the Objective-C runtime and the Metal framework could both be opened. False on any
     * non-Apple host, where every method here would otherwise fail on a null handle.
     */
    public static boolean isAvailable() {
        return LIBOBJC != null && METAL != null && OBJC_GET_CLASS != null && OBJC_MSG_SEND_SYMBOL_PROBE != null;
    }

    /** The Foundation framework lookup, for the few plain C functions that live there. */
    public static SymbolLookup foundation() {
        return FOUNDATION;
    }

    /** The Metal framework lookup; {@code MTLCreateSystemDefaultDevice} is a C function in it. */
    public static SymbolLookup metal() {
        return METAL;
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException e) {
            throw e;
        }
        if (t instanceof Error e) {
            throw e;
        }
        throw new IllegalStateException(t);
    }

    /** Looks up an Objective-C class by name, interning the result. Returns 0 if it is unknown. */
    public static long objc_getClass(String name) {
        if (OBJC_GET_CLASS == null) {
            return 0;
        }
        return CLASSES.computeIfAbsent(name, key -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment result = (MemorySegment) OBJC_GET_CLASS.invokeExact(FFMSupport.allocateCString(arena, key));
                return result.address();
            } catch (Throwable t) {
                throw rethrow(t);
            }
        });
    }

    /** Interns a selector by name. Selectors are unique per process, so this is cached too. */
    public static long sel(String name) {
        if (SEL_REGISTER_NAME == null) {
            return 0;
        }
        return SELECTORS.computeIfAbsent(name, key -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment result = (MemorySegment) SEL_REGISTER_NAME.invokeExact(FFMSupport.allocateCString(arena, key));
                return result.address();
            } catch (Throwable t) {
                throw rethrow(t);
            }
        });
    }

    /**
     * A handle for sending a message whose target method has the given signature.
     *
     * <p>
     * {@code descriptor} describes the <em>method</em>, not {@code objc_msgSend}: its first two
     * arguments must be the receiver ({@code id}) and the selector ({@code SEL}), both
     * {@code JAVA_LONG} here, followed by the method's own arguments.
     */
    public static MethodHandle msgSend(FunctionDescriptor descriptor) {
        return MSG_SEND.computeIfAbsent(descriptor, key -> FFMSupport.downcall(LIBOBJC, key, "objc_msgSend"));
    }

    /**
     * The variant for a method returning a struct too large for registers.
     *
     * <p>
     * On Apple silicon there is no such variant -- the caller passes the return slot in {@code x8}
     * and the ordinary send is used -- so this returns the same handle {@link #msgSend} would. It is
     * separate so that an x86_64 port has one place to change, and so that call sites record which
     * sends return a struct.
     */
    public static MethodHandle msgSendStret(FunctionDescriptor descriptor) {
        if (IS_APPLE_SILICON) {
            return msgSend(descriptor);
        }
        return MSG_SEND_STRET.computeIfAbsent(descriptor, key -> FFMSupport.downcall(LIBOBJC, key, "objc_msgSend_stret"));
    }

    /** Sends a selector taking no arguments and returning an object or integer. */
    public static long send(long receiver, String selector) {
        if (receiver == 0) {
            return 0;
        }
        try {
            return (long) msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG)).invokeExact(receiver, sel(selector));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Sends a selector taking one {@code id}- or integer-sized argument. */
    public static long send(long receiver, String selector, long argument) {
        if (receiver == 0) {
            return 0;
        }
        try {
            return (long) msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG)).invokeExact(receiver, sel(selector), argument);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Sends a selector taking two {@code id}- or integer-sized arguments. */
    public static long send(long receiver, String selector, long first, long second) {
        if (receiver == 0) {
            return 0;
        }
        try {
            return (long) msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG, C_LONG)).invokeExact(receiver, sel(selector), first, second);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Sends a selector taking three {@code id}- or integer-sized arguments. */
    public static long send(long receiver, String selector, long first, long second, long third) {
        if (receiver == 0) {
            return 0;
        }
        try {
            return (long) msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_LONG, C_LONG, C_LONG)).invokeExact(receiver, sel(selector), first, second, third);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Sends a selector returning {@code BOOL}, which is a signed char. */
    public static boolean sendBoolean(long receiver, String selector) {
        if (receiver == 0) {
            return false;
        }
        try {
            return (byte) msgSend(FunctionDescriptor.of(FFMSupport.C_CHAR, C_LONG, C_LONG)).invokeExact(receiver, sel(selector)) != 0;
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Sends a selector returning {@code void}. */
    public static void sendVoid(long receiver, String selector) {
        if (receiver == 0) {
            return;
        }
        try {
            msgSend(FunctionDescriptor.ofVoid(C_LONG, C_LONG)).invokeExact(receiver, sel(selector));
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Sends a selector returning {@code void} and taking one argument. */
    public static void sendVoid(long receiver, String selector, long argument) {
        if (receiver == 0) {
            return;
        }
        try {
            msgSend(FunctionDescriptor.ofVoid(C_LONG, C_LONG, C_LONG)).invokeExact(receiver, sel(selector), argument);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code [[NSString alloc] initWithUTF8String:]}, owned by the caller: release it. */
    public static long newNSString(String value) {
        long nsString = objc_getClass("NSString");
        if (nsString == 0) {
            return 0;
        }
        try (Arena arena = Arena.ofConfined()) {
            long allocated = send(nsString, "alloc");
            MemorySegment utf8 = FFMSupport.allocateCString(arena, value);
            return (long) msgSend(FunctionDescriptor.of(C_LONG, C_LONG, C_LONG, C_POINTER)).invokeExact(allocated, sel("initWithUTF8String:"), utf8);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** Reads an {@code NSString} back as a Java string via {@code -UTF8String}. */
    public static String toJavaString(long nsString) {
        if (nsString == 0) {
            return null;
        }
        try {
            MemorySegment utf8 = (MemorySegment) msgSend(FunctionDescriptor.of(C_POINTER, C_LONG, C_LONG)).invokeExact(nsString, sel("UTF8String"));
            return FFMSupport.readCString(utf8);
        } catch (Throwable t) {
            throw rethrow(t);
        }
    }

    /** {@code -retain}: takes ownership of an object that would otherwise be autoreleased. */
    public static long retain(long object) {
        return send(object, "retain");
    }

    /** {@code -release}: gives up ownership taken by {@code new}/{@code alloc}/{@code copy} or {@link #retain}. */
    public static void release(long object) {
        sendVoid(object, "release");
    }

    /**
     * An {@code @autoreleasepool} block, as a try-with-resources scope.
     *
     * <p>
     * Many Metal methods hand back autoreleased objects. Without a pool on the calling thread those
     * accumulate for the life of the process, which on a per-dispatch path is a leak in all but
     * name. Anything that must outlive the pool has to be {@link #retain}ed inside it.
     */
    public static final class AutoreleasePool implements AutoCloseable {

        private final long pool;

        public AutoreleasePool() {
            long poolClass = objc_getClass("NSAutoreleasePool");
            this.pool = poolClass == 0 ? 0 : send(send(poolClass, "alloc"), "init");
        }

        @Override
        public void close() {
            if (pool != 0) {
                sendVoid(pool, "drain");
            }
        }
    }

    /** Convenience for the common {@code (id, SEL) -> id} shape. */
    public static FunctionDescriptor returningObject(MemoryLayout... arguments) {
        MemoryLayout[] all = new MemoryLayout[arguments.length + 2];
        all[0] = C_LONG;
        all[1] = C_LONG;
        System.arraycopy(arguments, 0, all, 2, arguments.length);
        return FunctionDescriptor.of(C_LONG, all);
    }

    /** Convenience for the common {@code (id, SEL) -> void} shape. */
    public static FunctionDescriptor returningVoid(MemoryLayout... arguments) {
        MemoryLayout[] all = new MemoryLayout[arguments.length + 2];
        all[0] = C_LONG;
        all[1] = C_LONG;
        System.arraycopy(arguments, 0, all, 2, arguments.length);
        return FunctionDescriptor.ofVoid(all);
    }

    /** Convenience for a selector returning a 32-bit integer. */
    public static FunctionDescriptor returningInt(MemoryLayout... arguments) {
        MemoryLayout[] all = new MemoryLayout[arguments.length + 2];
        all[0] = C_LONG;
        all[1] = C_LONG;
        System.arraycopy(arguments, 0, all, 2, arguments.length);
        return FunctionDescriptor.of(C_INT, all);
    }
}
