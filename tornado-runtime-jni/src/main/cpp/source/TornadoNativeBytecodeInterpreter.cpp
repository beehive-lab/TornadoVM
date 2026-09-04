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

#include <jni.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "TornadoNativeBytecodeInterpreter.h"
#include "tornado_backend.h"
#include "tornado_interpreter.h"

static_assert(sizeof(jlong) == sizeof(int64_t), "JNI jlong must be 64-bit on every platform TornadoVM builds");
static_assert(sizeof(jint) == sizeof(int32_t), "JNI jint must be 32-bit on every platform TornadoVM builds");
static_assert(sizeof(jbyte) == 1, "JNI jbyte must be 8-bit");

/*
 * Get*ArrayElements rather than GetPrimitiveArrayCritical: ADD_DEPENDENCY may
 * allocate an event-list row, and a critical pin forbids other JNI calls.
 */
enum TornadoPinKind { PIN_BYTE, PIN_INT, PIN_LONG, PIN_SHORT, PIN_CHAR, PIN_FLOAT, PIN_DOUBLE };

struct TornadoPin {
    jarray array;
    void *raw;
    TornadoPinKind kind;
    jint mode;
};

struct TornadoPinnedArrays {
    JNIEnv *env;
    TornadoPin *pins;
    int count;
    int cap;
};

struct TornadoEnsureRowState {
    JNIEnv *env;
    jobjectArray events;
    int32_t **rows;
    int32_t rowCount;
    int32_t rowLength;
    TornadoPinnedArrays *pins;
};

struct TornadoHostPointerState {
    JNIEnv *env;
    jobjectArray objects;
    const uint8_t *kinds;
    int64_t *pointers;
    int32_t count;
    TornadoPinnedArrays *pins;
};

struct TornadoBackendState {
    TornadoBackendOperations *operations;
};

// Calls the native equivalent of XPUBuffer.enqueueWrite/enqueueRead.
static int copy_buffer(void *user, bool toDevice, bool blocking, int64_t buffer, int64_t deviceOffset, int64_t bytes, int64_t hostPtr, int64_t hostOffset) {
    TornadoBackendState *state = (TornadoBackendState *) user;
    if (state == NULL || state->operations == nullptr || bytes <= 0) {
        return TORNADO_COPY_UNSUPPORTED;
    }
    return state->operations->copy(toDevice, blocking, buffer, deviceOffset, bytes, hostPtr, hostOffset);
}

// Calls the native equivalent of TornadoBufferProvider.allocateBuffer.
static int allocate_buffer(void *user, int64_t bytes, int32_t access, int64_t *handle) {
    TornadoBackendState *state = (TornadoBackendState *) user;
    return state == NULL || state->operations == nullptr ? TORNADO_COPY_UNSUPPORTED : state->operations->allocate(bytes, access, handle);
}

// Applies the Java CUDA provider's preparation when a cached buffer is reused.
static int prepare_reused_buffer(void *user, int64_t handle, int64_t bytes, int32_t access) {
    TornadoBackendState *state = (TornadoBackendState *) user;
    return state == NULL || state->operations == nullptr ? TORNADO_COPY_UNSUPPORTED : state->operations->prepareReusedAllocation(handle, bytes, access);
}

// Calls the native equivalent of TornadoBufferProvider.releaseBuffer.
static int release_buffer(void *user, int64_t handle) {
    TornadoBackendState *state = (TornadoBackendState *) user;
    return state == NULL || state->operations == nullptr ? TORNADO_COPY_UNSUPPORTED : state->operations->release(handle);
}

static int set_kernel_argument(void *user, int64_t kernel, int32_t index, int32_t kind, const void *value, int64_t bytes) {
    TornadoBackendState *state = (TornadoBackendState *) user;
    return state == NULL || state->operations == nullptr ? TORNADO_COPY_UNSUPPORTED : state->operations->setKernelArgument(kernel, index, kind, value, bytes);
}

static int launch_kernel(void *user, int64_t kernel, int32_t dimensions, const int64_t *globalOffset, const int64_t *globalWork, const int64_t *localWork) {
    TornadoBackendState *state = (TornadoBackendState *) user;
    return state == NULL || state->operations == nullptr ? TORNADO_COPY_UNSUPPORTED : state->operations->launchKernel(kernel, dimensions, globalOffset, globalWork, localWork);
}

static int flush_backend(void *user) {
    TornadoBackendState *state = (TornadoBackendState *) user;
    return state == NULL || state->operations == nullptr ? TORNADO_COPY_UNSUPPORTED : state->operations->flush();
}

// Converts native validation failures into the same Java exception types used at the boundary.
static void throw_java_exception(JNIEnv *env, const char *name, const char *message) {
    jclass clazz = env->FindClass(name);
    if (clazz != NULL) {
        env->ThrowNew(clazz, message);
        env->DeleteLocalRef(clazz);
    }
}

// Starts the JNI-only list of Java arrays held for one native interpreter call.
static void pinned_arrays_init(TornadoPinnedArrays *pins, JNIEnv *env) {
    pins->env = env;
    pins->pins = NULL;
    pins->count = 0;
    pins->cap = 0;
}

// Releases every Java array pinned for the native interpreter call.
static void pinned_arrays_release(TornadoPinnedArrays *pins) {
    for (int i = pins->count - 1; i >= 0; i--) {
        TornadoPin *p = &pins->pins[i];
        switch (p->kind) {
            case PIN_BYTE:
                pins->env->ReleaseByteArrayElements((jbyteArray) p->array, (jbyte *) p->raw, p->mode);
                break;
            case PIN_INT:
                pins->env->ReleaseIntArrayElements((jintArray) p->array, (jint *) p->raw, p->mode);
                break;
            case PIN_LONG:
                pins->env->ReleaseLongArrayElements((jlongArray) p->array, (jlong *) p->raw, p->mode);
                break;
            case PIN_SHORT:
                pins->env->ReleaseShortArrayElements((jshortArray) p->array, (jshort *) p->raw, p->mode);
                break;
            case PIN_CHAR:
                pins->env->ReleaseCharArrayElements((jcharArray) p->array, (jchar *) p->raw, p->mode);
                break;
            case PIN_FLOAT:
                pins->env->ReleaseFloatArrayElements((jfloatArray) p->array, (jfloat *) p->raw, p->mode);
                break;
            case PIN_DOUBLE:
                pins->env->ReleaseDoubleArrayElements((jdoubleArray) p->array, (jdouble *) p->raw, p->mode);
                break;
        }
    }
    free(pins->pins);
    pins->pins = NULL;
    pins->count = 0;
    pins->cap = 0;
}

// Grows the JNI-only pin list; there is no Java interpreter counterpart.
static bool pinned_arrays_grow(TornadoPinnedArrays *pins) {
    int cap = pins->cap == 0 ? 16 : pins->cap * 2;
    TornadoPin *grown = (TornadoPin *) realloc(pins->pins, (size_t) cap * sizeof(TornadoPin));
    if (grown == NULL) {
        pinned_arrays_release(pins);
        throw_java_exception(pins->env, "java/lang/OutOfMemoryError", "could not grow interpreter pin list");
        return false;
    }
    pins->pins = grown;
    pins->cap = cap;
    return true;
}

/*
 * Pins `array` and returns the raw pointer. On failure, previously pinned arrays are
 * released and a Java exception is pending. `mode` is passed to the matching Release*.
 */
static void *pin_array(TornadoPinnedArrays *pins, jarray array, TornadoPinKind kind, jint mode) {
    if (array == NULL) {
        return NULL;
    }
    if (pins->count >= pins->cap && !pinned_arrays_grow(pins)) {
        return NULL;
    }
    void *raw = NULL;
    switch (kind) {
        case PIN_BYTE:
            raw = pins->env->GetByteArrayElements((jbyteArray) array, NULL);
            break;
        case PIN_INT:
            raw = pins->env->GetIntArrayElements((jintArray) array, NULL);
            break;
        case PIN_LONG:
            raw = pins->env->GetLongArrayElements((jlongArray) array, NULL);
            break;
        case PIN_SHORT:
            raw = pins->env->GetShortArrayElements((jshortArray) array, NULL);
            break;
        case PIN_CHAR:
            raw = pins->env->GetCharArrayElements((jcharArray) array, NULL);
            break;
        case PIN_FLOAT:
            raw = pins->env->GetFloatArrayElements((jfloatArray) array, NULL);
            break;
        case PIN_DOUBLE:
            raw = pins->env->GetDoubleArrayElements((jdoubleArray) array, NULL);
            break;
    }
    if (raw == NULL) {
        pinned_arrays_release(pins);
        throw_java_exception(pins->env, "java/lang/OutOfMemoryError", "could not pin an interpreter array");
        return NULL;
    }
    TornadoPin *p = &pins->pins[pins->count];
    p->array = array;
    p->raw = raw;
    p->kind = kind;
    p->mode = mode;
    pins->count++;
    return raw;
}

// Returns a table length, matching Java's null-as-empty table convention.
static int32_t array_length_or_zero(JNIEnv *env, jarray array) {
    return array == NULL ? 0 : (int32_t) env->GetArrayLength(array);
}

// Native counterpart of TornadoVMInterpreter.waitListForWrite().
static int32_t *ensure_event_row(void *user, int32_t eventId) {
    TornadoEnsureRowState *state = (TornadoEnsureRowState *) user;
    if (eventId < 0 || eventId >= state->rowCount || state->events == NULL) {
        return NULL;
    }
    if (state->rows[eventId] != NULL) {
        return state->rows[eventId];
    }
    jintArray row = state->env->NewIntArray(state->rowLength);
    if (row == NULL) {
        return NULL;
    }
    state->env->SetObjectArrayElement(state->events, eventId, row);
    void *raw = pin_array(state->pins, row, PIN_INT, 0);
    if (raw == NULL) {
        return NULL;
    }
    memset(raw, 0xFF, (size_t) state->rowLength * sizeof(int32_t));
    state->rows[eventId] = (int32_t *) raw;
    return state->rows[eventId];
}

// Native counterpart of an array transfer obtaining its Java primitive storage.
static int64_t resolve_host_pointer(void *user, int32_t objectIndex) {
    TornadoHostPointerState *state = (TornadoHostPointerState *) user;
    if (state == NULL || objectIndex < 0 || objectIndex >= state->count) {
        return 0;
    }
    if (state->pointers[objectIndex] != 0) {
        return state->pointers[objectIndex];
    }
    TornadoPinKind pinKind;
    switch (state->kinds[objectIndex]) {
        case TORNADO_OBJECT_KIND_BYTE_ARRAY: pinKind = PIN_BYTE; break;
        case TORNADO_OBJECT_KIND_CHAR_ARRAY: pinKind = PIN_CHAR; break;
        case TORNADO_OBJECT_KIND_SHORT_ARRAY: pinKind = PIN_SHORT; break;
        case TORNADO_OBJECT_KIND_INT_ARRAY: pinKind = PIN_INT; break;
        case TORNADO_OBJECT_KIND_LONG_ARRAY: pinKind = PIN_LONG; break;
        case TORNADO_OBJECT_KIND_FLOAT_ARRAY: pinKind = PIN_FLOAT; break;
        case TORNADO_OBJECT_KIND_DOUBLE_ARRAY: pinKind = PIN_DOUBLE; break;
        default: return 0;
    }
    jarray array = (jarray) state->env->GetObjectArrayElement(state->objects, objectIndex);
    if (array == NULL) {
        return 0;
    }
    void *raw = pin_array(state->pins, array, pinKind, 0);
    if (raw == NULL) {
        return 0;
    }
    state->pointers[objectIndex] = (int64_t) (uintptr_t) raw;
    return state->pointers[objectIndex];
}

// Pins an optional Java table only when it has entries.
static void *pin_or_null(TornadoPinnedArrays *pins, jarray array, TornadoPinKind kind, jint mode, int32_t length) {
    if (length <= 0 || array == NULL) {
        return NULL;
    }
    return pin_array(pins, array, kind, mode);
}

/*
 * Class:     uk_ac_manchester_tornado_runtime_interpreter_NativeBytecodeInterpreter
 * Method:    execute
 * Signature: ([BIII[J[J[J[J[J[J[J[BJJIIIJ[I[I[[II[B[B[Ljava/lang/Object;[B[J[J)J
 */
// JNI entry for NativeBytecodeInterpreter.execute().
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_runtime_interpreter_NativeBytecodeInterpreter_execute(JNIEnv *env, jclass clazz, jbyteArray bytecode, jint position, jint limit, jint flags,
        jlongArray bufferHandles, jlongArray bufferOffsets, jlongArray bufferSizes, jlongArray hostPointers, jlongArray kernelHandles, jlongArray programHandles, jlongArray launchMetadata,
        jbyteArray constants, jlong commandQueue, jlong deviceContext, jint backend, jint deviceIndex, jint platformIndex, jlong executionPlanId, jintArray lastEvent, jintArray eventsIndexes,
        jobjectArray events, jint eventRowLength, jbyteArray objectFlags, jbyteArray objectAccesses, jobjectArray objects, jbyteArray objectKinds, jlongArray dataOffsets, jlongArray partialCopySizes) {
    (void) clazz;

    if (bytecode == NULL) {
        throw_java_exception(env, "java/lang/NullPointerException", "bytecode buffer is null");
        return 0;
    }
    if (lastEvent == NULL) {
        throw_java_exception(env, "java/lang/NullPointerException", "lastEvent is null");
        return 0;
    }
    if (env->GetArrayLength(lastEvent) < 1) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "lastEvent must have length at least 1");
        return 0;
    }

    const jsize length = env->GetArrayLength(bytecode);
    if (limit < 0 || limit > length || position < 0 || position > limit) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "position and limit must satisfy 0 <= position <= limit <= bytecode.length");
        return 0;
    }

    const int32_t objectCount = array_length_or_zero(env, bufferHandles);
    const int32_t offsetCount = array_length_or_zero(env, bufferOffsets);
    const int32_t sizeCount = array_length_or_zero(env, bufferSizes);
    const int32_t hostCount = array_length_or_zero(env, hostPointers);
    const int32_t kernelCount = array_length_or_zero(env, kernelHandles);
    const int32_t programCount = array_length_or_zero(env, programHandles);
    const int32_t launchMetadataCount = array_length_or_zero(env, launchMetadata);
    const int32_t constantsBytes = array_length_or_zero(env, constants);
    const int32_t eventRowCount = array_length_or_zero(env, events);
    const int32_t eventIndexCount = array_length_or_zero(env, eventsIndexes);
    const int32_t objectFlagCount = array_length_or_zero(env, objectFlags);
    const int32_t objectAccessCount = array_length_or_zero(env, objectAccesses);
    const int32_t javaObjectCount = array_length_or_zero(env, objects);
    const int32_t objectKindCount = array_length_or_zero(env, objectKinds);
    const int32_t dataOffsetCount = array_length_or_zero(env, dataOffsets);
    const int32_t partialCopyCount = array_length_or_zero(env, partialCopySizes);

    if (eventRowCount != eventIndexCount) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "events and eventsIndexes must have the same length");
        return 0;
    }
    if (programCount != kernelCount || launchMetadataCount != kernelCount * TORNADO_LAUNCH_META_STRIDE) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "kernel, program, and launch metadata tables do not match");
        return 0;
    }
    if (eventRowCount > 0 && eventRowLength <= 0) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "eventRowLength must be positive when event lists are present");
        return 0;
    }
    if (objectFlagCount > 0 && objectFlagCount != objectCount) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "objectFlags must match bufferHandles length");
        return 0;
    }
    if (offsetCount != objectCount || sizeCount != objectCount || hostCount != objectCount || objectAccessCount != objectCount || javaObjectCount != objectCount || objectKindCount != objectCount
            || dataOffsetCount != objectCount || partialCopyCount != objectCount) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "all per-object native tables must have the same length");
        return 0;
    }

    TornadoPinnedArrays pins;
    pinned_arrays_init(&pins, env);

    int32_t **eventRows = NULL;
    if (eventRowCount > 0) {
        eventRows = (int32_t **) calloc((size_t) eventRowCount, sizeof(int32_t *));
        if (eventRows == NULL) {
            throw_java_exception(env, "java/lang/OutOfMemoryError", "could not allocate event-row pointers");
            return 0;
        }
    }

    void *bytecodeRaw = pin_or_null(&pins, bytecode, PIN_BYTE, JNI_ABORT, length);
    if (length > 0 && bytecodeRaw == NULL) {
        free(eventRows);
        return 0;
    }

    void *bufferHandlesRaw = pin_or_null(&pins, bufferHandles, PIN_LONG, 0, objectCount);
    if (objectCount > 0 && bufferHandlesRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *bufferOffsetsRaw = pin_or_null(&pins, bufferOffsets, PIN_LONG, JNI_ABORT, offsetCount);
    if (offsetCount > 0 && bufferOffsetsRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *bufferSizesRaw = pin_or_null(&pins, bufferSizes, PIN_LONG, JNI_ABORT, sizeCount);
    if (sizeCount > 0 && bufferSizesRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *hostPointersRaw = pin_or_null(&pins, hostPointers, PIN_LONG, JNI_ABORT, hostCount);
    if (hostCount > 0 && hostPointersRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *kernelHandlesRaw = pin_or_null(&pins, kernelHandles, PIN_LONG, JNI_ABORT, kernelCount);
    if (kernelCount > 0 && kernelHandlesRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *programHandlesRaw = pin_or_null(&pins, programHandles, PIN_LONG, JNI_ABORT, programCount);
    if (programCount > 0 && programHandlesRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *launchMetadataRaw = pin_or_null(&pins, launchMetadata, PIN_LONG, JNI_ABORT, launchMetadataCount);
    if (launchMetadataCount > 0 && launchMetadataRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *constantsRaw = pin_or_null(&pins, constants, PIN_BYTE, JNI_ABORT, constantsBytes);
    if (constantsBytes > 0 && constantsRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *objectFlagsRaw = pin_or_null(&pins, objectFlags, PIN_BYTE, 0, objectFlagCount);
    if (objectFlagCount > 0 && objectFlagsRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *objectAccessesRaw = pin_or_null(&pins, objectAccesses, PIN_BYTE, JNI_ABORT, objectAccessCount);
    if (objectAccessCount > 0 && objectAccessesRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *objectKindsRaw = pin_or_null(&pins, objectKinds, PIN_BYTE, JNI_ABORT, objectKindCount);
    void *dataOffsetsRaw = pin_or_null(&pins, dataOffsets, PIN_LONG, JNI_ABORT, dataOffsetCount);
    void *partialCopyRaw = pin_or_null(&pins, partialCopySizes, PIN_LONG, JNI_ABORT, partialCopyCount);
    if (objectCount > 0 && (objectKindsRaw == NULL || dataOffsetsRaw == NULL || partialCopyRaw == NULL)) {
        free(eventRows);
        return 0;
    }

    void *lastEventRaw = pin_array(&pins, lastEvent, PIN_INT, 0);
    if (lastEventRaw == NULL) {
        free(eventRows);
        return 0;
    }
    void *eventIndexesRaw = pin_or_null(&pins, eventsIndexes, PIN_INT, 0, eventIndexCount);
    if (eventIndexCount > 0 && eventIndexesRaw == NULL) {
        free(eventRows);
        return 0;
    }

    if (env->EnsureLocalCapacity(eventRowCount + objectCount + 32) != 0) {
        pinned_arrays_release(&pins);
        free(eventRows);
        return 0;
    }

    for (int32_t i = 0; i < eventRowCount; i++) {
        jintArray row = (jintArray) env->GetObjectArrayElement(events, i);
        if (row == NULL) {
            continue;
        }
        if (env->GetArrayLength(row) < eventRowLength) {
            env->DeleteLocalRef(row);
            pinned_arrays_release(&pins);
            free(eventRows);
            throw_java_exception(env, "java/lang/IllegalArgumentException", "event row is shorter than eventRowLength");
            return 0;
        }
        void *rowRaw = pin_array(&pins, row, PIN_INT, 0);
        if (rowRaw == NULL) {
            free(eventRows);
            return 0;
        }
        eventRows[i] = (int32_t *) rowRaw;
    }

    int64_t *resolvedHostPointers = NULL;
    if (objectCount > 0) {
        resolvedHostPointers = (int64_t *) calloc((size_t) objectCount, sizeof(int64_t));
        if (resolvedHostPointers == NULL) {
            pinned_arrays_release(&pins);
            free(eventRows);
            throw_java_exception(env, "java/lang/OutOfMemoryError", "could not allocate resolved host-pointer table");
            return 0;
        }
        memcpy(resolvedHostPointers, hostPointersRaw, (size_t) objectCount * sizeof(int64_t));
    }

    TornadoHostPointerState hostPointerState;
    hostPointerState.env = env;
    hostPointerState.objects = objects;
    hostPointerState.kinds = (const uint8_t *) objectKindsRaw;
    hostPointerState.pointers = resolvedHostPointers;
    hostPointerState.count = objectCount;
    hostPointerState.pins = &pins;

    TornadoEnsureRowState ensureState;
    ensureState.env = env;
    ensureState.events = events;
    ensureState.rows = eventRows;
    ensureState.rowCount = eventRowCount;
    ensureState.rowLength = (int32_t) eventRowLength;
    ensureState.pins = &pins;

    std::unique_ptr<TornadoBackendOperations> operations = tornado_create_backend((int32_t) backend, (int64_t) commandQueue, (int64_t) deviceContext);
    TornadoBackendState backendState;
    backendState.operations = operations.get();

    TornadoInterpreterContext ctx = {};
    ctx.code = (const uint8_t *) bytecodeRaw;
    ctx.position = (int32_t) position;
    ctx.limit = (int32_t) limit;
    ctx.flags = (int32_t) flags;
    ctx.buffer_handles = (int64_t *) bufferHandlesRaw;
    ctx.buffer_offsets = (const int64_t *) bufferOffsetsRaw;
    ctx.buffer_sizes = (const int64_t *) bufferSizesRaw;
    ctx.host_pointers = resolvedHostPointers;
    ctx.resolve_host_pointer = resolve_host_pointer;
    ctx.resolve_host_pointer_user = &hostPointerState;
    ctx.data_offsets = (const int64_t *) dataOffsetsRaw;
    ctx.partial_copy_sizes = (const int64_t *) partialCopyRaw;
    ctx.object_kinds = (const uint8_t *) objectKindsRaw;
    ctx.object_count = objectCount;
    ctx.kernel_handles = (const int64_t *) kernelHandlesRaw;
    ctx.program_handles = (const int64_t *) programHandlesRaw;
    ctx.launch_metadata = (const int64_t *) launchMetadataRaw;
    ctx.kernel_count = kernelCount;
    ctx.constants = (const uint8_t *) constantsRaw;
    ctx.constants_bytes = constantsBytes;
    ctx.command_queue = (int64_t) commandQueue;
    ctx.device_context = (int64_t) deviceContext;
    ctx.backend = (int32_t) backend;
    ctx.device_index = (int32_t) deviceIndex;
    ctx.platform_index = (int32_t) platformIndex;
    ctx.execution_plan_id = (int64_t) executionPlanId;
    ctx.last_event = (int32_t *) lastEventRaw;
    ctx.event_indexes = (int32_t *) eventIndexesRaw;
    ctx.event_rows = eventRows;
    ctx.event_row_count = eventRowCount;
    ctx.event_row_length = (int32_t) eventRowLength;
    ctx.ensure_event_row = ensure_event_row;
    ctx.ensure_event_row_user = &ensureState;
    ctx.object_flags = (uint8_t *) objectFlagsRaw;
    ctx.object_accesses = (const uint8_t *) objectAccessesRaw;
    ctx.allocate_buffer = allocate_buffer;
    ctx.prepare_reused_buffer = prepare_reused_buffer;
    ctx.release_buffer = release_buffer;
    ctx.copy_buffer = copy_buffer;
    ctx.set_kernel_argument = set_kernel_argument;
    ctx.launch_kernel = launch_kernel;
    ctx.flush_backend = flush_backend;
    ctx.backend_user = &backendState;

    const int64_t result = tornado_interpret(&ctx);

    pinned_arrays_release(&pins);
    free(resolvedHostPointers);
    free(eventRows);

    if (env->ExceptionCheck()) {
        return 0;
    }
    return (jlong) result;
}
