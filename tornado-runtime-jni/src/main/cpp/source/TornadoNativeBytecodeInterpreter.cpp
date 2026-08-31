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
#include <stdint.h>

#include "TornadoNativeBytecodeInterpreter.h"
#include "tornado_interpreter.h"

static_assert(sizeof(jlong) == sizeof(int64_t), "JNI jlong must be 64-bit on every platform TornadoVM builds");
static_assert(sizeof(jint) == sizeof(int32_t), "JNI jint must be 32-bit on every platform TornadoVM builds");
static_assert(sizeof(jbyte) == 1, "JNI jbyte must be 8-bit");

enum { TORNADO_PINNED_ARRAY_CAP = 8 };

struct TornadoPinnedArrays {
    JNIEnv *env;
    jarray arrays[TORNADO_PINNED_ARRAY_CAP];
    void *raws[TORNADO_PINNED_ARRAY_CAP];
    int count;
};

static void throw_java_exception(JNIEnv *env, const char *name, const char *message) {
    jclass clazz = env->FindClass(name);
    if (clazz != NULL) {
        env->ThrowNew(clazz, message);
        env->DeleteLocalRef(clazz);
    }
}

static void pinned_arrays_init(TornadoPinnedArrays *pins, JNIEnv *env) {
    pins->env = env;
    pins->count = 0;
}

static void pinned_arrays_release(TornadoPinnedArrays *pins) {
    for (int i = pins->count - 1; i >= 0; i--) {
        pins->env->ReleasePrimitiveArrayCritical(pins->arrays[i], pins->raws[i], JNI_ABORT);
    }
    pins->count = 0;
}

/*
 * Pins `array` and returns the raw pointer.
 * On failure, previously pinned arrays are released and a Java exception is pending.
 */
static void *pin_array(TornadoPinnedArrays *pins, jarray array) {
    if (array == NULL) {
        return NULL;
    }
    if (pins->count >= TORNADO_PINNED_ARRAY_CAP) {
        pinned_arrays_release(pins);
        throw_java_exception(pins->env, "java/lang/IllegalStateException", "too many pinned arrays");
        return NULL;
    }
    void *raw = pins->env->GetPrimitiveArrayCritical(array, NULL);
    if (raw == NULL) {
        pinned_arrays_release(pins);
        throw_java_exception(pins->env, "java/lang/OutOfMemoryError", "could not pin an interpreter array");
        return NULL;
    }
    pins->arrays[pins->count] = array;
    pins->raws[pins->count] = raw;
    pins->count++;
    return raw;
}

static int32_t array_length_or_zero(JNIEnv *env, jarray array) {
    return array == NULL ? 0 : (int32_t) env->GetArrayLength(array);
}

/*
 * Class:     uk_ac_manchester_tornado_runtime_interpreter_NativeBytecodeInterpreter
 * Method:    execute
 * Signature: ([BIII[J[J[J[J[J[J[BJJIIIJ)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_runtime_interpreter_NativeBytecodeInterpreter_execute(JNIEnv *env, jclass clazz, jbyteArray bytecode, jint position, jint limit, jint flags,
        jlongArray bufferHandles, jlongArray bufferOffsets, jlongArray bufferSizes, jlongArray hostPointers, jlongArray kernelHandles, jlongArray programHandles, jbyteArray constants, jlong commandQueue,
        jlong deviceContext, jint backend, jint deviceIndex, jint platformIndex, jlong executionPlanId) {
    (void) clazz;

    if (bytecode == NULL) {
        throw_java_exception(env, "java/lang/NullPointerException", "bytecode buffer is null");
        return 0;
    }

    const jsize length = env->GetArrayLength(bytecode);
    if (limit < 0 || limit > length || position < 0 || position > limit) {
        throw_java_exception(env, "java/lang/IllegalArgumentException", "position and limit must satisfy 0 <= position <= limit <= bytecode.length");
        return 0;
    }

    /*
     * Lengths are taken before any critical pin. GetArrayLength is a JNI call, so it must
     * not run while an array is pinned. 
     */
    const int32_t objectCount = array_length_or_zero(env, bufferHandles);
    const int32_t offsetCount = array_length_or_zero(env, bufferOffsets);
    const int32_t sizeCount = array_length_or_zero(env, bufferSizes);
    const int32_t hostCount = array_length_or_zero(env, hostPointers);
    const int32_t kernelCount = array_length_or_zero(env, kernelHandles);
    const int32_t programCount = array_length_or_zero(env, programHandles);
    const int32_t constantsBytes = array_length_or_zero(env, constants);

    TornadoPinnedArrays pins;
    pinned_arrays_init(&pins, env);

    void *bytecodeRaw = NULL;
    if (length > 0) {
        bytecodeRaw = pin_array(&pins, bytecode);
        if (bytecodeRaw == NULL) {
            return 0;
        }
    }

    void *bufferHandlesRaw = NULL;
    void *bufferOffsetsRaw = NULL;
    void *bufferSizesRaw = NULL;
    void *hostPointersRaw = NULL;
    void *kernelHandlesRaw = NULL;
    void *programHandlesRaw = NULL;
    void *constantsRaw = NULL;

    if (objectCount > 0) {
        bufferHandlesRaw = pin_array(&pins, bufferHandles);
        if (bufferHandlesRaw == NULL) {
            return 0;
        }
    }
    if (offsetCount > 0) {
        bufferOffsetsRaw = pin_array(&pins, bufferOffsets);
        if (bufferOffsetsRaw == NULL) {
            return 0;
        }
    }
    if (sizeCount > 0) {
        bufferSizesRaw = pin_array(&pins, bufferSizes);
        if (bufferSizesRaw == NULL) {
            return 0;
        }
    }
    if (hostCount > 0) {
        hostPointersRaw = pin_array(&pins, hostPointers);
        if (hostPointersRaw == NULL) {
            return 0;
        }
    }
    if (kernelCount > 0) {
        kernelHandlesRaw = pin_array(&pins, kernelHandles);
        if (kernelHandlesRaw == NULL) {
            return 0;
        }
    }
    if (programCount > 0) {
        programHandlesRaw = pin_array(&pins, programHandles);
        if (programHandlesRaw == NULL) {
            return 0;
        }
    }
    if (constantsBytes > 0) {
        constantsRaw = pin_array(&pins, constants);
        if (constantsRaw == NULL) {
            return 0;
        }
    }

    TornadoInterpreterContext ctx = {};
    ctx.code = (const uint8_t *) bytecodeRaw;
    ctx.position = (int32_t) position;
    ctx.limit = (int32_t) limit;
    ctx.flags = (int32_t) flags;
    ctx.buffer_handles = (const int64_t *) bufferHandlesRaw;
    ctx.buffer_offsets = (const int64_t *) bufferOffsetsRaw;
    ctx.buffer_sizes = (const int64_t *) bufferSizesRaw;
    ctx.host_pointers = (const int64_t *) hostPointersRaw;
    ctx.object_count = objectCount;
    ctx.kernel_handles = (const int64_t *) kernelHandlesRaw;
    ctx.program_handles = (const int64_t *) programHandlesRaw;
    ctx.kernel_count = kernelCount;
    ctx.constants = (const uint8_t *) constantsRaw;
    ctx.constants_bytes = constantsBytes;
    ctx.command_queue = (int64_t) commandQueue;
    ctx.device_context = (int64_t) deviceContext;
    ctx.backend = (int32_t) backend;
    ctx.device_index = (int32_t) deviceIndex;
    ctx.platform_index = (int32_t) platformIndex;
    ctx.execution_plan_id = (int64_t) executionPlanId;

    const int64_t result = tornado_interpret(&ctx);

    pinned_arrays_release(&pins);

    return (jlong) result;
}
