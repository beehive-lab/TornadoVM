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
#include <stdbool.h>

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

/* One flattened per-task launch record. Must match NativeBytecodeInterpreter. */
enum {
    TORNADO_LAUNCH_META_STRIDE = 18,
    TORNADO_LAUNCH_META_FRAME_BUFFER = 0,
    TORNADO_LAUNCH_META_CONSTANT_BUFFER = 1,
    TORNADO_LAUNCH_META_ATOMIC_BUFFER = 2,
    TORNADO_LAUNCH_META_LOCAL_MEMORY = 3,
    TORNADO_LAUNCH_META_DIMENSIONS = 4,
    TORNADO_LAUNCH_META_FLAGS = 5,
    TORNADO_LAUNCH_META_CONTEXT = 6,
    TORNADO_LAUNCH_META_GLOBAL_OFFSET = 9,
    TORNADO_LAUNCH_META_GLOBAL_WORK = 12,
    TORNADO_LAUNCH_META_LOCAL_WORK = 15
};

enum TornadoLaunchFlags {
    TORNADO_LAUNCH_SUPPORTED = 1,
    TORNADO_LAUNCH_HAS_LOCAL_WORK = 2
};

enum TornadoKernelArgumentKind {
    TORNADO_KERNEL_ARG_VALUE = 0,
    TORNADO_KERNEL_ARG_REFERENCE = 1,
    TORNADO_KERNEL_ARG_LOCAL = 2
};

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
    int64_t *buffer_handles;
    const int64_t *buffer_offsets;
    const int64_t *buffer_sizes;
    /* Off-heap host address, or 0 for on-heap Java arrays / objects with no stable address. */
    const int64_t *host_pointers;
    /* Lazily pins an on-heap array when its first TRANSFER executes. */
    int64_t (*resolve_host_pointer)(void *user, int32_t object_index);
    void *resolve_host_pointer_user;
    const int64_t *data_offsets;
    const int64_t *partial_copy_sizes;
    const uint8_t *object_kinds;
    int32_t object_count;

    /* Per-task tables, indexed by the local task index on this device. */
    const int64_t *kernel_handles;
    const int64_t *program_handles;
    const int64_t *launch_metadata;
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

    /*
     * Event-list state, indexed by the event-list id in the bytecode. Writable.
     * `last_event` is the loop-carried event-pool id (Java `lastEvent`); never null.
     * A null `event_rows[i]` means that list has not been allocated yet.
     */
    int32_t *last_event;
    int32_t *event_indexes;
    int32_t **event_rows;
    int32_t event_row_count;
    int32_t event_row_length;

    /*
     * Allocates `events[eventId]` if it is still null (Java `waitListForWrite`), pins it,
     * stores the pointer in `event_rows[eventId]`, and returns that pointer. Returns
     * nullptr on failure; a Java exception is then pending.
     */
    int32_t *(*ensure_event_row)(void *user, int32_t eventId);
    void *ensure_event_row_user;

    /*
     * Per-object flags, indexed with the buffer tables. Writable: ONCE transfers
     * set HAS_CONTENT. Must match NativeBytecodeInterpreter.OBJ_*.
     */
    uint8_t *object_flags;
    const uint8_t *object_accesses;

    int (*allocate_buffer)(void *user, int64_t bytes, int32_t access, int64_t *handle);
    int (*prepare_reused_buffer)(void *user, int64_t handle, int64_t bytes, int32_t access);
    int (*release_buffer)(void *user, int64_t handle);

    /*
     * Device copy. OpenCL, CUDA and Metal issue clEnqueueWriteBuffer / cuMemcpy /
     * shared-buffer memcpy directly.
     * Returns TornadoCopyStatus. nullptr means this backend cannot perform the copy.
     */
    int (*copy_buffer)(void *user, bool toDevice, bool blocking, int64_t buffer, int64_t deviceOffset, int64_t bytes, int64_t hostPtr, int64_t hostOffset);
    int (*set_kernel_argument)(void *user, int64_t kernel, int32_t index, int32_t kind, const void *value, int64_t bytes);
    int (*launch_kernel)(void *user, int64_t kernel, int32_t dimensions, const int64_t *globalOffset, const int64_t *globalWork, const int64_t *localWork);
    int (*flush_backend)(void *user);
    void *backend_user;
};

/* Must match NativeBytecodeInterpreter.OBJ_*. */
enum TornadoObjectFlags {
    TORNADO_OBJ_HAS_CONTENT = 1,
    TORNADO_OBJ_LOCKED = 2,
    TORNADO_OBJ_KERNEL_CONTEXT = 4,
    TORNADO_OBJ_PERSISTENT = 8,
    TORNADO_OBJ_NATIVE_ALLOCATED = 16,
    TORNADO_OBJ_NATIVE_DEALLOCATED = 32,
    TORNADO_OBJ_NATIVE_ALLOCATION_PREPARED = 64,
    TORNADO_OBJ_STAGED_HOST_BUFFER = 128
};

enum TornadoCopyStatus {
    TORNADO_COPY_OK = 0,
    TORNADO_COPY_UNSUPPORTED = 1,
    TORNADO_COPY_FAILED = 2
};

enum TornadoObjectKind {
    TORNADO_OBJECT_KIND_UNSUPPORTED = 0,
    TORNADO_OBJECT_KIND_SEGMENT = 1,
    TORNADO_OBJECT_KIND_BYTE_ARRAY = 2,
    TORNADO_OBJECT_KIND_CHAR_ARRAY = 3,
    TORNADO_OBJECT_KIND_SHORT_ARRAY = 4,
    TORNADO_OBJECT_KIND_INT_ARRAY = 5,
    TORNADO_OBJECT_KIND_LONG_ARRAY = 6,
    TORNADO_OBJECT_KIND_FLOAT_ARRAY = 7,
    TORNADO_OBJECT_KIND_DOUBLE_ARRAY = 8,
    TORNADO_OBJECT_KIND_ATOMIC = 9
};

#endif /* TORNADO_CONTEXT_H */
