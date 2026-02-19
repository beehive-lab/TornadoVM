#import <Metal/Metal.h>
#import <Foundation/Foundation.h>
#import <jni.h>

// Small Objective-C++ helper classes to represent programs and kernels
@interface MetalProgramWrapper : NSObject
@property(nonatomic, strong) id<MTLLibrary> library;
@property(nonatomic, strong) NSString *buildLog;
@property(nonatomic) jint buildStatus; // 0 success, non-zero error
@property(nonatomic, strong) id<MTLDevice> device;
@end

@implementation MetalProgramWrapper
@end

@interface ArgItem : NSObject
@property(nonatomic) int kind; // 0=buffer,1=bytes,2=local
@property(nonatomic, strong) id obj; // MTLBuffer or NSData
@property(nonatomic) NSUInteger size;
@end

@implementation ArgItem
@end

@interface MetalKernelWrapper : NSObject
@property(nonatomic, strong) id<MTLComputePipelineState> pipeline;
@property(nonatomic, strong) NSMutableArray *args;
@property(nonatomic, strong) id<MTLDevice> device;
@property(nonatomic, strong) NSString *functionName;
@property(nonatomic, strong) NSArray *argumentInfo; // array of MTLArgument
@end

@implementation MetalKernelWrapper
@end

extern "C" {

static inline id<MTLDevice> toDevice(jlong p) {
    return (__bridge id<MTLDevice>)(void*)(uintptr_t)p;
}

JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalNative_createSystemDefaultDevice
  (JNIEnv* env, jclass)
{
    @autoreleasepool {
        id<MTLDevice> dev = MTLCreateSystemDefaultDevice();
        if (!dev) return 0;
        CFRetain((__bridge CFTypeRef)dev); // hold across JNI
        return (jlong)(uintptr_t)(__bridge void*)dev;
    }
}

JNIEXPORT jstring JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalDevice_mtGetDeviceName
  (JNIEnv* env, jclass, jlong devicePtr)
{
    @autoreleasepool {
        id<MTLDevice> dev = toDevice(devicePtr);
        if (!dev) return env->NewStringUTF("unknown");
        const char* utf8 = [[dev name] UTF8String];
        return env->NewStringUTF(utf8 ? utf8 : "unknown");
    }
}

JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalDevice_mtGetDeviceGlobalMemorySize
  (JNIEnv* env, jclass, jlong devicePtr)
{
    @autoreleasepool {
        id<MTLDevice> dev = toDevice(devicePtr);
        if (!dev) return 0;

        unsigned long long size = 0;
        // recommendedMaxWorkingSetSize is available on macOS 10.15+ and provides
        // a reasonable approximation for the amount of memory the device can use.
        if (@available(macOS 10.15, *)) {
            if ([dev respondsToSelector:@selector(recommendedMaxWorkingSetSize)]) {
                size = (unsigned long long)[dev recommendedMaxWorkingSetSize];
            }
        }

        return (jlong)size;
    }
}

JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalDevice_mtGetDeviceLocalMemorySize
    (JNIEnv* env, jclass, jlong devicePtr)
{
    @autoreleasepool {
        id<MTLDevice> dev = toDevice(devicePtr);
        if (!dev) return 0;

        // maxThreadgroupMemoryLength is available on iOS/tvOS and macOS Metal devices
        // It returns the maximum size in bytes of threadgroup (local) memory.
        // Use respondsToSelector in case older runtimes don't expose the selector.
        if ([dev respondsToSelector:@selector(maxThreadgroupMemoryLength)]) {
            NSUInteger len = (NSUInteger)[dev maxThreadgroupMemoryLength];
            return (jlong)len;
        }

        return 0;
    }
}

JNIEXPORT jint JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalDevice_mtHasUnifiedMemory
  (JNIEnv* env, jclass, jlong devicePtr)
{
    @autoreleasepool {
        id<MTLDevice> dev = toDevice(devicePtr);
        if (!dev) return 0;

        // -hasUnifiedMemory is available on macOS/iOS Metal devices
        if ([dev respondsToSelector:@selector(hasUnifiedMemory)]) {
            BOOL v = (BOOL)[dev hasUnifiedMemory];
            return v ? (jint)1 : (jint)0;
        }

        return 0;
    }
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalNative_releaseDevice
  (JNIEnv* env, jclass, jlong devicePtr)
{
    @autoreleasepool {
        id<MTLDevice> dev = toDevice(devicePtr);
        if (dev) CFRelease((__bridge CFTypeRef)dev);
    }
}

JNIEXPORT jint JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_Metal_mtGetPlatformCount
        (JNIEnv *, jclass)
{
  return 1; // always 1 platform (Apple Metal)
}

JNIEXPORT jint JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalPlatform_clGetDeviceCount
        (JNIEnv *, jclass)
{
  @autoreleasepool {
    NSArray<id<MTLDevice>> *devices = nil;
    if (@available(macOS 10.11, *)) {
        devices = MTLCopyAllDevices();
    } else {
        id<MTLDevice> d = MTLCreateSystemDefaultDevice();
        devices = d ? @[d] : @[];
    }
    return (jint)devices.count;
  }
}

JNIEXPORT jint JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_Metal_mtGetPlatformIDs
  (JNIEnv *env, jclass clazz, jlongArray array)
{
    @autoreleasepool {
        jsize len = env->GetArrayLength(array);
        if (len == 0) return 0;

        jlong *platforms = env->GetLongArrayElements(array, nullptr);

        NSArray<id<MTLDevice>> *devices = nil;
        if (@available(macOS 10.11, *)) {
            devices = MTLCopyAllDevices();
        } else {
            id<MTLDevice> d = MTLCreateSystemDefaultDevice();
            devices = d ? @[d] : @[];
        }

        NSUInteger count = devices.count;
        NSUInteger limit = MIN(count, (NSUInteger)len);

        for (NSUInteger i = 0; i < limit; i++) {
            id<MTLDevice> dev = devices[i];
            CFRetain((__bridge CFTypeRef)dev); // hold across JNI boundary
            platforms[i] = (jlong)(uintptr_t)(__bridge void*)dev;
        }

        env->ReleaseLongArrayElements(array, platforms, 0);
        return (jint)count;
    }
}

JNIEXPORT jstring JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalPlatform_clGetPlatformInfo
  (JNIEnv *env, jclass, jlong platform_id, jint platform_info)
{
    @autoreleasepool {
        const char *value = "unknown";

        switch (platform_info) {
            case 0x0900: // CL_PLATFORM_PROFILE
                value = "FULL_PROFILE";
                break;

            case 0x0901: // CL_PLATFORM_VERSION
                value = "Metal 3.0";
                break;

            case 0x0902: // CL_PLATFORM_NAME
                value = "Apple Metal";
                break;

            case 0x0903: // CL_PLATFORM_VENDOR
                value = "Apple Inc.";
                break;

            case 0x0904: // CL_PLATFORM_EXTENSIONS
                value = ""; // Metal exposes no OpenCL-style extensions
                break;

            default:
                value = "unsupported_info";
                break;
        }

        return env->NewStringUTF(value);
    }
}

static inline bool wants_gpu(jlong device_type) {
    const jlong CL_DEVICE_TYPE_DEFAULT     = 1ULL;
    const jlong CL_DEVICE_TYPE_CPU         = 2ULL;
    const jlong CL_DEVICE_TYPE_GPU         = 4ULL;
    const jlong CL_DEVICE_TYPE_ACCELERATOR = 8ULL;
    const jlong CL_DEVICE_TYPE_ALL         = 0xFFFFFFFFULL;
    if (device_type == CL_DEVICE_TYPE_CPU || device_type == CL_DEVICE_TYPE_ACCELERATOR) return false;
    return (device_type & (CL_DEVICE_TYPE_GPU | CL_DEVICE_TYPE_DEFAULT | CL_DEVICE_TYPE_ALL)) != 0;
}

JNIEXPORT jint JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalPlatform_clGetDeviceIDs
  (JNIEnv *env, jclass, jlong /*platform_id*/, jlong device_type, jlongArray array)
{
    @autoreleasepool {
        if (!wants_gpu(device_type)) return 0;

        NSArray<id<MTLDevice>> *devs = MTLCopyAllDevices();     // macOS only
        const jsize len = env->GetArrayLength(array);
        jboolean isCopy = JNI_FALSE;
        jlong *out = env->GetLongArrayElements(array, &isCopy);

        const NSUInteger total = devs.count;
        const NSUInteger nfill = (NSUInteger)MIN((jsize)total, len);

        for (NSUInteger i = 0; i < nfill; i++) {
            id<MTLDevice> d = devs[i];
            CFRetain((__bridge CFTypeRef)d);                    // hold across JNI
            out[i] = (jlong)(uintptr_t)(__bridge void*)d;
        }

        env->ReleaseLongArrayElements(array, out, 0);
        return (jint)total;                                     // total devices available
    }
}

JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalPlatform_clCreateContext
  (JNIEnv* env, jclass, jlong /*platform_id*/, jlongArray devicesArray)
{
    @autoreleasepool {
        jsize len = env->GetArrayLength(devicesArray);
        if (len == 0) return 0;

        jboolean isCopy = JNI_FALSE;
        jlong* deviceHandles = env->GetLongArrayElements(devicesArray, &isCopy);
        id<MTLDevice> dev = (__bridge id<MTLDevice>)(void*)(uintptr_t)deviceHandles[0];
        env->ReleaseLongArrayElements(devicesArray, deviceHandles, 0);

        if (!dev) return 0;
        id<MTLCommandQueue> queue = [dev newCommandQueue];
        if (!queue) return 0;

        CFRetain((__bridge CFTypeRef)queue); // keep alive across JNI boundary
        return (jlong)(uintptr_t)(__bridge void*)queue;
    }
}

// Create
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_clCreateCommandQueue(
    JNIEnv* env, jclass clazz, jlong device_id, jint maxInFlight /* use 0 to ignore */) {
  @autoreleasepool {
    id<MTLDevice> device = (__bridge id<MTLDevice>) (void*) device_id;
    id<MTLCommandQueue> q =
        (maxInFlight > 0)
        ? [device newCommandQueueWithMaxCommandBufferCount:maxInFlight]
        : [device newCommandQueue];
    // Return a retained pointer for Java to hold
    return (jlong) CFBridgingRetain(q);
  }
}

JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_clReleaseCommandQueue(
        JNIEnv* env, jclass clazz, jlong queue_id) {
    @autoreleasepool {
        id<MTLCommandQueue> q = (__bridge_transfer id<MTLCommandQueue>)(void*) queue_id;
        (void)q; // released by __bridge_transfer
    }
    // Return 0 to indicate success (no OpenCL-style error codes used here)
    return (jlong)0;
}

/*
 * Allocate off-heap memory (posix_memalign on POSIX platforms)
 */
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_allocateOffHeapMemory
    (JNIEnv* env, jclass, jlong size, jlong alignment)
{
    void *ptr = nullptr;
#if _WIN32
    ptr = _aligned_malloc((size_t) alignment, (size_t) size);
    if (ptr == 0) {
        printf("Metal off-heap memory allocation (aligned_malloc) failed.\n");
    }
#else
    int rc = posix_memalign(&ptr, (size_t) alignment, (size_t) size);
    if (rc != 0) {
        printf("Metal off-heap memory allocation (posix_memalign) failed. Error value: %d.\n", rc);
    }
#endif
    if (ptr) memset(ptr, 0, (size_t) size);
    size_t i = 0;
    for (; i < (size_t) size / 4; i++) {
        ((int *) ptr)[i] = i;
    }
    return (jlong) ptr;
}

/*
 * writeArrayToDevice overloads
 * These functions copy host data into the MTLBuffer returned to Java (assumed shared storage).
 * For array variants we use GetPrimitiveArrayCritical for performance. The host-pointer
 * variant reads directly from the provided native address.
 */

// byte[] variant
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_writeArrayToDevice__J_3BJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jbyteArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jbyte *src = nullptr;
    jlong ret = -1;
    if (array) {
        src = (jbyte*) env->GetPrimitiveArrayCritical(array, NULL);
    }

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *dst = (void *)[buf contents];
        if (!dst) goto done;

        void *src_ptr = (void *)src;
        if (src_ptr) src_ptr = (void *)((char *)src_ptr + (size_t)hostOffset);
        void *dst_ptr = (void *)((char *)dst + (size_t)offset);

        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0; // no event produced
    }

done:
    if (array && src) env->ReleasePrimitiveArrayCritical(array, src, 0);
    return ret;
}

// char[] (Java char is unsigned 16-bit)
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_writeArrayToDevice__J_3CJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jcharArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jchar *src = nullptr;
    jlong ret = -1;
    if (array) src = (jchar*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *dst = (void *)[buf contents];
        if (!dst) goto done;

        void *src_ptr = (void *)src;
        if (src_ptr) src_ptr = (void *)((char *)src_ptr + (size_t)hostOffset);
        void *dst_ptr = (void *)((char *)dst + (size_t)offset);
        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && src) env->ReleasePrimitiveArrayCritical(array, src, 0);
    return ret;
}

// short[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_writeArrayToDevice__J_3SJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jshortArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jshort *src = nullptr;
    jlong ret = -1;
    if (array) src = (jshort*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *dst = (void *)[buf contents];
        if (!dst) goto done;

        void *src_ptr = (void *)src;
        if (src_ptr) src_ptr = (void *)((char *)src_ptr + (size_t)hostOffset);
        void *dst_ptr = (void *)((char *)dst + (size_t)offset);
        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && src) env->ReleasePrimitiveArrayCritical(array, src, 0);
    return ret;
}

// int[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_writeArrayToDevice__J_3IZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jintArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jint *src = nullptr;
    jlong ret = -1;
    if (array) src = (jint*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *dst = (void *)[buf contents];
        if (!dst) goto done;

        void *src_ptr = (void *)src;
        if (src_ptr) src_ptr = (void *)((char *)src_ptr + (size_t)hostOffset);
        void *dst_ptr = (void *)((char *)dst + (size_t)offset);
        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && src) env->ReleasePrimitiveArrayCritical(array, src, 0);
    return ret;
}

// long[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_writeArrayToDevice__J_3JZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jlongArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jlong *src = nullptr;
    jlong ret = -1;
    if (array) src = (jlong*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *dst = (void *)[buf contents];
        if (!dst) goto done;

        void *src_ptr = (void *)src;
        if (src_ptr) src_ptr = (void *)((char *)src_ptr + (size_t)hostOffset);
        void *dst_ptr = (void *)((char *)dst + (size_t)offset);
        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && src) env->ReleasePrimitiveArrayCritical(array, src, 0);
    return ret;
}

// float[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_writeArrayToDevice__J_3FJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jfloatArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jfloat *src = nullptr;
    jlong ret = -1;
    if (array) src = (jfloat*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *dst = (void *)[buf contents];
        if (!dst) goto done;

        void *src_ptr = (void *)src;
        if (src_ptr) src_ptr = (void *)((char *)src_ptr + (size_t)hostOffset);
        void *dst_ptr = (void *)((char *)dst + (size_t)offset);
        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && src) env->ReleasePrimitiveArrayCritical(array, src, 0);
    return ret;
}

// double[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_writeArrayToDevice__J_3DJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jdoubleArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jdouble *src = nullptr;
    jlong ret = -1;
    if (array) src = (jdouble*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *dst = (void *)[buf contents];
        if (!dst) goto done;

        void *src_ptr = (void *)src;
        if (src_ptr) src_ptr = (void *)((char *)src_ptr + (size_t)hostOffset);
        void *dst_ptr = (void *)((char *)dst + (size_t)offset);
        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && src) env->ReleasePrimitiveArrayCritical(array, src, 0);
    return ret;
}

// host-pointer variant: (long hostPointer)
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_writeArrayToDevice__JJJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jlong hostPointer, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) return (jlong)-1;
        void *dst = (void *)[buf contents];
        if (!dst) return (jlong)-1;

        void *src = (void *)(uintptr_t)hostPointer;
        if (!src) return (jlong)-1;

        void *src_ptr = (void *)((char *)src + (size_t)hostOffset);
        void *dst_ptr = (void *)((char *)dst + (size_t)offset);
        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        return (jlong)0;
    }
}

// readArrayFromDeviceOffHeap: copy from device buffer to native host pointer
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_readArrayFromDeviceOffHeap__JJJZJJJ_3J
    (JNIEnv *env, jclass clazz, jlong queueId, jlong hostPointer, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
        @autoreleasepool {
                id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
                if (!buf) return (jlong)-1;
                void *src = (void *)[buf contents];
                if (!src) return (jlong)-1;

                void *dst = (void *)(uintptr_t)hostPointer;
                if (!dst) return (jlong)-1;

                void *src_ptr = (void *)((char *)src + (size_t)offset);
                void *dst_ptr = (void *)((char *)dst + (size_t)hostOffset);
                memcpy(dst_ptr, src_ptr, (size_t)bytes);
                return (jlong)0;
        }
}

// byte[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_readArrayFromDevice__J_3BJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jbyteArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jbyte *dst = nullptr;
    jlong ret = -1;
    if (array) {
        dst = (jbyte*) env->GetPrimitiveArrayCritical(array, NULL);
    }

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *src = (void *)[buf contents];
        if (!src) goto done;

        void *src_ptr = (void *)((char *)src + (size_t)offset);
        void *dst_ptr = (void *)dst;
        if (dst_ptr) dst_ptr = (void *)((char *)dst_ptr + (size_t)hostOffset);

        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && dst) env->ReleasePrimitiveArrayCritical(array, dst, 0);
    return ret;
}

// char[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_readArrayFromDevice__J_3CJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jcharArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jchar *dst = nullptr;
    jlong ret = -1;
    if (array) dst = (jchar*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *src = (void *)[buf contents];
        if (!src) goto done;

        void *src_ptr = (void *)((char *)src + (size_t)offset);
        void *dst_ptr = (void *)dst;
        if (dst_ptr) dst_ptr = (void *)((char *)dst_ptr + (size_t)hostOffset);

        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && dst) env->ReleasePrimitiveArrayCritical(array, dst, 0);
    return ret;
}

// short[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_readArrayFromDevice__J_3SJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jshortArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jshort *dst = nullptr;
    jlong ret = -1;
    if (array) dst = (jshort*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *src = (void *)[buf contents];
        if (!src) goto done;

        void *src_ptr = (void *)((char *)src + (size_t)offset);
        void *dst_ptr = (void *)dst;
        if (dst_ptr) dst_ptr = (void *)((char *)dst_ptr + (size_t)hostOffset);

        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && dst) env->ReleasePrimitiveArrayCritical(array, dst, 0);
    return ret;
}

// int[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_readArrayFromDevice__J_3IJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jintArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jint *dst = nullptr;
    jlong ret = -1;
    if (array) dst = (jint*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *src = (void *)[buf contents];
        if (!src) goto done;

        void *src_ptr = (void *)((char *)src + (size_t)offset);
        void *dst_ptr = (void *)dst;
        if (dst_ptr) dst_ptr = (void *)((char *)dst_ptr + (size_t)hostOffset);

        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && dst) env->ReleasePrimitiveArrayCritical(array, dst, 0);
    return ret;
}

// long[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_readArrayFromDevice__J_3JZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jlongArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jlong *dst = nullptr;
    jlong ret = -1;
    if (array) dst = (jlong*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *src = (void *)[buf contents];
        if (!src) goto done;

        void *src_ptr = (void *)((char *)src + (size_t)offset);
        void *dst_ptr = (void *)dst;
        if (dst_ptr) dst_ptr = (void *)((char *)dst_ptr + (size_t)hostOffset);

        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && dst) env->ReleasePrimitiveArrayCritical(array, dst, 0);
    return ret;
}

// float[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_readArrayFromDevice__J_3FJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jfloatArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jfloat *dst = nullptr;
    jlong ret = -1;
    if (array) dst = (jfloat*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *src = (void *)[buf contents];
        if (!src) goto done;

        void *src_ptr = (void *)((char *)src + (size_t)offset);
        void *dst_ptr = (void *)dst;
        if (dst_ptr) dst_ptr = (void *)((char *)dst_ptr + (size_t)hostOffset);

        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && dst) env->ReleasePrimitiveArrayCritical(array, dst, 0);
    return ret;
}

// double[]
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_readArrayFromDevice__J_3DJZJJJ_3J
  (JNIEnv *env, jclass clazz, jlong queueId, jdoubleArray array, jlong hostOffset, jboolean blocking, jlong offset, jlong bytes, jlong ptr, jlongArray events)
{
    jdouble *dst = nullptr;
    jlong ret = -1;
    if (array) dst = (jdouble*) env->GetPrimitiveArrayCritical(array, NULL);

    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) ptr;
        if (!buf) goto done;
        void *src = (void *)[buf contents];
        if (!src) goto done;

        void *src_ptr = (void *)((char *)src + (size_t)offset);
        void *dst_ptr = (void *)dst;
        if (dst_ptr) dst_ptr = (void *)((char *)dst_ptr + (size_t)hostOffset);

        memcpy(dst_ptr, src_ptr, (size_t)bytes);
        ret = (jlong)0;
    }

done:
    if (array && dst) env->ReleasePrimitiveArrayCritical(array, dst, 0);
    return ret;
}

/*
 * Free off-heap memory
 */
JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_freeOffHeapMemory
    (JNIEnv* env, jclass, jlong address)
{
    free((void *) address);
}

/*
 * Create a direct ByteBuffer for a given native address
 */
JNIEXPORT jobject JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_asByteBuffer
    (JNIEnv *env, jclass clazz, jlong address, jlong capacity)
{
    return env->NewDirectByteBuffer((void *) address, capacity);
}

/*
 * Create a Metal buffer and return a MetalBufferResult(java) with (bufferPtr, address, status)
 */
JNIEXPORT jobject JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_createBuffer
    (JNIEnv *env, jclass clazz, jlong contextId, jlong flags, jlong size, jlong hostPointer)
{
        jclass resultClass = env->FindClass("uk/ac/manchester/tornado/drivers/metal/MetalContext$MetalBufferResult");
        if (resultClass == NULL) return NULL;
        jmethodID constructorId = env->GetMethodID(resultClass, "<init>", "(JJI)V");
        if (constructorId == NULL) return NULL;

        @autoreleasepool {
            id<MTLCommandQueue> queue = (__bridge id<MTLCommandQueue>)(void*) contextId;
            if (!queue) {
                return env->NewObject(resultClass, constructorId, (jlong)0, (jlong)0, (jint)-1);
            }

            id<MTLDevice> dev = [queue device];
            id<MTLBuffer> buf = nil;
            int status = 0;

            if (hostPointer == 0) {
                buf = [dev newBufferWithLength:(NSUInteger)size options:MTLResourceStorageModeShared];
            } else {
                void *host = (void*)(uintptr_t)hostPointer;
                buf = [dev newBufferWithBytesNoCopy:host length:(NSUInteger)size options:MTLResourceStorageModeShared deallocator:nil];
            }

            if (!buf) {
                status = -1;
                return env->NewObject(resultClass, constructorId, (jlong)0, (jlong)0, (jint)status);
            }

            // Retain buffer to hand ownership to Java
            CFRetain((__bridge CFTypeRef)buf);
            jlong bufferPtr = (jlong)(uintptr_t)(__bridge void*)buf;
            // Obtain CPU-accessible address (may be null for certain storage modes)
            void *contents = (void *)[buf contents];
            jlong address = (jlong)(uintptr_t)contents;

            return env->NewObject(resultClass, constructorId, bufferPtr, address, (jint)status);
        }
}

/*
 * Sub-buffers are not directly supported in Metal; return 0 (unsupported)
 */
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_createSubBuffer
    (JNIEnv *env, jclass clazz, jlong buffer, jlong flags, jint buffer_create_type, jbyteArray array)
{
        // Not supported in Metal; callers should manage ranges themselves
        return (jlong)0;
}

/*
 * Release a Metal buffer object
 */
JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_clReleaseMemObject
    (JNIEnv *env, jclass clazz, jlong memobj)
{
    @autoreleasepool {
        id<MTLBuffer> buf = (__bridge id<MTLBuffer>)(void*) memobj;
        if (buf) CFRelease((__bridge CFTypeRef)buf);
    }
}

/*
 * Query some context info. We provide the underlying device pointer when requested,
 * otherwise return without modifying the buffer.
 */
JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_clGetContextInfo
    (JNIEnv *env, jclass clazz, jlong context_id, jint param_name, jbyteArray array)
{
    jbyte *value = static_cast<jbyte *>(env->GetPrimitiveArrayCritical(array, NULL));
    int len = env->GetArrayLength(array);
    // Default: zero the buffer
    if (value && len > 0) memset(value, 0, len);

    @autoreleasepool {
        id<MTLCommandQueue> queue = (__bridge id<MTLCommandQueue>)(void*) context_id;
        if (!queue) {
            env->ReleasePrimitiveArrayCritical(array, value, 0);
            return;
        }
        id<MTLDevice> dev = [queue device];
        // If caller provided enough space, write the device pointer (8 bytes)
        if (value && len >= (int)sizeof(void *)) {
            void *devPtr = (void *)(__bridge void *)dev;
            memcpy(value, &devPtr, sizeof(void *));
        }
    }

    env->ReleasePrimitiveArrayCritical(array, value, 0);
}

/*
 * Release the 'context' (command queue) returned by clCreateContext
 */
JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_clReleaseContext
    (JNIEnv *env, jclass clazz, jlong context_id)
{
    @autoreleasepool {
        id<MTLCommandQueue> q = (__bridge_transfer id<MTLCommandQueue>)(void*) context_id;
        (void)q; // released by __bridge_transfer
    }
}

/*
 * Program creation is platform-specific. For now provide stubs consistent with
 * the OpenCL JNI layer: return -1 for unsupported operations or 0 for failure.
 */
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_clCreateProgramWithSource
    (JNIEnv *env, jclass clazz, jlong context_id, jbyteArray array1, jlongArray array2)
{
    if (array1 == NULL) return (jlong)0;
    jsize len = env->GetArrayLength(array1);
    jbyte *bytes = (jbyte *) env->GetPrimitiveArrayCritical(array1, NULL);
    if (!bytes) return (jlong)0;

    @autoreleasepool {
        id<MTLCommandQueue> queue = (__bridge id<MTLCommandQueue>)(void*) context_id;
        if (!queue) {
            env->ReleasePrimitiveArrayCritical(array1, bytes, 0);
            return (jlong)0;
        }
        id<MTLDevice> device = [queue device];
        if (!device) {
            env->ReleasePrimitiveArrayCritical(array1, bytes, 0);
            return (jlong)0;
        }

        // Create NSString from bytes (assume UTF8 source)
        NSString *src = [[NSString alloc] initWithBytes:bytes length:(NSUInteger)len encoding:NSUTF8StringEncoding];
        env->ReleasePrimitiveArrayCritical(array1, bytes, 0);
        if (!src) return (jlong)0;

        NSError *error = nil;
        id<MTLLibrary> lib = [device newLibraryWithSource:src options:nil error:&error];

        MetalProgramWrapper *pw = [[MetalProgramWrapper alloc] init];
        if (!lib) {
            pw.buildStatus = -1;
            pw.buildLog = error ? [error localizedDescription] : @"compile error";
        } else {
            pw.library = lib;
            pw.device = device;
            pw.buildStatus = 0;
            pw.buildLog = @"";
        }

        // Retain wrapper for JNI and return pointer
        CFRetain((__bridge CFTypeRef)pw);
        return (jlong)(uintptr_t)(__bridge void *)pw;
    }
}

JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_clCreateProgramWithBinary
    (JNIEnv *env, jclass clazz, jlong context_id, jlong device_id, jbyteArray array1, jlongArray array2)
{
    if (!array1) return (jlong)0;

    jsize len = env->GetArrayLength(array1);
    if (len <= 0) return (jlong)0;

    jbyte *bytes = (jbyte *) env->GetPrimitiveArrayCritical(array1, NULL);
    if (!bytes) return (jlong)0;

    @autoreleasepool {
        id<MTLCommandQueue> queue = (__bridge id<MTLCommandQueue>)(void*) context_id;
        if (!queue) {
            env->ReleasePrimitiveArrayCritical(array1, bytes, 0);
            return (jlong)0;
        }
        id<MTLDevice> device = [queue device];
        if (!device) {
            env->ReleasePrimitiveArrayCritical(array1, bytes, 0);
            return (jlong)0;
        }

        // Create NSData from binary bytes
        NSData *bin = [NSData dataWithBytes:(const void *)bytes length:(NSUInteger)len];
        env->ReleasePrimitiveArrayCritical(array1, bytes, 0);

        NSError *error = nil;

        // Convert NSData to dispatch_data_t for newLibraryWithData
        dispatch_data_t distData = dispatch_data_create([bin bytes], [bin length], dispatch_get_main_queue(), DISPATCH_DATA_DESTRUCTOR_DEFAULT);
        id<MTLLibrary> lib = [device newLibraryWithData:distData error:&error];

        MetalProgramWrapper *pw = [[MetalProgramWrapper alloc] init];
        if (!lib) {
            pw.buildStatus = -1;
            pw.buildLog = error ? [error localizedDescription] : @"createLibraryFromBinary failed";
        } else {
            pw.library = lib;
            pw.device = device;
            pw.buildStatus = 0;
            pw.buildLog = @"";
        }

        // Retain wrapper for JNI and return pointer
        CFRetain((__bridge CFTypeRef)pw);
        return (jlong)(uintptr_t)(__bridge void *)pw;
    }
}

JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalContext_clCreateProgramWithIL
    (JNIEnv *env, jclass clazz, jlong context_id, jbyteArray javaSourceBinaryArray, jlongArray javaSizeArray)
{
    // Indicate not supported
    return (jlong)-1;
}

/*
 * Minimal program build/info stubs for MetalProgram Java wrappers.
 * These provide basic, safe responses so the Java-side can query build status
 * and logs without requiring a full Metal shader compilation implementation
 * in native code. This is intentionally conservative: we return 'success'
 * and empty binaries/logs.
 */

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalProgram_clBuildProgram
    (JNIEnv *env, jclass clazz, jlong programId, jlongArray devices, jstring options)
{
    // No-op build: platform-specific compilation is not implemented here.
    (void) env; (void) clazz; (void) programId; (void) devices; (void) options;
    return;
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalProgram_clGetProgramInfo
    (JNIEnv *env, jclass clazz, jlong programId, jint param, jbyteArray buffer)
{
    (void) clazz;
    if (buffer == NULL) return;
    jsize len = env->GetArrayLength(buffer);
    jbyte *out = (jbyte *) env->GetPrimitiveArrayCritical(buffer, NULL);
    if (!out) return;
    if (len > 0) memset(out, 0, (size_t) len);

    const jint CL_PROGRAM_NUM_DEVICES = 0x1160; // OpenCL-like enum
    const jint CL_PROGRAM_DEVICES = 0x1161;
    const jint CL_PROGRAM_BINARY_SIZES = 0x1162;

    MetalProgramWrapper *pw = NULL;
    if (programId != 0) pw = (__bridge MetalProgramWrapper *)(void*)(uintptr_t)programId;

    if (param == CL_PROGRAM_NUM_DEVICES && len >= (int)sizeof(jint)) {
        jint one = 1;
        memcpy(out, &one, sizeof(jint));
    } else if (param == CL_PROGRAM_DEVICES && len >= (int)sizeof(jlong)) {
        jlong devptr = 0;
        memcpy(out, &devptr, sizeof(jlong));
    } else if (param == CL_PROGRAM_BINARY_SIZES && len >= (int)sizeof(jlong)) {
        jlong sz = 0;
        memcpy(out, &sz, sizeof(jlong));
    }

    env->ReleasePrimitiveArrayCritical(buffer, out, 0);
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalProgram_clGetProgramBuildInfo
    (JNIEnv *env, jclass clazz, jlong programId, jlong deviceId, jint param, jbyteArray buffer)
{
    (void) clazz; (void) deviceId;
    if (buffer == NULL) return;
    jsize len = env->GetArrayLength(buffer);
    jbyte *out = (jbyte *) env->GetPrimitiveArrayCritical(buffer, NULL);
    if (!out) return;
    if (len > 0) memset(out, 0, (size_t) len);

    const jint CL_PROGRAM_BUILD_STATUS = 0x1181; // mimic OpenCL
    const jint CL_PROGRAM_BUILD_LOG = 0x1183;

    MetalProgramWrapper *pw = NULL;
    if (programId != 0) pw = (__bridge MetalProgramWrapper *)(void*)(uintptr_t)programId;

    if (param == CL_PROGRAM_BUILD_STATUS && len >= (int)sizeof(jint)) {
        jint status = (pw) ? pw.buildStatus : 0;
        memcpy(out, &status, sizeof(jint));
    } else if (param == CL_PROGRAM_BUILD_LOG) {
        if (pw && pw.buildLog) {
            const char *s = [pw.buildLog UTF8String];
            if (s) {
                size_t n = strlen(s);
                size_t tocopy = (size_t)MIN((jsize)(len - 1), (jsize)n);
                if (tocopy > 0) memcpy(out, s, tocopy);
                out[tocopy] = '\0';
            }
        }
    }

    env->ReleasePrimitiveArrayCritical(buffer, out, 0);
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalProgram_getBinaries
    (JNIEnv *env, jclass clazz, jlong programId, jlong numDevices, jobject buffer)
{
    // No binaries available; Java-side expects this to write into a direct ByteBuffer.
    (void) env; (void) clazz; (void) programId; (void) numDevices; (void) buffer;
    return;
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalProgram_clReleaseProgram
    (JNIEnv *env, jclass clazz, jlong programId)
{
    (void) env; (void) clazz;
    if (programId == 0) return;
    @autoreleasepool {
        MetalProgramWrapper *pw = (__bridge_transfer MetalProgramWrapper *)(void*)(uintptr_t) programId;
        (void)pw; // released by __bridge_transfer
    }
}

/*
 * Kernel-related JNI stubs for Metal
 * We represent a "kernel" as a retained NSString containing the function name
 * so Java-side can query the function name via clGetKernelInfo. Argument
 * setting and kernel execution plumbing are not implemented here and are
 * treated as no-ops to allow higher-level Java code to proceed.
 */

JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalProgram_clCreateKernel
    (JNIEnv *env, jclass clazz, jlong programId, jstring name)
{
    if (programId == 0 || name == NULL) return (jlong)0;
    const char *utf = env->GetStringUTFChars(name, NULL);
    if (!utf) return (jlong)0;
    @autoreleasepool {
        NSString *ns = [NSString stringWithUTF8String:utf];
        env->ReleaseStringUTFChars(name, utf);
        if (!ns) return (jlong)0;

        MetalProgramWrapper *pw = (__bridge MetalProgramWrapper *)(void*)(uintptr_t)programId;
        if (!pw || !pw.library) return (jlong)0;

        id<MTLFunction> func = [pw.library newFunctionWithName:ns];
        if (!func) return (jlong)0;

        id<MTLDevice> device = [pw.library device];
        if (!device) device = MTLCreateSystemDefaultDevice();

        NSError *err = nil;
        MTLComputePipelineReflection *reflection = nil;
        id<MTLComputePipelineState> pipeline = nil;
        // Try to create pipeline with reflection if available (macOS 10.13+)
        if (@available(macOS 10.13, *)) {
            pipeline = [device newComputePipelineStateWithFunction:func options:MTLPipelineOptionArgumentInfo reflection:(MTLComputePipelineReflection **)&reflection error:&err];
        } else {
            pipeline = [device newComputePipelineStateWithFunction:func error:&err];
        }
        if (!pipeline) {
            // attach error to program buildLog
            if (pw) pw.buildLog = err ? [err localizedDescription] : @"pipeline creation failed";
            return (jlong)0;
        }

        MetalKernelWrapper *kw = [[MetalKernelWrapper alloc] init];
        kw.pipeline = pipeline;
        kw.args = [NSMutableArray array];
        kw.device = device;
        if (reflection) {
            // reflection.arguments is an NSArray of MTLArgument
            kw.argumentInfo = reflection.arguments;
        } else {
            kw.argumentInfo = @[];
        }
    kw.functionName = ns;

        CFRetain((__bridge CFTypeRef)kw);
        return (jlong)(uintptr_t)(__bridge void *)kw;
    }
}

JNIEXPORT jint JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalKernel_clGetKernelArgCount
    (JNIEnv *env, jclass clazz, jlong kernelId)
{
    (void) clazz;
    if (kernelId == 0) return 0;
    MetalKernelWrapper *kw = (__bridge MetalKernelWrapper *)(void*)(uintptr_t)kernelId;
    if (!kw || !kw.argumentInfo) return 0;
    return (jint)kw.argumentInfo.count;
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalKernel_clGetKernelArgInfo
    (JNIEnv *env, jclass clazz, jlong kernelId, jint index, jbyteArray buffer)
{
    (void) clazz;
    if (buffer == NULL) return;
    if (kernelId == 0) return;
    MetalKernelWrapper *kw = (__bridge MetalKernelWrapper *)(void*)(uintptr_t)kernelId;
    if (!kw || !kw.argumentInfo) return;

    jsize len = env->GetArrayLength(buffer);
    jbyte *out = (jbyte *) env->GetPrimitiveArrayCritical(buffer, NULL);
    if (!out) return;
    if (len > 0) memset(out, 0, (size_t) len);

    if (index < 0 || (NSUInteger)index >= kw.argumentInfo.count) {
        env->ReleasePrimitiveArrayCritical(buffer, out, 0);
        return;
    }

    id arg = [kw.argumentInfo objectAtIndex:(NSUInteger)index];
    if (!arg) {
        env->ReleasePrimitiveArrayCritical(buffer, out, 0);
        return;
    }

    @autoreleasepool {
        // Build a simple descriptor string: name:index:type:access:arrayLength
        MTLArgument *mArg = (MTLArgument *)arg;
        NSString *name = [mArg name];
        NSUInteger idx = (NSUInteger)[mArg index];
        NSString *typeStr = @"unknown";
        switch ([mArg type]) {
            case MTLArgumentTypeBuffer: typeStr = @"buffer"; break;
            case MTLArgumentTypeThreadgroupMemory: typeStr = @"threadgroup"; break;
            case MTLArgumentTypeTexture: typeStr = @"texture"; break;
            case MTLArgumentTypeSampler: typeStr = @"sampler"; break;
            default: typeStr = @"unknown"; break;
        }
        NSString *accessStr = @"unknown";
        switch ([mArg access]) {
            case MTLArgumentAccessReadOnly: accessStr = @"read"; break;
            case MTLArgumentAccessReadWrite: accessStr = @"readwrite"; break;
            case MTLArgumentAccessWriteOnly: accessStr = @"write"; break;
            default: accessStr = @"unknown"; break;
        }
        NSUInteger arrayLength = 0;
        if ([mArg respondsToSelector:@selector(arrayLength)]) {
            arrayLength = (NSUInteger)[arg arrayLength];
        }

        NSString *desc = [NSString stringWithFormat:@"%s:%lu:%@:%@:%lu", name ? [name UTF8String] : "", (unsigned long)idx, typeStr, accessStr, (unsigned long)arrayLength];
        const char *utf = [desc UTF8String];
        if (utf) {
            size_t n = strlen(utf);
            size_t tocopy = (size_t)MIN((jsize)(len - 1), (jsize)n);
            if (tocopy > 0) memcpy(out, utf, tocopy);
            out[tocopy] = '\0';
        }
    }

    env->ReleasePrimitiveArrayCritical(buffer, out, 0);
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalKernel_clReleaseKernel
    (JNIEnv *env, jclass clazz, jlong kernelId)
{
    (void) env; (void) clazz;
    @autoreleasepool {
        id obj = (__bridge_transfer id)(void*)(uintptr_t)kernelId;
        (void)obj; // released by __bridge_transfer
    }
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalKernel_clGetKernelInfo
    (JNIEnv *env, jclass clazz, jlong kernelId, jint info, jbyteArray buffer)
{
    (void) clazz; (void) info;
    if (buffer == NULL) return;
    jsize len = env->GetArrayLength(buffer);
    jbyte *out = (jbyte *) env->GetPrimitiveArrayCritical(buffer, NULL);
    if (!out) return;
    // Default: zero the buffer
    if (len > 0) memset(out, 0, (size_t) len);

    // Only support CL_KERNEL_FUNCTION_NAME (0x1190) for now
    const jint CL_KERNEL_FUNCTION_NAME = 0x1190;
    if (info == CL_KERNEL_FUNCTION_NAME) {
        @autoreleasepool {
                id obj = (__bridge id)(void*)(uintptr_t)kernelId;
                NSString *name = nil;
                if (obj) {
                    if ([obj isKindOfClass:[NSString class]]) {
                        name = (NSString *)obj;
                    } else if ([obj isKindOfClass:[MetalKernelWrapper class]]) {
                        MetalKernelWrapper *kw = (MetalKernelWrapper *)obj;
                        name = kw.functionName;
                    }
                }
                if (name && len > 0) {
                    const char *utf = [name UTF8String];
                    if (utf) {
                        size_t n = strlen(utf);
                        size_t tocopy = (size_t)MIN((jsize)(len - 1), (jsize)n);
                        if (tocopy > 0) memcpy(out, utf, tocopy);
                        out[tocopy] = '\0';
                    }
                }
            }
    }

    env->ReleasePrimitiveArrayCritical(buffer, out, 0);
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalKernel_clSetKernelArg
    (JNIEnv *env, jclass clazz, jlong kernelId, jint index, jlong size, jbyteArray buffer)
{
    (void) clazz;
    if (kernelId == 0) return;
    MetalKernelWrapper *kw = (__bridge MetalKernelWrapper *)(void*)(uintptr_t)kernelId;
    if (!kw) return;

    ArgItem *item = [[ArgItem alloc] init];
    if (buffer != NULL) {
        jsize len = env->GetArrayLength(buffer);
        jbyte *data = (jbyte *) env->GetPrimitiveArrayCritical(buffer, NULL);
        if (data) {
            NSData *d = [NSData dataWithBytes:(const void *)data length:(NSUInteger)MIN((jsize)size, len)];
            env->ReleasePrimitiveArrayCritical(buffer, data, 0);
            item.kind = 1; // bytes
            item.obj = d;
            item.size = (NSUInteger)size;
        } else {
            return;
        }
    } else {
        // when buffer is null and size>0 -> local (threadgroup) memory
        if (size > 0) {
            item.kind = 2; // local
            item.size = (NSUInteger)size;
            item.obj = nil;
        } else {
            // unused arg
            item.kind = 1;
            item.obj = [NSData data];
            item.size = 0;
        }
    }

    // Ensure args array is big enough
    NSUInteger idx = (NSUInteger)index;
    while (kw.args.count <= idx) {
        [kw.args addObject:[NSNull null]];
    }
    [kw.args replaceObjectAtIndex:idx withObject:item];
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalKernel_clSetKernelArgRef
    (JNIEnv *env, jclass clazz, jlong kernelId, jint index, jlong buffer)
{
    (void) clazz;
    if (kernelId == 0) return;
    MetalKernelWrapper *kw = (__bridge MetalKernelWrapper *)(void*)(uintptr_t)kernelId;
    if (!kw) return;

    ArgItem *item = [[ArgItem alloc] init];
    id<MTLBuffer> mbuf = (__bridge id<MTLBuffer>)(void*) buffer;
    item.kind = 0; // buffer
    item.obj = mbuf;
    item.size = 0; // unknown

    NSUInteger idx = (NSUInteger)index;
    while (kw.args.count <= idx) {
        [kw.args addObject:[NSNull null]];
    }
    [kw.args replaceObjectAtIndex:idx withObject:item];
}

/*
 * Enqueue a kernel for execution on the Metal command queue.
 * This is a conservative no-op stub: it validates the queue and kernel
 * handles and returns 0 (no event). Proper dispatch requires mapping
 * kernel/function and argument binding into Metal compute pipelines.
 */
JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_clEnqueueNDRangeKernel
    (JNIEnv *env, jclass clazz, jlong queueId, jlong kernelId, jint dim, jlongArray global_work_offset, jlongArray global_work_size, jlongArray local_work_size, jlongArray events)
{
    (void) clazz;
    if (queueId == 0 || kernelId == 0) return (jlong)-1;

    @autoreleasepool {
        id<MTLCommandQueue> q = (__bridge id<MTLCommandQueue>)(void*) queueId;
        MetalKernelWrapper *kw = (__bridge MetalKernelWrapper *)(void*)(uintptr_t)kernelId;
        if (!q || !kw || !kw.pipeline) return (jlong)-1;

        // Read global size
        jlong *gws = NULL;
        jlong *lws = NULL;
        jsize gwlen = 0;
        jsize lwlen = 0;
        if (global_work_size != NULL) {
            gwlen = env->GetArrayLength(global_work_size);
            gws = env->GetLongArrayElements(global_work_size, NULL);
        }
        if (local_work_size != NULL) {
            lwlen = env->GetArrayLength(local_work_size);
            lws = env->GetLongArrayElements(local_work_size, NULL);
        }

        NSUInteger gx = (gwlen > 0) ? (NSUInteger) gws[0] : 1;
        NSUInteger gy = (gwlen > 1) ? (NSUInteger) gws[1] : 1;
        NSUInteger gz = (gwlen > 2) ? (NSUInteger) gws[2] : 1;

        NSUInteger lx = 0, ly = 1, lz = 1;
        if (lwlen > 0) {
            lx = (NSUInteger) lws[0];
            ly = (lwlen > 1) ? (NSUInteger) lws[1] : 1;
            lz = (lwlen > 2) ? (NSUInteger) lws[2] : 1;
        } else {
            // choose reasonable defaults
            lx = kw.pipeline.threadExecutionWidth;
            ly = 1;
            lz = 1;
        }

        // clamp by maxTotalThreadsPerThreadgroup
        NSUInteger maxPer = kw.pipeline.maxTotalThreadsPerThreadgroup;
        if (lx * ly * lz > maxPer) {
            lx = MIN(lx, maxPer);
            ly = 1; lz = 1;
        }

        MTLSize threadsPerThreadgroup = MTLSizeMake(lx, ly, lz);
        MTLSize threadsPerGrid = MTLSizeMake(gx, gy, gz);

        id<MTLCommandBuffer> cb = [q commandBuffer];
        id<MTLComputeCommandEncoder> encoder = [cb computeCommandEncoder];
        [encoder setComputePipelineState:kw.pipeline];

        // bind arguments
        for (NSUInteger i = 0; i < kw.args.count; i++) {
            id obj = [kw.args objectAtIndex:i];
            if ((NSNull *)obj == [NSNull null]) continue;
            ArgItem *ai = (ArgItem *)obj;
            if (ai.kind == 0) {
                id<MTLBuffer> mb = (id<MTLBuffer>)ai.obj;
                if (mb) [encoder setBuffer:mb offset:0 atIndex:(NSUInteger)i];
            } else if (ai.kind == 1) {
                NSData *d = (NSData *)ai.obj;
                if (d && ai.size > 0) [encoder setBytes:[d bytes] length:ai.size atIndex:(NSUInteger)i];
            } else if (ai.kind == 2) {
                [encoder setThreadgroupMemoryLength:ai.size atIndex:(NSUInteger)i];
            }
        }

        // Create a small device buffer containing the three uint32 global sizes
        // and bind it as a device pointer parameter named _global_sizes in the kernel.
        NSUInteger scalarBaseIndex = kw.args.count;
        uint32_t sizes[3];
        sizes[0] = (uint32_t) gx;
        sizes[1] = (uint32_t) gy;
        sizes[2] = (uint32_t) gz;

        id<MTLBuffer> sizesBuf = [kw.device newBufferWithBytes:&sizes length:sizeof(sizes) options:MTLResourceStorageModeShared];
        if (sizesBuf) {
            // Retain sizesBuf while in use by command buffer; we'll release after commit
            CFRetain((__bridge CFTypeRef)sizesBuf);
            [encoder setBuffer:sizesBuf offset:0 atIndex:scalarBaseIndex];
        }

        // compute threadgroups
        MTLSize tg;
        tg.width = (gx + threadsPerThreadgroup.width - 1) / threadsPerThreadgroup.width;
        tg.height = (gy + threadsPerThreadgroup.height - 1) / threadsPerThreadgroup.height;
        tg.depth = (gz + threadsPerThreadgroup.depth - 1) / threadsPerThreadgroup.depth;

        [encoder dispatchThreadgroups:tg threadsPerThreadgroup:threadsPerThreadgroup];
        [encoder endEncoding];
        // If we created a sizes buffer, release it after the command buffer completes
        if (sizesBuf) {
            // Capture sizesBuf in a completion handler and release (must be added before commit)
            __unsafe_unretained id localBuf = sizesBuf;
            [cb addCompletedHandler:^(id<MTLCommandBuffer> buffer) {
                if (localBuf) CFRelease((__bridge CFTypeRef)localBuf);
            }];
        }
        [cb commit];
        [cb waitUntilCompleted]; // synchronous for now - ensure GPU is done before returning
        // release array elements
        if (gws) env->ReleaseLongArrayElements(global_work_size, gws, 0);
        if (lws) env->ReleaseLongArrayElements(local_work_size, lws, 0);

        // Retain command buffer and return as event id
        CFRetain((__bridge CFTypeRef)cb);
        return (jlong)(uintptr_t)(__bridge void *)cb;
    }
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_clEnqueueWaitForEvents
    (JNIEnv *env, jclass clazz, jlong queueId, jlongArray array)
{
    (void) clazz;
    if (array == NULL) return;
    jsize len = env->GetArrayLength(array);
    if (len == 0) return;
    jlong *events = env->GetLongArrayElements(array, NULL);
    // events format: [count, e1, e2, ...]
    jsize count = (jsize) events[0];
    for (jsize i = 0; i < count; i++) {
        jlong ev = events[i+1];
        if (ev == 0) continue;
        id<MTLCommandBuffer> cb = (__bridge id<MTLCommandBuffer>)(void*) ev;
        if (cb) {
            [cb waitUntilCompleted];
            // release retained cb
            CFRelease((__bridge CFTypeRef)cb);
        }
    }
    env->ReleaseLongArrayElements(array, events, 0);
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_clFlush
    (JNIEnv *env, jclass clazz, jlong queueId)
{
    (void) env; (void) clazz;
    @autoreleasepool {
        // Metal does not have a direct equivalent to OpenCL clFlush; in this
        // stub we simply ensure the queue handle is valid and do nothing.
        id<MTLCommandQueue> q = (__bridge id<MTLCommandQueue>)(void*) queueId;
        (void) q;
    }
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_clFinish
    (JNIEnv *env, jclass clazz, jlong queueId)
{
    (void) env; (void) clazz;
    @autoreleasepool {
        // Provide a conservative implementation: create a command buffer,
        // commit it and wait until completed to simulate a 'finish'. This is
        // lightweight when there are no pending GPU commands (no-ops otherwise).
        id<MTLCommandQueue> q = (__bridge id<MTLCommandQueue>)(void*) queueId;
        if (q) {
            id<MTLCommandBuffer> cb = [q commandBuffer];
            if (cb) {
                [cb commit];
                [cb waitUntilCompleted];
            }
        }
    }
}

// ---------------------------------------------------------------------------
// MetalEvent native methods
// Events in the Metal backend are retained MTLCommandBuffer pointers.
// ---------------------------------------------------------------------------

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalEvent_clReleaseEvent
    (JNIEnv *env, jclass clazz, jlong eventId)
{
    (void) env; (void) clazz;
    fprintf(stderr, "JNI: clReleaseEvent(0x%llx)\n", (unsigned long long)eventId);
    if (eventId == 0) return;
    @autoreleasepool {
        id<MTLCommandBuffer> cb = (__bridge id<MTLCommandBuffer>)(void*)(uintptr_t) eventId;
        CFRelease((__bridge CFTypeRef) cb);
    }
    fprintf(stderr, "JNI: clReleaseEvent done\n");
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalEvent_clWaitForEvents
    (JNIEnv *env, jclass clazz, jlongArray events)
{
    (void) clazz;
    if (events == NULL) return;
    jsize len = env->GetArrayLength(events);
    if (len == 0) return;
    jlong *evts = env->GetLongArrayElements(events, NULL);
    @autoreleasepool {
        for (jsize i = 0; i < len; i++) {
            if (evts[i] == 0) continue;
            id<MTLCommandBuffer> cb = (__bridge id<MTLCommandBuffer>)(void*)(uintptr_t) evts[i];
            [cb waitUntilCompleted];
        }
    }
    env->ReleaseLongArrayElements(events, evts, 0);
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalEvent_clGetEventInfo
    (JNIEnv *env, jclass clazz, jlong eventId, jint param, jbyteArray buffer)
{
    (void) clazz;
    // Stub: Metal command buffers don't expose the same event info as OpenCL.
    // Write zeros to the buffer.
    if (buffer == NULL) return;
    jsize len = env->GetArrayLength(buffer);
    jbyte *buf = env->GetByteArrayElements(buffer, NULL);
    memset(buf, 0, len);
    env->ReleaseByteArrayElements(buffer, buf, 0);
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalEvent_clGetEventProfilingInfo
    (JNIEnv *env, jclass clazz, jlong eventId, jlong param, jbyteArray buffer)
{
    (void) clazz;
    // Stub: Metal command buffers have timing info via GPUStartTime/GPUEndTime
    // but the mapping to OpenCL profiling params is not direct.
    // Write zeros for now.
    if (buffer == NULL) return;
    jsize len = env->GetArrayLength(buffer);
    jbyte *buf = env->GetByteArrayElements(buffer, NULL);
    memset(buf, 0, len);
    env->ReleaseByteArrayElements(buffer, buf, 0);
}

// ---------------------------------------------------------------------------
// MetalCommandQueue: clEnqueueMarkerWithWaitList, clEnqueueBarrierWithWaitList
// ---------------------------------------------------------------------------

JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_clEnqueueMarkerWithWaitList
    (JNIEnv *env, jclass clazz, jlong queueId, jlongArray events)
{
    (void) clazz;
    fprintf(stderr, "JNI: clEnqueueMarkerWithWaitList entered\n");
    @autoreleasepool {
        id<MTLCommandQueue> q = (__bridge id<MTLCommandQueue>)(void*)(uintptr_t) queueId;
        // Wait for all provided events (command buffers) to complete
        if (events != NULL) {
            jsize len = env->GetArrayLength(events);
            fprintf(stderr, "JNI: marker - %d events\n", (int)len);
            jlong *evts = env->GetLongArrayElements(events, NULL);
            for (jsize i = 0; i < len; i++) {
                fprintf(stderr, "JNI: marker - event[%d] = 0x%llx\n", (int)i, (unsigned long long)evts[i]);
                if (evts[i] == 0) continue;
                // Only wait on valid command buffer pointers (from clEnqueueNDRangeKernel)
                id<MTLCommandBuffer> cb = (__bridge id<MTLCommandBuffer>)(void*)(uintptr_t) evts[i];
                if (cb && [cb respondsToSelector:@selector(waitUntilCompleted)]) {
                    fprintf(stderr, "JNI: marker - waiting on event[%d]\n", (int)i);
                    [cb waitUntilCompleted];
                    fprintf(stderr, "JNI: marker - event[%d] completed\n", (int)i);
                }
            }
            env->ReleaseLongArrayElements(events, evts, 0);
        }
        fprintf(stderr, "JNI: clEnqueueMarkerWithWaitList done\n");
        // Return 0 to indicate no event produced (marker is implicit in Metal)
        return (jlong)0;
    }
}

JNIEXPORT jlong JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_clEnqueueBarrierWithWaitList
    (JNIEnv *env, jclass clazz, jlong queueId, jlongArray events)
{
    (void) clazz;
    @autoreleasepool {
        // In Metal, command buffers submitted to the same queue execute in order.
        // A barrier just needs to wait for all provided events to complete.
        if (events != NULL) {
            jsize len = env->GetArrayLength(events);
            jlong *evts = env->GetLongArrayElements(events, NULL);
            for (jsize i = 0; i < len; i++) {
                if (evts[i] == 0) continue;
                id<MTLCommandBuffer> cb = (__bridge id<MTLCommandBuffer>)(void*)(uintptr_t) evts[i];
                if (cb && [cb respondsToSelector:@selector(waitUntilCompleted)]) {
                    [cb waitUntilCompleted];
                }
            }
            env->ReleaseLongArrayElements(events, evts, 0);
        }
        return (jlong)0;
    }
}

JNIEXPORT void JNICALL
Java_uk_ac_manchester_tornado_drivers_metal_MetalCommandQueue_clReleaseCommandQueue
    (JNIEnv *env, jclass clazz, jlong queueId)
{
    (void) env; (void) clazz;
    if (queueId == 0) return;
    @autoreleasepool {
        CFRelease((void*)(uintptr_t) queueId);
    }
}

} // extern "C"