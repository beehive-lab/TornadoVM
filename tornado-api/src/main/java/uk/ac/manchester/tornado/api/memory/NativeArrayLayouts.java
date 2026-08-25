/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package uk.ac.manchester.tornado.api.memory;

import java.util.Map;
import java.util.OptionalInt;

import uk.ac.manchester.tornado.api.types.arrays.BFloat16Array;
import uk.ac.manchester.tornado.api.types.arrays.ByteArray;
import uk.ac.manchester.tornado.api.types.arrays.CharArray;
import uk.ac.manchester.tornado.api.types.arrays.DoubleArray;
import uk.ac.manchester.tornado.api.types.arrays.FP8Array;
import uk.ac.manchester.tornado.api.types.arrays.FloatArray;
import uk.ac.manchester.tornado.api.types.arrays.HalfFloatArray;
import uk.ac.manchester.tornado.api.types.arrays.Int8Array;
import uk.ac.manchester.tornado.api.types.arrays.IntArray;
import uk.ac.manchester.tornado.api.types.arrays.LongArray;
import uk.ac.manchester.tornado.api.types.arrays.ShortArray;
import uk.ac.manchester.tornado.api.types.arrays.TornadoNativeArray;

/**
 * Element width, in bytes, of each {@link TornadoNativeArray} type that a backend can hold in a
 * memory-segment buffer.
 *
 * <p>Every backend used to carry its own {@code instanceof} chain answering this question, and the
 * chains drifted: the CUDA one listed thirteen types where OpenCL and Metal listed eleven, so a
 * {@code BFloat16Array} or an {@code FP8Array} that worked on one backend fell off the end of the
 * chain on another and was silently handed to a field buffer: a wrapper with a different memory
 * layout, which copies the wrong bytes rather than failing. One table means a new array type is
 * registered once and is either supported everywhere or nowhere.
 *
 * <p>It lives beside {@link XPUBuffer} because it describes how a buffer lays an array out, and
 * every backend consumes it; it is not part of the user-facing API surface.
 *
 * <p>The widths are storage widths, not codegen support. A backend that has no kernel-side type for
 * an entry still fails where it always failed, in compilation, instead of mis-copying at runtime.
 */
public final class NativeArrayLayouts {

    private static final Map<Class<?>, Integer> ELEMENT_SIZES = Map.ofEntries(//
            Map.entry(IntArray.class, Integer.BYTES), //
            Map.entry(FloatArray.class, Float.BYTES), //
            Map.entry(DoubleArray.class, Double.BYTES), //
            Map.entry(LongArray.class, Long.BYTES), //
            Map.entry(ShortArray.class, Short.BYTES), //
            Map.entry(ByteArray.class, Byte.BYTES), //
            // One byte, deliberately, and NOT what CharArray.getElementSize() reports (2). Every
            // backend has always sized a CharArray buffer with its 1-byte character kind, so the
            // table preserves that; the disagreement between the API type and the backends is a
            // pre-existing question, not something to settle inside a refactor. TestNativeArrayLayouts
            // pins both values so the day someone changes either, a test says so.
            Map.entry(CharArray.class, Byte.BYTES), //
            Map.entry(HalfFloatArray.class, Short.BYTES), //
            Map.entry(BFloat16Array.class, Short.BYTES), //
            Map.entry(Int8Array.class, Byte.BYTES), //
            Map.entry(FP8Array.class, Byte.BYTES));

    private NativeArrayLayouts() {
    }

    /**
     * The element width of {@code object}, or empty if it is not a native array type this table
     * knows. The types are final, so an exact class lookup is exhaustive.
     *
     * @param object
     *     Candidate host object.
     * @return element width in bytes, or empty when the object is not a registered native array.
     */
    public static OptionalInt elementSizeOf(Object object) {
        if (object == null) {
            return OptionalInt.empty();
        }
        Integer size = ELEMENT_SIZES.get(object.getClass());
        return size == null ? OptionalInt.empty() : OptionalInt.of(size);
    }

    /**
     * Whether a native array type is registered but the caller's backend has no entry for it. Used
     * by tests to keep the table and the backends in step.
     *
     * @return the registered types.
     */
    public static Iterable<Class<?>> registeredTypes() {
        return ELEMENT_SIZES.keySet();
    }
}
