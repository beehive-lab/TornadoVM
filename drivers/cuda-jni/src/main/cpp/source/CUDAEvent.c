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

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAEvent
 * Method:    cuEventSynchronize
 * Signature: ([[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_cuEventSynchronize
  (JNIEnv *env, jclass clazz, jobjectArray wrappers) {
    jsize events_length = (*env)->GetArrayLength(env, wrappers);

    for (int i = 0; i < events_length; i++) {
        jbyteArray array = (jbyteArray) (*env)->GetObjectArrayElement(env, wrappers, i);
        CUevent event;
        event_from_array(env, &event, array);
        if (cuEventQuery(event) != 0) cuEventSynchronize(event); // Only wait on event if not completed yet

        (*env)->DeleteLocalRef(env, array);
    }
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAEvent
 * Method:    cuEventQuery
 * Signature: ([B)V
 */
JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_cuEventQuery
  (JNIEnv *env, jclass clazz, jbyteArray wrapper) {
    CUevent event;
    event_from_array(env, &event, wrapper);

    return (jboolean) cuEventQuery(event) == 0;
}