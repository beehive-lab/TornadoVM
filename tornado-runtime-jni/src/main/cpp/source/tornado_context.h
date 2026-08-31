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

#ifndef TORNADO_CONTEXT_H
#define TORNADO_CONTEXT_H

#include <stdint.h>

#include "tornado_decoder.h"

/*
 * The tables the native bytecode loop reads. Java resolves objects / constants 
 * to these arrays before each JNI call.
 *
 * These values MUST be kept in sync with
 * tornado-runtime/src/main/java/uk/ac/manchester/tornado/runtime/interpreter/NativeBytecodeInterpreter.java
 */

/* One constant: a type tag, then an 8-byte little-endian payload (unused high bytes are 0). */
enum { TORNADO_CONSTANT_ENTRY_BYTES = 9 };

enum TornadoConstantTag {
    TORNADO_CONSTANT_NONE = 0,
    TORNADO_CONSTANT_BYTE = 1,
    TORNADO_CONSTANT_CHAR = 2,
    TORNADO_CONSTANT_SHORT = 3,
    TORNADO_CONSTANT_INT = 4,
    TORNADO_CONSTANT_FLOAT = 5,
    TORNADO_CONSTANT_LONG = 6,
    TORNADO_CONSTANT_DOUBLE = 7,
    TORNADO_CONSTANT_HALF = 8
};

/* Must match TornadoVMBackendType declaration order. */
enum TornadoBackend {
    TORNADO_BACKEND_OPENCL = 0,
    TORNADO_BACKEND_METAL = 1,
    TORNADO_BACKEND_CUDA = 2,
    TORNADO_BACKEND_JAVA = 3,
    TORNADO_BACKEND_VIRTUAL = 4
};

struct TornadoConstant {
    uint8_t tag;
    uint64_t bits;
};

/*
 * Reads constant `index` out of a packed blob. Returns false and leaves `out`
 * untouched when the blob is malformed or `index` is out of range.
 */
static inline bool tornado_constant_at(const uint8_t *blob, int32_t byteLength, int32_t index, TornadoConstant *out) {
    if (blob == nullptr || out == nullptr || index < 0 || byteLength < 0) {
        return false;
    }
    if ((byteLength % TORNADO_CONSTANT_ENTRY_BYTES) != 0) {
        return false;
    }
    const int32_t count = byteLength / TORNADO_CONSTANT_ENTRY_BYTES;
    if (index >= count) {
        return false;
    }
    const uint8_t *p = blob + ((int64_t) index * TORNADO_CONSTANT_ENTRY_BYTES);
    TornadoConstant value;
    value.tag = p[0];
    value.bits = (uint64_t) tornado_read_i64(p, 1);
    *out = value;
    return true;
}

struct TornadoInterpreterContext {
    /* Bytecode stream. `position` is the opcode to decode next. */
    const uint8_t *code;
    int32_t position;
    int32_t limit;
    int32_t flags;

    /* Per-object tables, indexed by the object index in the bytecode. */
    const int64_t *buffer_handles;
    const int64_t *buffer_offsets;
    const int64_t *buffer_sizes;
    /* Off-heap host address, or 0 for on-heap Java arrays / objects with no stable address. */
    const int64_t *host_pointers;
    int32_t object_count;

    /* Per-task tables, indexed by the local task index on this device. */
    const int64_t *kernel_handles;
    const int64_t *program_handles;
    int32_t kernel_count;

    /* Packed constants blob (tag + 8-byte payload per entry). */
    const uint8_t *constants;
    int32_t constants_bytes;

    /* Backend-opaque device handles. 0 when the Java device does not expose them. */
    int64_t command_queue;
    int64_t device_context;
    /* TornadoVMBackendType.ordinal() of the Java device. */
    int32_t backend;
    int32_t device_index;
    int32_t platform_index;
    int64_t execution_plan_id;
};

#endif /* TORNADO_CONTEXT_H */
