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