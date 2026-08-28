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

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Shared plumbing for the CUDA backend's Panama (java.lang.foreign) bindings, which replace the
 * hand-written {@code libtornado-cuda} JNI library.
 *
 * <p>
 * The source of this class has to compile twice: once under {@code -source 21 --enable-preview},
 * where FFM is a preview API, and once under {@code --release 22}, where it is final. Only the
 * intersection of the two API surfaces may be used here, which rules out the methods that were
 * renamed in 22 ({@code allocateUtf8String} became {@code allocateFrom}, {@code getUtf8String}
 * became {@code getString}, {@code allocateArray} became an {@code allocate} overload). The
 * C-string and array helpers below exist so that no call site has to care.
 */
public final class FFMSupport {

    public static final Linker LINKER = Linker.nativeLinker();

    public static final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT;
    public static final AddressLayout C_POINTER = ValueLayout.ADDRESS;

    /**
     * Native memory that lives for the whole VM: symbol lookups and the boxed handles the Java
     * layer keeps addressing by {@code long}.
     */
    public static final Arena GLOBAL = Arena.global();

    private FFMSupport() {
    }

    /**
     * Opens the first of {@code sonames} that dlopen accepts. The candidates are tried in order so
     * that a caller can ask for the versioned soname first ({@code libcuda.so.1}), which is the one
     * a driver installation is guaranteed to ship; the unversioned name only exists when the
     * development package is installed.
     *
     * @return the lookup, or {@code null} if none of the candidates could be loaded.
     */
    public static SymbolLookup loadLibrary(String... sonames) {
        for (String soname : sonames) {
            try {
                return SymbolLookup.libraryLookup(soname, GLOBAL);
            } catch (IllegalArgumentException | IllegalCallerException e) {
                // Not present under this name; fall through to the next candidate.
            }
        }
        return null;
    }

    /**
     * Resolves the first of {@code candidates} the library exports.
     *
     * <p>
     * CUDA's headers rename most driver entry points to a versioned symbol ({@code cuCtxCreate} is
     * a macro for {@code cuCtxCreate_v2}), and the unversioned symbol is still exported with the
     * OLD, incompatible ABI. Every binding therefore names the versioned symbol first and the plain
     * one only as a last resort, which is what a C compilation would have linked against.
     */
    public static MemorySegment findSymbol(SymbolLookup lookup, String... candidates) {
        for (String candidate : candidates) {
            MemorySegment symbol = lookup.find(candidate).orElse(null);
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    /**
     * Builds a downcall handle for the first exported symbol among {@code candidates}, or returns
     * {@code null} when the library exports none of them. A missing symbol is not fatal by itself:
     * entry points that only exist from a given CUDA version onwards are resolved optionally and
     * the caller degrades instead of failing to load the backend.
     */
    public static MethodHandle downcall(SymbolLookup lookup, FunctionDescriptor descriptor, String... candidates) {
        MemorySegment symbol = findSymbol(lookup, candidates);
        return symbol == null ? null : LINKER.downcallHandle(symbol, descriptor);
    }

    /** Allocates a NUL-terminated copy of {@code value}. */
    public static MemorySegment allocateCString(SegmentAllocator allocator, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        MemorySegment segment = allocator.allocate(bytes.length + 1L, 1);
        MemorySegment.copy(bytes, 0, segment, C_CHAR, 0, bytes.length);
        segment.set(C_CHAR, bytes.length, (byte) 0);
        return segment;
    }

    /**
     * Reads a NUL-terminated string from a pointer the native side owns. The pointer arrives from a
     * downcall with zero size, so it is reinterpreted before being walked.
     */
    public static String readCString(MemorySegment pointer) {
        if (pointer == null || pointer.equals(MemorySegment.NULL)) {
            return null;
        }
        MemorySegment unbounded = pointer.byteSize() == 0 ? pointer.reinterpret(Long.MAX_VALUE) : pointer;
        long length = 0;
        while (unbounded.get(C_CHAR, length) != 0) {
            length++;
        }
        byte[] bytes = new byte[(int) length];
        MemorySegment.copy(unbounded, C_CHAR, 0, bytes, 0, bytes.length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Reads a NUL-terminated string that sits at the start of an already-sized segment. */
    public static String readCString(MemorySegment segment, long maxBytes) {
        long length = 0;
        while (length < maxBytes && segment.get(C_CHAR, length) != 0) {
            length++;
        }
        byte[] bytes = new byte[(int) length];
        MemorySegment.copy(segment, C_CHAR, 0, bytes, 0, bytes.length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Allocates room for {@code count} elements of {@code layout}, naturally aligned. */
    public static MemorySegment allocateArray(SegmentAllocator allocator, MemoryLayout layout, long count) {
        return allocator.allocate(layout.byteSize() * count, layout.byteAlignment());
    }

    /** Allocates a single pointer-sized out-parameter, zeroed. */
    public static MemorySegment allocatePointer(SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(C_POINTER.byteSize(), C_POINTER.byteAlignment());
        segment.set(C_POINTER, 0, MemorySegment.NULL);
        return segment;
    }

    /** Allocates a single {@code int} out-parameter, zeroed. */
    public static MemorySegment allocateInt(SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(C_INT.byteSize(), C_INT.byteAlignment());
        segment.set(C_INT, 0, 0);
        return segment;
    }

    /** Allocates a single {@code long}/{@code size_t} out-parameter, zeroed. */
    public static MemorySegment allocateLong(SegmentAllocator allocator) {
        MemorySegment segment = allocator.allocate(C_LONG.byteSize(), C_LONG.byteAlignment());
        segment.set(C_LONG, 0, 0L);
        return segment;
    }

    /** Copies {@code values} into a fresh native {@code long} array. */
    public static MemorySegment allocateLongArray(SegmentAllocator allocator, long[] values) {
        MemorySegment segment = allocateArray(allocator, C_LONG, Math.max(values.length, 1));
        MemorySegment.copy(values, 0, segment, C_LONG, 0, values.length);
        return segment;
    }

    /** Turns a raw address into a segment of the given size that can be read and written. */
    public static MemorySegment asSegment(long address, long byteSize) {
        return MemorySegment.ofAddress(address).reinterpret(byteSize);
    }
}
