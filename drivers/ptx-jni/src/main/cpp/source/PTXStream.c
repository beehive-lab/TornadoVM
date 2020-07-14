/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#include <jni.h>
#include <cuda.h>
#include <stdio.h>

#include "PTXModule.h"
#include "PTXEvent.h"
#include "data_copies.h"

/*
    A singly linked list (with elements of type StagingAreaList) is used to keep all the allocated pinned memory through cuMemAllocHost.
    A queue (with elements of type QueueNode) is used to hold all the free (no longer used) pinned memory regions.

    On a new read/write we call get_first_free_staging_area which will try to dequeue a pinned memory region to use it.
*/

/*
    Linked list which holds information regarding the pinned memory allocated.

    next            -- next element of the list
    staging_area    -- pointer to the pinned memory region
    length          -- length in bytes of the memory region referenced by staging_area
*/
typedef struct area_list {
    struct area_list *next;
    void *staging_area;
    size_t length;
} StagingAreaList;

/*
    Head of the allocated pinned memory list
 */
static StagingAreaList *head = NULL;

/*
    Linked list used to implement a queue which holds the free (no longer used) pinned memory regions.
*/
typedef struct queue_list {
    StagingAreaList* element;
    struct queue_list *next;
} QueueNode;

/*
    Pointers to the front and rear of the queue.
*/
static QueueNode *front = NULL;
static QueueNode *rear = NULL;

/*
    Adds a free pinned memory region to the queue.
*/
static void enqueue(StagingAreaList *region) {
    if (front == NULL) {
        front = malloc(sizeof(QueueNode));
        front->next = NULL;
        front->element = region;

        rear = front;
    } else {
        QueueNode *newRear = malloc(sizeof(QueueNode));
        newRear->next = NULL;
        newRear->element = region;

        rear->next = newRear;
        rear = newRear;
    }
}

/*
    Returns the first element (free pinned memory region) of the queue.
*/
static StagingAreaList* dequeue() {
    if (front == NULL) {
        return NULL;
    }
    StagingAreaList* region = front->element;
    QueueNode *oldFront = front;
    front = front->next;
    free(oldFront);

    return region;
}

/*
    Free the queue.
*/
static void free_queue() {
    if (front == NULL) return;

    QueueNode *node;
    while(front != NULL) {
        node = front;
        front = front->next;
        free(node);
    }
}

/*
    Checks if the given staging region can fit the required size. If not, allocates the required pinned memory.
*/
static StagingAreaList *check_or_init_staging_area(size_t size, StagingAreaList *list) {
    // Create
    if (list == NULL) {
        list = malloc(sizeof(StagingAreaList));
        CUresult result = cuMemAllocHost(&(list->staging_area), size);
        if (result != CUDA_SUCCESS) {
            printf("uk.ac.manchester.tornado.drivers.ptx> %s: %s = %d\n", "check_or_init_staging_area create" ,"cuMemAllocHost", result); fflush(stdout);
        }
        list->length = size;
        list->next = NULL;
    }

    // Update
    else if (list->length < size) {
        CUresult result = cuMemFreeHost(list->staging_area);
        if (result != CUDA_SUCCESS) {
            printf("uk.ac.manchester.tornado.drivers.ptx> %s: %s = %d\n", "check_or_init_staging_area update" ,"cuMemFreeHost", result);
            fflush(stdout);
        }
        result = cuMemAllocHost(&(list->staging_area), size);
        if (result != CUDA_SUCCESS) {
            printf("uk.ac.manchester.tornado.drivers.ptx> %s: %s = %d\n", "check_or_init_staging_area update" ,"cuMemAllocHost", result);
            fflush(stdout);
        }
        list->length = size;
    }
    return list;
}

/*
    Returns a StagingAreaList with pinned memory of given size.
*/
static StagingAreaList *get_first_free_staging_area(size_t size) {
    // Dequeue the first free staging area
    StagingAreaList *list = dequeue();

    list = check_or_init_staging_area(size, list);
    if (head == NULL) head = list;

    return list;
}

/*
    Called by cuStreamAddCallback, enqueues a StagingAreaList to the free queue for memory reuse.
*/
static void set_to_unused(CUstream hStream,  CUresult status, void *list) {
    StagingAreaList *stagingList = (StagingAreaList *) list;
    enqueue(stagingList);
}

/*
    Free all the allocated pinned memory.
*/
static void free_staging_area_list() {
    CUresult result;
    while (head != NULL) {
        result = cuMemFreeHost(head->staging_area);
        if (result != CUDA_SUCCESS) {
            printf("uk.ac.manchester.tornado.drivers.ptx> %s: %s = %d\n", "free_staging_area_list" ,"cuMemFreeHost", result);
            fflush(stdout);
        }
        StagingAreaList *list = head;
        head = head->next;
        free(list);
    }
}

static void stream_from_array(JNIEnv *env, CUstream *stream_ptr, jbyteArray array) {
    (*env)->GetByteArrayRegion(env, array, 0, sizeof(CUstream), (void *) stream_ptr);
}

static jbyteArray array_from_stream(JNIEnv *env, CUstream *stream) {
    jbyteArray array = (*env)->NewByteArray(env, sizeof(CUstream));
    (*env)->SetByteArrayRegion(env, array, 0, sizeof(CUstream), (void *) stream);
    return array;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[BJ[I)I
 */
COPY_ARRAY_D_TO_H(B, jbyte, Byte)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[SJ[I)I
 */
COPY_ARRAY_D_TO_H(S, jshort, Short)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJJ[CJ[I)I
 */
COPY_ARRAY_D_TO_H(C, jchar, Char)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[IJ[I)I
 */
COPY_ARRAY_D_TO_H(I, jint, Int)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[JJ[I)I
 */
COPY_ARRAY_D_TO_H(J, jlong, Long)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[FJ[I)I
 */
COPY_ARRAY_D_TO_H(F, jfloat, Float)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH(Async)
 * Signature: (JJJ[DJ[I)I
 */
COPY_ARRAY_D_TO_H(D, jdouble, Double)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[BJ[I)V
 */
COPY_ARRAY_H_TO_D(B, jbyte, Byte)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[SJ[I)V
 */
COPY_ARRAY_H_TO_D(S, jshort, Short)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[CJ[I)I
 */
COPY_ARRAY_H_TO_D(C, jchar, Char)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[IJ[I)V
 */
COPY_ARRAY_H_TO_D(I, jint, Int)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[JJ[I)V
 */
COPY_ARRAY_H_TO_D(J, jlong, Long)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[FJ[I)V
 */
COPY_ARRAY_H_TO_D(F, jfloat, Float)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD(Async)
 * Signature: (JJJ[DJ[I)V
 */
COPY_ARRAY_H_TO_D(D, jdouble, Double)

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuLaunchKernel
 * Signature: ([BLjava/lang/String;IIIIIIJ[B[B)I
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuLaunchKernel(
        JNIEnv *env,
        jclass clazz,
        jbyteArray module,
        jstring function_name,
        jint gridDimX, jint gridDimY, jint gridDimZ,
        jint blockDimX, jint blockDimY, jint blockDimZ,
        jlong sharedMemBytes,
        jbyteArray stream_wrapper,
        jbyteArray args) {

    CUresult result;
    CUmodule native_module;
    array_to_module(env, &native_module, module);

    const char *native_function_name = (*env)->GetStringUTFChars(env, function_name, 0);
    CUfunction kernel;
    CUDA_CHECK_ERROR("cuModuleGetFunction", cuModuleGetFunction(&kernel, native_module, native_function_name));

    size_t arg_buffer_size = (*env)->GetArrayLength(env, args);
    char arg_buffer[arg_buffer_size];
    (*env)->GetByteArrayRegion(env, args, 0, arg_buffer_size, arg_buffer);


    void *arg_config[] = {
        CU_LAUNCH_PARAM_BUFFER_POINTER, arg_buffer,
        CU_LAUNCH_PARAM_BUFFER_SIZE,    &arg_buffer_size,
        CU_LAUNCH_PARAM_END
    };

    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);

    RECORD_EVENT_BEGIN()
    CUDA_CHECK_ERROR("cuLaunchKernel",
        cuLaunchKernel(
            kernel,
            (unsigned int) gridDimX,  (unsigned int) gridDimY,  (unsigned int) gridDimZ,
            (unsigned int) blockDimX, (unsigned int) blockDimY, (unsigned int) blockDimZ,
            (unsigned int) sharedMemBytes, stream,
            NULL,
            arg_config
        )
    );
    RECORD_EVENT_END()

    (*env)->ReleaseStringUTFChars(env, function_name, native_function_name);

    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuCreateStream
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuCreateStream
  (JNIEnv *env, jclass clazz) {
    CUresult result;
    int lowestPriority, highestPriority;
    CUDA_CHECK_ERROR("cuCtxGetStreamPriorityRange", cuCtxGetStreamPriorityRange (&lowestPriority, &highestPriority));

    CUstream stream;
    CUDA_CHECK_ERROR("cuStreamCreateWithPriority", cuStreamCreateWithPriority(&stream, CU_STREAM_NON_BLOCKING, highestPriority));

    return array_from_stream(env, &stream);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuDestroyStream
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuDestroyStream
  (JNIEnv *env, jclass clazz, jbyteArray stream_wrapper) {
    CUresult result;
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);

    CUDA_CHECK_ERROR("cuStreamDestroy", cuStreamDestroy(stream));

    free_queue();
    free_staging_area_list();
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuStreamSynchronize
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuStreamSynchronize
  (JNIEnv *env, jclass clazz, jbyteArray stream_wrapper) {
    CUresult result;
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);

    CUDA_CHECK_ERROR("cuStreamSynchronize", cuStreamSynchronize(stream));
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuEventCreateAndRecord
 * Signature: (Z[B)[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuEventCreateAndRecord
  (JNIEnv *env, jclass clazz, jboolean is_timing, jbyteArray stream_wrapper) {
    CUresult result;
    unsigned int flags = CU_EVENT_DEFAULT;
    if (!is_timing) flags |= CU_EVENT_DISABLE_TIMING;

    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    RECORD_EVENT_BEGIN()
    RECORD_EVENT_END()

    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}