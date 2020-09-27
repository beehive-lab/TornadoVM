/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
 *
 */

#ifndef TORNADO_PTX_MACROS_DATA_COPIES
#define TORNADO_PTX_MACROS_DATA_COPIES

#include "macros.h"

#define COPY_ARRAY_D_TO_H(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoH__JJ_3##SIG##J_3B \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jbyteArray stream_wrapper) { \
    CUevent beforeEvent, afterEvent; \
    CUresult result; \
    CUstream stream; \
    stream_from_array(env, &stream, stream_wrapper); \
\
    StagingAreaList *staging_list = get_first_free_staging_area(length); \
    record_events_create(&beforeEvent, &afterEvent); \
    record_event_begin(&beforeEvent, &stream); \
\
    CUDA_CHECK_ERROR("cuMemcpyDtoHAsync", cuMemcpyDtoHAsync(staging_list->staging_area, device_ptr, (size_t) length, stream), result) \
\
    record_event_end(&afterEvent, &stream); \
    if (cuEventQuery(afterEvent) != CUDA_SUCCESS) cuEventSynchronize(afterEvent); \
 \
    (*env)->Set ## J_TYPE ## ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), staging_list->staging_area); \
    set_to_unused(stream, result, staging_list); \
 \
    return wrapper_from_events(env, &beforeEvent, &afterEvent); \
} \
 \
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayDtoHAsync__JJ_3##SIG##J_3B \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jbyteArray stream_wrapper) { \
    CUevent beforeEvent, afterEvent; \
    CUresult result; \
    void *native_array = (*env)->GetPrimitiveArrayCritical(env, array, 0); \
    CUstream stream; \
    stream_from_array(env, &stream, stream_wrapper); \
\
    record_events_create(&beforeEvent, &afterEvent); \
    record_event_begin(&beforeEvent, &stream); \
\
    CUDA_CHECK_ERROR("cuMemcpyDtoHAsync", cuMemcpyDtoHAsync(native_array + host_offset, device_ptr, (size_t) length, stream), result); \
 \
    record_event_end(&afterEvent, &stream); \
    CUDA_CHECK_ERROR("cuMemFreeHost", cuMemFreeHost(native_array), result); \
    (*env)->ReleasePrimitiveArrayCritical(env, array, native_array, 0); \
 \
    return wrapper_from_events(env, &beforeEvent, &afterEvent); \
}

#define COPY_ARRAY_H_TO_D(SIG,NATIVE_J_TYPE,J_TYPE) \
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoD__JJ_3##SIG##J_3B \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jbyteArray stream_wrapper) { \
    CUevent beforeEvent, afterEvent; \
    CUresult result; \
    CUstream stream; \
    stream_from_array(env, &stream, stream_wrapper); \
 \
    StagingAreaList *staging_list = get_first_free_staging_area(length); \
    (*env)->Get##J_TYPE##ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), staging_list->staging_area); \
\
    record_events_create(&beforeEvent, &afterEvent); \
    record_event_begin(&beforeEvent, &stream); \
 \
    CUDA_CHECK_ERROR("cuMemcpyHtoDAsync", cuMemcpyHtoDAsync(device_ptr, staging_list->staging_area, (size_t) length, stream), result); \
 \
    record_event_end(&afterEvent, &stream); \
    CUDA_CHECK_ERROR("cuStreamAddCallback", cuStreamAddCallback(stream, set_to_unused, staging_list, 0), result); \
    return wrapper_from_events(env, &beforeEvent, &afterEvent); \
} \
 \
JNIEXPORT jobjectArray JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXStream_writeArrayHtoDAsync__JJ_3##SIG##J_3B \
  (JNIEnv *env, jclass clazz, jlong device_ptr, jlong length, NATIVE_J_TYPE## Array array, jlong host_offset, jbyteArray stream_wrapper) { \
    CUevent beforeEvent, afterEvent; \
    CUresult result; \
    StagingAreaList *staging_list = get_first_free_staging_area(length); \
    (*env)->Get##J_TYPE##ArrayRegion(env, array, host_offset / sizeof(NATIVE_J_TYPE), length / sizeof(NATIVE_J_TYPE), staging_list->staging_area); \
 \
    CUstream stream; \
    stream_from_array(env, &stream, stream_wrapper); \
    record_events_create(&beforeEvent, &afterEvent); \
    record_event_begin(&beforeEvent, &stream); \
\
    CUDA_CHECK_ERROR("cuMemcpyHtoDAsync", cuMemcpyHtoDAsync(device_ptr, staging_list->staging_area, (size_t) length, stream), result); \
\
    record_event_end(&afterEvent, &stream); \
 \
    CUDA_CHECK_ERROR("cuStreamAddCallback", cuStreamAddCallback(stream, set_to_unused, staging_list, 0), result); \
 \
    return wrapper_from_events(env, &beforeEvent, &afterEvent); \
}

#endif