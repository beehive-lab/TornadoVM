/*
 * MIT License
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer
 * Method:    copy_native
 * Signature: (J[B)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer_copy_1native
        (JNIEnv *env, jobject object, jlong javaBufferPtr, jbyteArray javaArray) {
    void *buffer = reinterpret_cast<void *>(javaBufferPtr);
    jbyte *offHeapByteArray = static_cast<jbyte *>(buffer);
    jbyte *arrayByte = env->GetByteArrayElements(javaArray, 0);
    int size = env->GetArrayLength(javaArray);
    memccpy(offHeapByteArray, arrayByte, 0, size);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer
 * Method:    getByteBuffer_native
 * Signature: (JI)[B
 */
JNIEXPORT jbyteArray JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer_getByteBuffer_1native
        (JNIEnv * env, jobject object, jlong javaBufferPtr, jint size) {
    void *buffer = reinterpret_cast<void *>(javaBufferPtr);
    jbyte *offHeapByteArray = static_cast<jbyte *>(buffer);
    jbyteArray javaArray = env->NewByteArray(size);
    env->SetByteArrayRegion(javaArray, 0, size, offHeapByteArray);
    return javaArray;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong
 * Method:    memset_native
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroBufferLong;JI)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong_memset_1native
        (JNIEnv * env, jobject object, jobject javaBufferObject, jlong value, jint size) {

    jclass klass = env->GetObjectClass(javaBufferObject);
    jfieldID fieldPointer = env->GetFieldID(klass, "ptrBuffer", "J");
    jlong ptr = env->GetLongField(javaBufferObject, fieldPointer);

    long *buffer = nullptr;
    if (ptr != -1) {
        buffer = reinterpret_cast<long *>(ptr);
    }
    for (int i = 0; i < size; i++) {
        buffer[i] = value;
    }
    env->SetLongField(javaBufferObject, fieldPointer, reinterpret_cast<jlong>(buffer));
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong
 * Method:    isEqual
 * Signature: (JJI)Z
 */
JNIEXPORT jboolean JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong_isEqual
        (JNIEnv *env, jobject object, jlong javaBufferPointerA, jlong javaBufferPointerB, jint size) {
    void *bufferA = reinterpret_cast<void *>(javaBufferPointerA);
    void *bufferB = reinterpret_cast<void *>(javaBufferPointerB);

    bool outputValidationSuccessful = true;
    long *srcCharBuffer = static_cast<long *>(bufferA);
    long *dstCharBuffer = static_cast<long *>(bufferB);
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

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong
 * Method:    copy_native
 * Signature: (J[J)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong_copy_1native
        (JNIEnv * env, jobject object, jlong javaBufferObjectPtr, jlongArray array) {
    void *buffer = reinterpret_cast<void *>(javaBufferObjectPtr);
    jlong *offHeapByteArray = static_cast<jlong *>(buffer);
    jlong *arrayByte = env->GetLongArrayElements(array, 0);
    int size = env->GetArrayLength(array);
    memccpy(offHeapByteArray, arrayByte, 0, size);
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong
 * Method:    getLongBuffer_native
 * Signature: (JI)[J
 */
JNIEXPORT jlongArray JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBufferLong_getLongBuffer_1native
        (JNIEnv * env, jobject object, jlong javaBufferPtr, jint size) {
    void *buffer = reinterpret_cast<void *>(javaBufferPtr);
    jlong *offHeapByteArray = static_cast<jlong *>(buffer);
    jlongArray javaArray = env->NewLongArray(size);
    env->SetLongArrayRegion(javaArray, 0, size, offHeapByteArray);
    return javaArray;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer
 * Method:    memset_nativeInt
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/LevelZeroByteBuffer;II)V
 */
JNIEXPORT void JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroByteBuffer_memset_1nativeInt
        (JNIEnv * env, jobject object, jobject javaBufferObject, jint value, jint bufferSize) {

        jclass klass = env->GetObjectClass(javaBufferObject);
    jfieldID fieldPointer = env->GetFieldID(klass, "ptrBuffer", "J");
    jlong ptr = env->GetLongField(javaBufferObject, fieldPointer);
    int *buffer = nullptr;
    if (ptr != -1) {
        buffer = reinterpret_cast<int *>(ptr);
    }
    for (int i = 0; i < bufferSize; i++) {
        buffer[i] = value;
    }
    env->SetLongField(javaBufferObject, fieldPointer, reinterpret_cast<jlong>(buffer));
}