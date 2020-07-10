#include <jni.h>
/* Header for class uk_ac_manchester_tornado_drivers_ptx_PTXDevice */

#ifndef _Included_uk_ac_manchester_tornado_drivers_ptx_PTXDevice
#define _Included_uk_ac_manchester_tornado_drivers_ptx_PTXDevice
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceGetName
 * Signature: (JI[B)V
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceGetName
  (JNIEnv *, jclass, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceGetAttribute
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceGetAttribute
  (JNIEnv *, jclass, jint, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceTotalMem
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceTotalMem
  (JNIEnv *, jclass, jint);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDriverGetVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDriverGetVersion
  (JNIEnv *, jclass);

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuMemGetInfo
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuMemGetInfo
  (JNIEnv *, jclass, jint);

#ifdef __cplusplus
}
#endif
#endif
