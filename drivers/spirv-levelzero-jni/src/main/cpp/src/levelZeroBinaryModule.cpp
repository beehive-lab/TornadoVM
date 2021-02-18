#include "levelZeroBinaryModule.h"

#include <iostream>
#include <fstream>
#include <memory>

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBinaryModule
 * Method:    readBinary_native
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroBinaryModule_readBinary_1native
        (JNIEnv *env, jobject javaLevelZeroBinaryModule) {

    jclass javaBinaryModule = env->GetObjectClass(javaLevelZeroBinaryModule);
    jfieldID fieldPath = env->GetFieldID(javaBinaryModule, "path", "Ljava/lang/String;");
    jstring objectString = static_cast<jstring>(env->GetObjectField(javaLevelZeroBinaryModule, fieldPath));
    const char* fileName = env->GetStringUTFChars(objectString, 0);
    std::string f(fileName);

    std::ifstream file(f, std::ios::binary);
    if (file.is_open()) {

        file.seekg(0, file.end);
        int length = file.tellg();
        file.seekg(0, file.beg);

        std::unique_ptr<char[]> spirvInput(new char[length]);
        file.read(spirvInput.get(), length);
        char* binaryFile = spirvInput.get();

        jfieldID field = env->GetFieldID(javaBinaryModule, "ptrToBinaryFile", "J");
        env->SetLongField(javaLevelZeroBinaryModule, field, reinterpret_cast<jlong>(binaryFile));

        field = env->GetFieldID(javaBinaryModule, "size", "I");
        env->SetIntField(javaLevelZeroBinaryModule, field, length);
        file.close();
        return 0;
    }
    return -1;
}
