#include "levelZeroBuffer.h"

#include <iostream>
#include <cstring>
#include "ze_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger
 * Method:    memset_native
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroBufferInteger;II)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger_memset_1native
    (JNIEnv *env, jobject object, jobject javaBufferObject, jint value, jint size) {

    jclass klass = env->GetObjectClass(javaBufferObject);
    jfieldID fieldPointer = env->GetFieldID(klass, "ptrBuffer", "J");
    jlong ptr = env->GetLongField(javaBufferObject, fieldPointer);

    int *buffer = nullptr;
    if (ptr != -1) {
        buffer = reinterpret_cast<int *>(ptr);
    }
    //memset(buffer, value, size);
    for (int i = 0; i < size; i++) {
        buffer[i] = value;
    }
    env->SetLongField(javaBufferObject, fieldPointer, reinterpret_cast<jlong>(buffer));
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger
 * Method:    isEqual
 * Signature: (JJI)Z
 */
JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger_isEqual
        (JNIEnv *env, jobject object, jlong javaBufferA, jlong javaBufferB, jint size) {

    void *bufferA = reinterpret_cast<void *>(javaBufferA);
    void *bufferB = reinterpret_cast<void *>(javaBufferB);

    bool outputValidationSuccessful = true;
    int *srcCharBuffer = static_cast<int *>(bufferA);
    int *dstCharBuffer = static_cast<int *>(bufferB);
    for (int i = 0; i < size; i++) {
        if (srcCharBuffer[i] != dstCharBuffer[i]) {
            if (LOG_JNI) {
                std::cout << "srcBuffer[" << i << "] = " << static_cast<unsigned int>(srcCharBuffer[i])
                          << " not equal to "
                          << "dstBuffer[" << i << "] = " << static_cast<unsigned int>(dstCharBuffer[i]) << "\n";
            }
            outputValidationSuccessful = false;
            break;
        } else if (LOG_JNI) {
//            std::cout << "srcBuffer[" << i << "] = " << static_cast<unsigned int>(srcCharBuffer[i]) << " == "
//                      << "dstBuffer[" << i << "] = " << static_cast<unsigned int>(dstCharBuffer[i]) << "\n";
        }
    }
    return outputValidationSuccessful;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer
 * Method:    memset_native
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;BI)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer_memset_1native
        (JNIEnv *env, jobject object, jobject javaBufferObject, jbyte value, jint size) {

    jclass klass = env->GetObjectClass(javaBufferObject);
    jfieldID fieldPointer = env->GetFieldID(klass, "ptrBuffer", "J");
    jlong ptr = env->GetLongField(javaBufferObject, fieldPointer);

    jbyte *buffer = nullptr;
    if (ptr != -1) {
        buffer = reinterpret_cast<jbyte *>(ptr);
    }
    memset(buffer, value, size);
    env->SetLongField(javaBufferObject, fieldPointer, reinterpret_cast<jlong>(buffer));
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferInteger
 * Method:    isEqual
 * Signature: (JJI)Z
 */
JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer_isEqual
        (JNIEnv *env, jobject object, jlong javaBufferA, jlong javaBufferB, jint size) {

    void *bufferA = reinterpret_cast<void *>(javaBufferA);
    void *bufferB = reinterpret_cast<void *>(javaBufferB);
    bool outputValidationSuccessful = true;
    jbyte *srcCharBuffer = static_cast<jbyte *>(bufferA);
    jbyte *dstCharBuffer = static_cast<jbyte *>(bufferB);
    for (int i = 0; i < size; i++) {
        if (srcCharBuffer[i] != dstCharBuffer[i]) {
            if (LOG_JNI) {
                std::cout << "srcBuffer[" << i << "] = " << static_cast<unsigned int>(srcCharBuffer[i])
                          << " not equal to "
                          << "dstBuffer[" << i << "] = " << static_cast<unsigned int>(dstCharBuffer[i]) << "\n";
            }
            outputValidationSuccessful = false;
            break;
        }
    }
    return outputValidationSuccessful;
}