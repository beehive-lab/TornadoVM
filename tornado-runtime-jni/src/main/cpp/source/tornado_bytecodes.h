/*
 * MIT License
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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

#ifndef TORNADO_BYTECODES_H
#define TORNADO_BYTECODES_H

#include <stdint.h>

/*
 * TornadoVM bytecode opcodes.
 *
 * These values MUST be kept in sync with
 * tornado-runtime/src/main/java/uk/ac/manchester/tornado/runtime/graph/TornadoVMBytecodes.java
 *
 * The underlying type is fixed to uint8_t so that casting an arbitrary byte read out of the
 * bytecode buffer to this type is well defined even when that byte is not a known opcode.
 * Such a value falls through to the default label of every switch below.
 */
enum class TornadoBytecode : uint8_t {
    ALLOC = 10,
    TRANSFER_HOST_TO_DEVICE_ONCE = 11,
    TRANSFER_HOST_TO_DEVICE_ALWAYS = 12,
    TRANSFER_DEVICE_TO_HOST_ALWAYS = 13,
    TRANSFER_DEVICE_TO_HOST_ALWAYS_BLOCKING = 14,
    LAUNCH = 15,
    BARRIER = 16,
    INIT = 17,
    BEGIN = 18,
    ADD_DEPENDENCY = 19,
    CONTEXT = 20,
    END = 21,
    PUSH_CONSTANT_ARGUMENT = 22,
    PUSH_REFERENCE_ARGUMENT = 23,
    DEALLOC = 24,
    ON_DEVICE = 25,
    PERSIST = 26,
    CUDA_GRAPH_BEGIN_CAPTURE = 27,
    CUDA_GRAPH_END_CAPTURE = 28,
    CUDA_GRAPH_LAUNCH = 29,
    CUDA_GRAPH_DESTROY = 30
};

#endif /* TORNADO_BYTECODES_H */
