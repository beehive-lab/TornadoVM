#include <jni.h>
#include <cuda.h>

#include "CUDAEvent.h"

jbyteArray array_from_event(JNIEnv *env, CUevent *event) {
    jbyteArray array = (*env)->NewByteArray(env, sizeof(CUevent));
    (*env)->SetByteArrayRegion(env, array, 0, sizeof(CUevent), (void*) event);
    return array;
}

jobjectArray wrapper_from_events(JNIEnv *env, CUevent *event1, CUevent *event2) {
    jbyteArray array1 = array_from_event(env, event1);
    jbyteArray array2 = array_from_event(env, event2);
    jclass byteClass = (*env)->FindClass(env, "[B");
    jobjectArray wrapper_2d_array = (*env)->NewObjectArray(env, (jsize) 2, byteClass, NULL);
    (*env)->SetObjectArrayElement(env, wrapper_2d_array, (jsize) 0, array1);
    (*env)->SetObjectArrayElement(env, wrapper_2d_array, (jsize) 1, array2);
    return wrapper_2d_array;
}

void event_from_array(JNIEnv *env, CUevent *event, jbyteArray array) {
    (*env)->GetByteArrayRegion(env, array, 0, sizeof(CUevent), (void *) event);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDAEvent
 * Method:    cuEventDestroy
 * Signature: ([B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_cuEventDestroy
  (JNIEnv *env, jclass clazz, jbyteArray event_wrapper) {
    CUevent event;
    event_from_array(env, &event, event_wrapper);

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
 * Method:    cuEventElapsedTime
 * Signature: ([B)V
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDAEvent_cuEventElapsedTime
  (JNIEnv *env, jclass clazz, jobjectArray wrapper) {
    jbyteArray array1 = (jbyteArray) (*env)->GetObjectArrayElement(env, wrapper, (jsize) 0);
    jbyteArray array2 = (jbyteArray) (*env)->GetObjectArrayElement(env, wrapper, (jsize) 1);

    CUevent beforeEvent, afterEvent;
    event_from_array(env, &beforeEvent, array1);
    event_from_array(env, &afterEvent, array2);

    float time;
    cuEventElapsedTime(&time, beforeEvent, afterEvent);
    // cuEventElapsedTime returns the time in milliseconds.  We convert because the tornado profiler uses nanoseconds.
    return (jlong) (time * 1e+6);
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