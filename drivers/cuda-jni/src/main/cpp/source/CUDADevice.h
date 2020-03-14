#include <jni.h>
/* Header for class uk_ac_manchester_tornado_drivers_cuda_CUDADevice */

#ifndef _Included_uk_ac_manchester_tornado_drivers_cuda_CUDADevice
#define _Included_uk_ac_manchester_tornado_drivers_cuda_CUDADevice
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADevice
 * Method:    cuDeviceGetName
 * Signature: (JI[B)V
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADevice_cuDeviceGetName
  (JNIEnv *, jclass, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADevice
 * Method:    cuDeviceGetAttribute
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADevice_cuDeviceGetAttribute
  (JNIEnv *, jclass, jint, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADevice
 * Method:    cuDeviceTotalMem
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADevice_cuDeviceTotalMem
  (JNIEnv *, jclass, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADevice
 * Method:    cuDriverGetVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADevice_cuDriverGetVersion
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif
