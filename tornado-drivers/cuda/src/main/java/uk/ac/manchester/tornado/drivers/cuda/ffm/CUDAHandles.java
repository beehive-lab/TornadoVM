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
package uk.ac.manchester.tornado.drivers.cuda.ffm;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The opaque {@code long} handles the CUDA backend's Java layer passes around, and the state each
 * one stands for.
 *
 * <p>
 * The JNI implementation this replaces boxed CUDA primitives inside {@code malloc}'d C structs and
 * handed their addresses to Java as longs. That worked, but the state those structs carried -- a
 * kernel's packed arguments, a program's source, build log and compiled image -- is Java state that
 * had been pushed across the boundary, and every handle was a raw pointer the Java side could get
 * wrong without the JVM noticing. Here the state stays in Java objects and the handle is a plain
 * counter: a stale or fabricated handle resolves to {@code null} instead of dereferencing whatever
 * happens to be at that address.
 *
 * <p>
 * Handles start above zero because the Java layer already treats {@code 0} as "no handle".
 */
public final class CUDAHandles {

    private static final AtomicLong NEXT_HANDLE = new AtomicLong(1);

    private static final ConcurrentHashMap<Long, Object> OBJECTS = new ConcurrentHashMap<>();

    private CUDAHandles() {
    }

    /** Registers {@code object} and returns the handle the Java layer will address it by. */
    public static long register(Object object) {
        long handle = NEXT_HANDLE.getAndIncrement();
        OBJECTS.put(handle, object);
        return handle;
    }

    /** Resolves a handle, or returns {@code null} if it was never registered or has been released. */
    @SuppressWarnings("unchecked")
    public static <T> T resolve(long handle, Class<T> type) {
        Object object = OBJECTS.get(handle);
        return type.isInstance(object) ? (T) object : null;
    }

    /** Drops a handle. Returns what it referred to so the caller can release native resources. */
    public static Object release(long handle) {
        return OBJECTS.remove(handle);
    }

    /** A CUDA device: the driver's {@code CUdevice} plus the ordinal it was enumerated at. */
    public record Device(int device, int ordinal) {
    }

    /** A CUDA context bound to one device. {@code context} is the raw {@code CUcontext}. */
    public record Context(long context, int device, int ordinal) {
    }

    /**
     * A command queue: one {@code CUstream} plus the context and device it belongs to.
     * {@code properties} carries the OpenCL-style queue property bits the Java layer created it
     * with, which is what tells the profiler whether the queue was asked to time its operations.
     */
    public record Queue(long stream, long context, int device, long properties) {
    }

    /**
     * An event pair. {@code event} is the completion event used for waits, queries and
     * dependencies; {@code start} is an optional timestamp recorded before the operation so that
     * {@code cuEventElapsedTime(start, event)} yields the operation's device time. {@code start} is
     * {@code 0} for events that do not bracket a timed operation, such as markers and barriers, and
     * their elapsed time is reported as zero.
     */
    public record Event(long event, long start) {
    }

    /** A program: CUDA C source, the image NVRTC produced for it, and the module it was loaded as. */
    public static final class Program {

        /** Matches the OpenCL {@code CL_BUILD_SUCCESS} the cloned Java enum expects. */
        public static final int BUILD_SUCCESS = 0;
        /** Matches the OpenCL {@code CL_BUILD_ERROR} the cloned Java enum expects. */
        public static final int BUILD_ERROR = -2;
        /** Matches the OpenCL {@code CL_BUILD_NONE} the cloned Java enum expects. */
        public static final int BUILD_NONE = -1;

        public final long context;
        public final String source;

        /** The loadable module image: an NVRTC cubin, NVRTC PTX, or a pre-supplied binary. */
        public byte[] binary;
        public String log = "";
        public int buildStatus = BUILD_NONE;
        public long module;
        public boolean moduleLoaded;

        public Program(long context, String source, byte[] binary) {
            this.context = context;
            this.source = source;
            this.binary = binary == null ? new byte[0] : binary;
        }
    }

    /** A kernel: a {@code CUfunction} plus the argument blobs staged for its next launch. */
    public static final class Kernel {

        public final long function;
        public final long module;
        public final String name;

        /**
         * One byte blob per argument index, in the order {@code cuLaunchKernel} expects them. A
         * blob is replaced wholesale when the argument is set again, and the vector is grown to fit
         * the highest index the caller has used.
         */
        public final List<byte[]> arguments = new ArrayList<>();

        public Kernel(long function, long module, String name) {
            this.function = function;
            this.module = module;
            this.name = name;
        }

        public void setArgument(int index, byte[] value) {
            while (arguments.size() <= index) {
                arguments.add(null);
            }
            arguments.set(index, value);
        }
    }
}
