#include "levelZeroModule.h"

#include <iostream>
#include "ze_api.h"
#include "ze_log.h"

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroModule
 * Method:    zeModuleBuildLogDestroy
 * Signature: (Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeBuildLogHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroModule_zeModuleBuildLogDestroy
        (JNIEnv *env, jobject object, jobject javaBuildLog) {

    jclass klass = env->GetObjectClass(javaBuildLog);
    jfieldID fieldLogPtr = env->GetFieldID(klass, "ptrZeBuildLogHandle", "J");
    ze_module_build_log_handle_t buildLog = reinterpret_cast<ze_module_build_log_handle_t>(env->GetLongField(
            javaBuildLog, fieldLogPtr));
    ze_result_t result = zeModuleBuildLogDestroy(buildLog);
    LOG_ZE_JNI("zeModuleBuildLogDestroy", result);
    return result;
}

/*
 * Class:     uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroModule
 * Method:    zeKernelCreate_native
 * Signature: (JLuk/ac/manchester/tornado/drivers/spirv/levelzero/ZeKernelDesc;Luk/ac/manchester/tornado/drivers/spirv/levelzero/ZeKernelHandle;)I
 */
JNIEXPORT jint JNICALL Java_uk_ac_manchester_tornado_drivers_spirv_levelzero_LevelZeroModule_zeKernelCreate_1native
        (JNIEnv *env, jobject object, jlong javaModuleHandlePtr, jobject javaKernelDesc, jobject javaKernelHandler) {

    ze_module_handle_t module = reinterpret_cast<ze_module_handle_t>(javaModuleHandlePtr);

    jclass javaKernelClass = env->GetObjectClass(javaKernelHandler);
    jfieldID fieldKernelPtr = env->GetFieldID(javaKernelClass, "ptrZeKernelHandle", "J");
    jlong ptrKernel = env->GetLongField(javaKernelHandler, fieldKernelPtr);
    ze_kernel_handle_t kernel = nullptr;
    if (ptrKernel != -1) {
        kernel = reinterpret_cast<ze_kernel_handle_t>(ptrKernel);
    }

    jclass javaKernelDescClass = env->GetObjectClass(javaKernelDesc);
    jfieldID fieldKernelDescPtr = env->GetFieldID(javaKernelDescClass, "ptrZeKernelDesc", "J");
    jlong ptrKernelDesc = env->GetLongField(javaKernelDesc, fieldKernelDescPtr);
    ze_kernel_desc_t kernelDesc = {};
    if (ptrKernel != -1) {
        ze_kernel_desc_t* ptr = reinterpret_cast<ze_kernel_desc_t*>(ptrKernelDesc);
        kernelDesc = *ptr;
    }

    jfieldID fieldType = env->GetFieldID(javaKernelDescClass, "stype", "I");
    int type = env->GetIntField(javaKernelDesc, fieldType);

    jfieldID fieldFlags = env->GetFieldID(javaKernelDescClass, "flags", "J");
    long flags = env->GetLongField(javaKernelDesc, fieldFlags);

    jfieldID fieldName = env->GetFieldID(javaKernelDescClass, "kernelName",  "Ljava/lang/String;");
    jstring javaStringName = static_cast<jstring>(env->GetObjectField(javaKernelDesc, fieldName));
    const char* kernelName = env->GetStringUTFChars(javaStringName, 0);

    kernelDesc.pKernelName = kernelName;
    kernelDesc.stype = static_cast<ze_structure_type_t>(type);
    kernelDesc.flags = flags;

    ze_result_t result = zeKernelCreate(module, &kernelDesc, &kernel);
    LOG_ZE_JNI("zeKernelCreate", result);

    // Update javaKernelHandler object
    env->SetLongField(javaKernelHandler, fieldKernelPtr, reinterpret_cast<jlong>(kernel));

    // Update javaKernelDesc
    jfieldID field = env->GetFieldID(javaKernelDescClass, "pNext",  "J");
    env->SetLongField(javaKernelDesc, field, (jlong) kernelDesc.pNext);
    env->SetLongField(javaKernelDesc, fieldKernelDescPtr, reinterpret_cast<jlong>(&kernelDesc));

    return result;
}
