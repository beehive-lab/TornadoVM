/*
 * MIT License
 *
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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
 * Base class for Descriptors in Level Zero.
 */
public abstract class LevelZeroDescriptor {

    /**
     * Type of the struct
     */
    protected int stype;

    /**
     * C++ pointer to the next struct
     */
    protected long pNext;

    /**
     * Pointer to its own data structure in the C++ part
     */
    protected long selfPtr;

    /**
     * Reference to the next descriptor in the Java side.
     */
    protected LevelZeroDescriptor next;

    protected LevelZeroDescriptor() {
        this.pNext = -1;
        this.next = null;
        this.selfPtr = -1;
    }

    public void setNext(LevelZeroDescriptor next) {
        this.next = next;

        // We need to materialize first before this assign
        this.pNext = next.selfPtr;
    }

    /**
     * The materialize method invoke the JNI to build the descriptor being used and
     * update the selfPtr with a pointer to its own descriptor struct.
     * This is useful when combining multiple descriptors (e.g., using extended
     * memory mode for device,host and shared allocations).
     */
    public abstract void materialize();
}
