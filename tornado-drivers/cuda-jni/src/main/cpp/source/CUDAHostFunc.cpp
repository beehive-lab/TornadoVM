/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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

#include <jni.h>
#include "cuda_jni.h"

/*
 * Host callbacks: cuLaunchHostFunc enqueues a host function into a stream, and the driver runs it on
 * one of its own threads once the stream reaches that point. This lets the Java side learn that
 * device work has completed without parking a thread in cuEventSynchronize.
 *
 * Two hard rules from the CUDA driver, both of which shape the code below:
 *   1. The callback must not call any CUDA API. Doing so may deadlock.
 *   2. The callback must not block. It runs on a driver thread that other streams depend on.
 * So the callback does the minimum: attach to the JVM, hand the token to Java, detach. The Java side
 * (CUDAHostCallbacks) immediately moves the actual work onto its own executor thread, so no user code
 * ever runs on the driver's thread.
 */

static JavaVM *javaVM = nullptr;
static jclass callbackClass = nullptr;
static jmethodID callbackMethod = nullptr;

extern "C" {

/*
 * Caches the JavaVM and the static dispatch method the first time a callback is enqueued. Global
 * references are intentionally never released: they live as long as the driver library.
 */
static bool ensure_callback_binding(JNIEnv *env) {
    if (callbackMethod != nullptr) {
        return true;
    }
    if (env->GetJavaVM(&javaVM) != JNI_OK) {
        return false;
    }
    jclass localClass = env->FindClass("uk/ac/manchester/tornado/drivers/cuda/CUDAHostCallbacks");
    if (localClass == nullptr) {
        env->ExceptionClear();
        return false;
    }
    callbackClass = (jclass) env->NewGlobalRef(localClass);
    env->DeleteLocalRef(localClass);
    if (callbackClass == nullptr) {
        return false;
    }
    callbackMethod = env->GetStaticMethodID(callbackClass, "fireFromDriverThread", "(J)V");
    if (callbackMethod == nullptr) {
        env->ExceptionClear();
        return false;
    }
    return true;
}

/*
 * Runs on a CUDA driver thread. No CUDA calls, no blocking: attach (as a daemon, so a pending
 * callback can never keep the JVM alive), dispatch the token, detach.
 */
static void tornado_host_callback(void *userData) {
    if (javaVM == nullptr || callbackMethod == nullptr) {
        return;
    }
    jlong token = (jlong) (intptr_t) userData;
    JNIEnv *env = nullptr;
    bool attached = false;
    jint status = javaVM->GetEnv((void **) &env, JNI_VERSION_1_8);
    if (status == JNI_EDETACHED) {
        if (javaVM->AttachCurrentThreadAsDaemon((void **) &env, nullptr) != JNI_OK) {
            return;
        }
        attached = true;
    } else if (status != JNI_OK) {
        return;
    }
    env->CallStaticVoidMethod(callbackClass, callbackMethod, token);
    if (env->ExceptionCheck()) {
        // An exception must not propagate across the C ABI into the driver.
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    if (attached) {
        javaVM->DetachCurrentThread();
    }
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue
 * Method:    cuLaunchHostFunc
 * Signature: (JJ)I
 *
 * Enqueues a host callback carrying `token` into the queue's stream. Returns the CUresult so the
 * caller can fall back to a blocking wait when the driver refuses the callback.
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDACommandQueue_cuLaunchHostFunc
        (JNIEnv *env, jclass clazz, jlong queue_id, jlong token) {
    cuda_queue_t *queue = (cuda_queue_t *) queue_id;
    if (queue == nullptr) {
        return (jint) CUDA_ERROR_INVALID_VALUE;
    }
    if (!ensure_callback_binding(env)) {
        return (jint) CUDA_ERROR_NOT_INITIALIZED;
    }
    CUresult result = cuLaunchHostFunc(queue->stream, tornado_host_callback, (void *) (intptr_t) token);
    LOG_CUDA_AND_VALIDATE("cuLaunchHostFunc", result);
    return (jint) result;
}

} // extern "C"
