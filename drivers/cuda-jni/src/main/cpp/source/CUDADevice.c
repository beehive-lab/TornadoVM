#include <jni.h>
#include <cuda.h>

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADevice
 * Method:    cuDeviceGetName
 * Signature: (JI[B)V
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADevice_cuDeviceGetName
  (JNIEnv *env, jclass clazz, jint device_id) {
    CUdevice dev;
    char name[256];
    CUresult deviceGet = cuDeviceGet(&dev, (int) device_id);
    cuDeviceGetName(name, 256, dev);

    return (*env)->NewStringUTF(env, name);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADevice
 * Method:    cuDeviceGetAttribute
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADevice_cuDeviceGetAttribute
  (JNIEnv *env, jclass clazz, jint device_id, jint attr_id) {

    CUdevice dev;
    cuDeviceGet(&dev, (int) device_id);

    int attribute_value;
    cuDeviceGetAttribute(&attribute_value, (CUdevice_attribute) attr_id, dev);

    return (jint) attribute_value;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_cuda_CUDADevice
 * Method:    cuDeviceTotalMem
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_cuda_CUDADevice_cuDeviceTotalMem
  (JNIEnv *env, jclass clazz, jint device_id) {
    CUdevice dev;
    cuDeviceGet(&dev, (int) device_id);

    size_t mem_in_bytes;
    cuDeviceTotalMem(&mem_in_bytes, dev);

    return (jlong) mem_in_bytes;
}