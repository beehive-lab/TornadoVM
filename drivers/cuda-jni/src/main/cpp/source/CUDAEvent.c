#include <jni.h>
#include <cuda.h>

#include "CUDAEvent.h"

jbyteArray array_from_event(JNIEnv *env, CUevent *event) {
    jbyteArray array = (*env)->NewByteArray(env, sizeof(CUevent));
    (*env)->SetByteArrayRegion(env, array, 0, sizeof(CUevent), (void*) event);
    return array;
}

void event_from_array(JNIEnv *env, CUevent *event, jbyteArray *array) {
    (*env)->GetByteArrayRegion(env, *array, 0, sizeof(CUevent), (void *) event);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAEvent
 * Method:    cuEventDestroy
 * Signature: ([B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_cuEventDestroy
  (JNIEnv *env, jclass clazz, jbyteArray event_wrapper) {
    CUevent event;
    event_from_array(env, &event, &event_wrapper);

    CUresult result = cuEventDestroy(event);
    return;
}