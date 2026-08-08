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

#include "TornadoNativeBytecodeInterpreter.h"
#include "tornado_interpreter.h"

static void throw_java_exception(JNIEnv *env, const char *name, const char *message) {
    jclass clazz = env->FindClass(name);
    if (clazz != NULL) {
        env->ThrowNew(clazz, message);
        env->DeleteLocalRef(clazz);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_runtime_interpreter_NativeBytecodeInterpreter
 * Method:    execute
 * Signature: ([BIII)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_runtime_interpreter_NativeBytecodeInterpreter_execute
        (JNIEnv *env, jclass clazz, jbyteArray bytecode, jint position, jint limit, jint flags) {
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
     * The loop below reads the buffer and returns; it allocates nothing and calls back into
     * neither the JVM nor any device driver, so holding the array critical for its duration
     * is safe and avoids copying the bytecode on every crossing. JNI_ABORT is correct on
     * release because the buffer is never written to.
     */
    void *raw = env->GetPrimitiveArrayCritical(bytecode, NULL);
    if (raw == NULL) {
        throw_java_exception(env, "java/lang/OutOfMemoryError", "could not pin the bytecode buffer");
        return 0;
    }

    const int64_t result = tornado_interpret((const uint8_t *) raw, (int32_t) position, (int32_t) limit, (int32_t) flags);

    env->ReleasePrimitiveArrayCritical(bytecode, raw, JNI_ABORT);

    return (jlong) result;
}
