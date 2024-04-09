/*
 * MIT License
 *
 * Copyright (c) 2020-2022, APT Group, Department of Computer Science,
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
#include <cuda.h>

#include <iostream>
#include "PTXStream.h"
#include "PTXModule.h"
#include "PTXEvent.h"
#include "ptx_utils.h"
#include "ptx_log.h"

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
        front = static_cast<QueueNode *>(malloc(sizeof(QueueNode)));
        front->next = NULL;
        front->element = region;

        rear = front;
    } else {
        QueueNode *newRear = static_cast<QueueNode *>(malloc(sizeof(QueueNode)));
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
    Checks if the given staging region can fit into the required size. If not, it allocates the required pinned memory.
*/
static StagingAreaList *check_or_init_staging_area(size_t size, StagingAreaList *list) {
    // Create
    if (list == NULL) {
        list = static_cast<StagingAreaList *>(malloc(sizeof(StagingAreaList)));
        CUresult result = cuMemAllocHost(&(list->staging_area), size);
        if (result != CUDA_SUCCESS) {
            std::cout << "\t[JNI] " << __FILE__ << ":" << __LINE__ << " in function: " << __FUNCTION__ << " result = " << result << std::endl;
            std::flush(std::cout);
            return NULL;
        }
        list->length = size;
        list->next = NULL;
    }

    // Update
    else if (list->length < size) {
        CUresult result = cuMemFreeHost(list->staging_area);
        if (result != CUDA_SUCCESS) {
            std::cout << "\t[JNI] " << __FILE__ << ":" << __LINE__ << " in function: " << __FUNCTION__ << " result = " << result << std::endl;
            std::flush(std::cout);
            return NULL;
        }
        result = cuMemAllocHost(&(list->staging_area), size);
        if (result != CUDA_SUCCESS) {
            std::cout << "\t[JNI] " << __FILE__ << ":" << __LINE__ << " in function: " << __FUNCTION__ << " result = " << result << std::endl;
            std::flush(std::cout);
            return NULL;
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
static CUresult free_staging_area_list() {
    CUresult result;
    while (head != NULL) {
        result = cuMemFreeHost(head->staging_area);
        if (result != CUDA_SUCCESS) {
            std::cout << "\t[JNI] " << __FILE__ << ":" << __LINE__ << " in function: " << __FUNCTION__ << " result = " << result << std::endl;
            std::flush(std::cout);
        }
        StagingAreaList *list = head;
        head = head->next;
        free(list);
    }
    return result;
}

static void stream_from_array(JNIEnv *env, CUstream *stream_ptr, jbyteArray array) {
    env->GetByteArrayRegion(array, 0, sizeof(CUstream), reinterpret_cast<jbyte *>(stream_ptr));
}

static jbyteArray array_from_stream(JNIEnv *env, CUstream *stream) {
    jbyteArray array = env->NewByteArray(sizeof(CUstream));
    env->SetByteArrayRegion(array, 0, sizeof(CUstream), reinterpret_cast<const jbyte *>(stream));
    return array;
}

#define WRITE_DEVICE_TO_HOST_BLOCKING(TYPE, JAVATYPE)       \
    CUevent beforeEvent, afterEvent;                        \
    CUstream stream;                                        \
    stream_from_array(env, &stream, stream_wrapper);        \
    StagingAreaList *staging_list = get_first_free_staging_area(length);\
    record_events_create(&beforeEvent, &afterEvent);        \
    record_event(&beforeEvent, &stream);                    \
    CUresult result = cuMemcpyDtoHAsync(staging_list->staging_area, device_ptr, (size_t) length, stream); \
    LOG_PTX_AND_VALIDATE("cuMemcpyDtoHAsync", result);               \
    record_event(&afterEvent, &stream);                     \
    if (cuEventQuery(afterEvent) != CUDA_SUCCESS) {         \
        cuEventSynchronize(afterEvent);                     \
    }                                                       \
    env->Set ## TYPE ## ArrayRegion(array, host_offset / sizeof(JAVATYPE),                      \
            length / sizeof(JAVATYPE), static_cast<const JAVATYPE *>(staging_list->staging_area)); \
    set_to_unused(stream, result, staging_list);            \
    return wrapper_from_events(env, &beforeEvent, &afterEvent);


#define WRITE_DEVICE_TO_HOST_ASYNC(JAVATYPE)                                          \
    JAVATYPE *native_array = static_cast<JAVATYPE *>(env->GetPrimitiveArrayCritical(array, 0)); \
    CUstream stream;                                                                        \
    stream_from_array(env, &stream, stream_wrapper);                                        \
    CUevent beforeEvent;                                                                    \
    CUevent afterEvent;                                                                     \
    record_events_create(&beforeEvent, &afterEvent);                                        \
    record_event(&beforeEvent, &stream);                                                    \
    CUresult result = cuMemcpyDtoHAsync(native_array + hostOffset, devicePtr, (size_t) length, stream);\
    LOG_PTX_AND_VALIDATE("cuMemcpyDtoHAsync", result);                                               \
    record_event(&afterEvent, &stream);                                                     \
    env->ReleasePrimitiveArrayCritical(array, native_array, 0);                             \
    return wrapper_from_events(env, &beforeEvent, &afterEvent);


/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3BJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jbyteArray array, jlong host_offset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_BLOCKING(Byte, jbyte);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3SJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jshortArray array, jlong host_offset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_BLOCKING(Short, short);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3CJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jcharArray array, jlong host_offset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_BLOCKING(Char, jchar);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3IJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jintArray array, jlong host_offset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_BLOCKING(Int, jint);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3JJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jlongArray array, jlong host_offset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_BLOCKING(Long, jlong);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3FJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jfloatArray array, jlong host_offset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_BLOCKING(Float, jfloat);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJ[DJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3DJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jdoubleArray array, jlong host_offset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_BLOCKING(Double, jdouble);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3BJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jbyteArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_ASYNC(jbyte);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3SJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jshortArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_ASYNC(jshort);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3CJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jcharArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_ASYNC(jchar);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3IJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jintArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_ASYNC(jint);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3JJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jlongArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_ASYNC(jlong);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3FJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jfloatArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_ASYNC(jfloat);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJ[DJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3DJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jdoubleArray array, jlong hostOffset, jbyteArray stream_wrapper) {
    WRITE_DEVICE_TO_HOST_ASYNC(jdouble);
}

#define TRANSFER_FROM_HOST_TO_DEVICE_BLOCKING(TYPE, JAVATYPE)           \
    CUevent beforeEvent, afterEvent;                                    \
    CUstream stream;                                                    \
    stream_from_array(env, &stream, stream_wrapper);                    \
    StagingAreaList *staging_list = get_first_free_staging_area(length);\
    env->Get## TYPE ##ArrayRegion(array, host_offset / sizeof(JAVATYPE), length / sizeof(JAVATYPE), static_cast<JAVATYPE *>(staging_list->staging_area)); \
    record_events_create(&beforeEvent, &afterEvent);                    \
    record_event(&beforeEvent, &stream);                                \
    CUresult result = cuMemcpyHtoDAsync(device_ptr, staging_list->staging_area, (size_t) length, stream);\
    LOG_PTX_AND_VALIDATE("cuMemcpyHtoDAsync", result);                           \
    record_event(&afterEvent, &stream);                                 \
    result = cuStreamAddCallback(stream, set_to_unused, staging_list, 0);\
    LOG_PTX_AND_VALIDATE("cuStreamAddCallback", result);                         \
    return wrapper_from_events(env, &beforeEvent, &afterEvent);


#define TRANSFER_FROM_HOST_TO_DEVICE_ASYNC(TYPE, JAVATYPE)              \
    CUevent beforeEvent, afterEvent;                                    \
    StagingAreaList *staging_list = get_first_free_staging_area(length);\
    env->Get## TYPE ##ArrayRegion(array, host_offset / sizeof(JAVATYPE), length / sizeof(JAVATYPE), static_cast<JAVATYPE *>(staging_list->staging_area));\
    CUstream stream;                                                    \
    stream_from_array(env, &stream, stream_wrapper);                    \
    record_events_create(&beforeEvent, &afterEvent);                    \
    record_event(&beforeEvent, &stream);                                \
    CUresult result = cuMemcpyHtoDAsync(device_ptr, staging_list->staging_area, (size_t) length, stream);\
    LOG_PTX_AND_VALIDATE("cuMemcpyHtoDAsync", result);                           \
    record_event(&afterEvent, &stream);                                 \
    result = cuStreamAddCallback(stream, set_to_unused, staging_list, 0);\
    LOG_PTX_AND_VALIDATE("cuStreamAddCallback", result);                         \
    return wrapper_from_events(env, &beforeEvent, &afterEvent);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3BJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jbyteArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_BLOCKING(Byte, jbyte);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3SJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jshortArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_BLOCKING(Short, jshort);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3CJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jcharArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_BLOCKING(Char, jchar);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3IJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jintArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_BLOCKING(Int, jint);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3JJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jlongArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_BLOCKING(Long, jlong);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3FJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jfloatArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_BLOCKING(Float, jfloat);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJJ[DJ[I)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3DJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jdoubleArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_BLOCKING(Double, jdouble);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[BJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3BJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jbyteArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_ASYNC(Byte, jbyte);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[SJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3SJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jshortArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_ASYNC(Short, jshort);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[CJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3CJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jcharArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_ASYNC(Char, jchar);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[IJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3IJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jintArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_ASYNC(Int, jint);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[JJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3JJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jlongArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_ASYNC(Long, jlong);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[FJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3FJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jfloatArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_ASYNC(Float, jfloat);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJ[DJ[I)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3DJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jdoubleArray array, jlong host_offset, jbyteArray stream_wrapper) {
    TRANSFER_FROM_HOST_TO_DEVICE_ASYNC(Double, jdouble);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuLaunchKernel
 * Signature: ([BLjava/lang/String;IIIIIIJ[B[B)[[B
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

    CUevent beforeEvent;
    CUevent afterEvent;
    CUmodule native_module;
    array_to_module(env, &native_module, module);

    const char *native_function_name = env->GetStringUTFChars(function_name, 0);
    CUfunction kernel;
    CUresult result = cuModuleGetFunction(&kernel, native_module, native_function_name);
    LOG_PTX_AND_VALIDATE("cuModuleGetFunction", result);

    size_t arg_buffer_size = env->GetArrayLength(args);
#ifdef _WIN32
    char *arg_buffer = new char[arg_buffer_size];
#else
    char arg_buffer[arg_buffer_size];
#endif
    env->GetByteArrayRegion(args, 0, arg_buffer_size, reinterpret_cast<jbyte *>(arg_buffer));

    void *arg_config[] = {
        CU_LAUNCH_PARAM_BUFFER_POINTER, arg_buffer,
        CU_LAUNCH_PARAM_BUFFER_SIZE,    &arg_buffer_size,
        CU_LAUNCH_PARAM_END
    };

    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);

    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    result = cuLaunchKernel(
            kernel,
            (unsigned int) gridDimX,  (unsigned int) gridDimY,  (unsigned int) gridDimZ,
            (unsigned int) blockDimX, (unsigned int) blockDimY, (unsigned int) blockDimZ,
            (unsigned int) sharedMemBytes, stream,
            NULL,
            arg_config);
    LOG_PTX_AND_VALIDATE("cuLaunchKernel", result);
#ifdef _WIN32
    delete[] arg_buffer;
#endif

    record_event(&afterEvent, &stream);
    env->ReleaseStringUTFChars(function_name, native_function_name);
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuCreateStream
 * Signature: ()[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuCreateStream
  (JNIEnv *env, jclass clazz) {
    int lowestPriority, highestPriority;
    CUresult result = cuCtxGetStreamPriorityRange (&lowestPriority, &highestPriority);
    LOG_PTX_AND_VALIDATE("cuCtxGetStreamPriorityRange", result);

    CUstream stream;
    result = cuStreamCreateWithPriority(&stream, CU_STREAM_NON_BLOCKING, highestPriority);
    LOG_PTX_AND_VALIDATE("cuStreamCreateWithPriority", result);
    return array_from_stream(env, &stream);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuDestroyStream
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuDestroyStream
  (JNIEnv *env, jclass clazz, jbyteArray stream_wrapper) {
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);

    CUresult result = cuStreamDestroy(stream);
    LOG_PTX_AND_VALIDATE("cuStreamDestroy", result);

    free_queue();
    CUresult stagingAreaResult = free_staging_area_list();
    return (jlong) result & stagingAreaResult;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuStreamSynchronize
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuStreamSynchronize
  (JNIEnv *env, jclass clazz, jbyteArray stream_wrapper) {
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    CUresult result = cuStreamSynchronize(stream);
    LOG_PTX_AND_VALIDATE("cuStreamSynchronize", result);
    return (jlong) result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    cuEventCreateAndRecord
 * Signature: (Z[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_cuEventCreateAndRecord
  (JNIEnv *env, jclass clazz, jboolean is_timing, jbyteArray stream_wrapper) {
    CUevent beforeEvent;
    CUevent afterEvent;
    CUstream stream;

    stream_from_array(env, &stream, stream_wrapper);
    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    record_event(&afterEvent, &stream);

    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoH
 * Signature: (JJJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJJJ_3B
        (JNIEnv * env, jclass klass, jlong device_ptr, jlong length, jlong hostPointer, jlong host_offset, jbyteArray stream_wrapper) {
    CUevent beforeEvent, afterEvent;
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    CUresult result = cuMemcpyDtoHAsync((void*) (hostPointer + host_offset), device_ptr, (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyDtoHMemSeg", result);
    record_event(&afterEvent, &stream);
    if (cuEventQuery(afterEvent) != CUDA_SUCCESS) {
        cuEventSynchronize(afterEvent);
    }
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayDtoHAsync
 * Signature: (JJJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJJJ_3B
        (JNIEnv * env, jclass klass, jlong devicePtr, jlong length, jlong hostPointer, jlong hostOffset, jbyteArray stream_wrapper) {
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    CUevent beforeEvent;
    CUevent afterEvent;
    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    CUresult result = cuMemcpyDtoHAsync((void *) (hostPointer + hostOffset), devicePtr, (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyDtoHAsyncMemSeg", result);
    record_event(&afterEvent, &stream);
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoD
 * Signature: (JJJJ[B)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jlong hostPointer, jlong host_offset, jbyteArray stream_wrapper) {
    CUevent beforeEvent, afterEvent;
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    CUresult result = cuMemcpyHtoDAsync(device_ptr, (void *) (hostPointer + host_offset), (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyHtoDMemSeg", result);
    record_event(&afterEvent, &stream);
    if (cuEventQuery(afterEvent) != CUDA_SUCCESS) {
        cuEventSynchronize(afterEvent);
    }
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXStream
 * Method:    writeArrayHtoDAsync
 * Signature: (JJJJ[I)[[B
 */
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJJJ_3B
        (JNIEnv *env, jclass klass, jlong device_ptr, jlong length, jlong hostPointer, jlong host_offset, jbyteArray stream_wrapper) {
    CUevent beforeEvent, afterEvent;
    CUstream stream;
    stream_from_array(env, &stream, stream_wrapper);
    record_events_create(&beforeEvent, &afterEvent);
    record_event(&beforeEvent, &stream);
    CUresult result = cuMemcpyHtoDAsync(device_ptr, (void *) (hostPointer + host_offset), (size_t) length, stream);
    LOG_PTX_AND_VALIDATE("cuMemcpyHtoDAsyncMemSeg", result);
    record_event(&afterEvent, &stream);
    return wrapper_from_events(env, &beforeEvent, &afterEvent);
}