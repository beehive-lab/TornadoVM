/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package uk.ac.manchester.tornado.drivers.spirv.levelzero;

/**
 * Supported memory advice hints.
 */
public class ZeMemoryAdvice {

    /**
     * Hint that memory will be read from frequently and written to rarely
     */
    public static final int ZE_MEMORY_ADVICE_SET_READ_MOSTLY = 0;

    /**
     * Removes the affect of {@link ZeMemoryAdvice.ZE_MEMORY_ADVICE_SET_READ_MOSTLY}
     */
    public static final int ZE_MEMORY_ADVICE_CLEAR_READ_MOSTLY = 1;

    /**
     * Hint that the preferred memory location is the specified device
     */
    public static final int ZE_MEMORY_ADVICE_SET_PREFERRED_LOCATION = 2;

    /**
     * Removes the affect of {@link ZeMemoryAdvice.ZE_MEMORY_ADVICE_SET_PREFERRED_LOCATION}
     */
    public static final int ZE_MEMORY_ADVICE_CLEAR_PREFERRED_LOCATION = 3;

    /**
     * Hints that memory will mostly be accessed non-atomically
     */
    public static final int ZE_MEMORY_ADVICE_SET_NON_ATOMIC_MOSTLY = 4;

    /**
     * Removes the affect of {@link ZeMemoryAdvice.ZE_MEMORY_ADVICE_SET_NON_ATOMIC_MOSTLY}
     */
    public static final int ZE_MEMORY_ADVICE_CLEAR_NON_ATOMIC_MOSTLY = 5;

    /**
     * Hints that memory should be cached
     */
    public static final int ZE_MEMORY_ADVICE_BIAS_CACHED = 6;

    /**
     * Hints that memory should be not be cached
     */
    public static final int ZE_MEMORY_ADVICE_BIAS_UNCACHED = 7;

    public static final int ZE_MEMORY_ADVICE_FORCE_UINT32 = 0x7fffffff;
}
