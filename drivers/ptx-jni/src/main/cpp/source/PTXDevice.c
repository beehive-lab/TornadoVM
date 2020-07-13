#include <jni.h>
#include <cuda.h>

#include "macros.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceGetName
 * Signature: (JI[B)V
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceGetName
  (JNIEnv *env, jclass clazz, jint device_id) {
    CUresult result;
    CUdevice dev;
    char name[256];
    CUDA_CHECK_ERROR("cuDeviceGet", cuDeviceGet(&dev, (int) device_id));
    CUDA_CHECK_ERROR("cuDeviceGetName", cuDeviceGetName(name, 256, dev));

    return (*env)->NewStringUTF(env, name);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceGetAttribute
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceGetAttribute
  (JNIEnv *env, jclass clazz, jint device_id, jint attr_id) {
    CUresult result;
    CUdevice dev;
    CUDA_CHECK_ERROR("cuDeviceGet", cuDeviceGet(&dev, (int) device_id));

    int attribute_value;
    CUDA_CHECK_ERROR("cuDeviceGetAttribute", cuDeviceGetAttribute(&attribute_value, (CUdevice_attribute) attr_id, dev));

    return (jint) attribute_value;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceTotalMem
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceTotalMem
  (JNIEnv *env, jclass clazz, jint device_id) {
    CUresult result;
    CUdevice dev;

    CUDA_CHECK_ERROR("cuDeviceGet", cuDeviceGet(&dev, (int) device_id));

    size_t mem_in_bytes;
    CUDA_CHECK_ERROR("cuDeviceTotalMem", cuDeviceTotalMem(&mem_in_bytes, dev));

    return (jlong) mem_in_bytes;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuMemGetInfo
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuMemGetInfo
  (JNIEnv *env, jclass clazz, jint device_id) {
    CUresult result;
    CUdevice dev;
    CUDA_CHECK_ERROR("cuDeviceGet", cuDeviceGet(&dev, device_id));

    size_t free;
    size_t total;
    CUDA_CHECK_ERROR("cuMemGetInfo", cuMemGetInfo(&free, &total));

    return free;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDriverGetVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDriverGetVersion
  (JNIEnv *env, jclass clazz) {
    CUresult result;
    int driver_version;

    CUDA_CHECK_ERROR("cuDriverGetVersion", cuDriverGetVersion(&driver_version));

    return (jint) driver_version;
}