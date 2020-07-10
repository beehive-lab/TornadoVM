#include <jni.h>
#include <cuda.h>

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceGetName
 * Signature: (JI[B)V
 */
JNIEXPORT jstring JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceGetName
  (JNIEnv *env, jclass clazz, jint device_id) {
    CUdevice dev;
    char name[256];
    CUresult deviceGet = cuDeviceGet(&dev, (int) device_id);
    cuDeviceGetName(name, 256, dev);

    return (*env)->NewStringUTF(env, name);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceGetAttribute
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceGetAttribute
  (JNIEnv *env, jclass clazz, jint device_id, jint attr_id) {

    CUdevice dev;
    cuDeviceGet(&dev, (int) device_id);

    int attribute_value;
    cuDeviceGetAttribute(&attribute_value, (CUdevice_attribute) attr_id, dev);

    return (jint) attribute_value;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDeviceTotalMem
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDeviceTotalMem
  (JNIEnv *env, jclass clazz, jint device_id) {
    CUdevice dev;
    cuDeviceGet(&dev, (int) device_id);

    size_t mem_in_bytes;
    cuDeviceTotalMem(&mem_in_bytes, dev);

    return (jlong) mem_in_bytes;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuMemGetInfo
 * Signature: (I)J
 */
JNIEXPORT jlong JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuMemGetInfo
  (JNIEnv *env, jclass clazz, jint device_id) {
    CUdevice dev;
    cuDeviceGet(&dev, device_id);

    size_t free;
    size_t total;
    cuMemGetInfo(&free, &total);

    return free;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_ptx_PTXDevice
 * Method:    cuDriverGetVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_ptx_PTXDevice_cuDriverGetVersion
  (JNIEnv *env, jclass clazz) {
    int driver_version;
    cuDriverGetVersion(&driver_version);
    return (jint) driver_version;
}