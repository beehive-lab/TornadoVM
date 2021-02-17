package uk.ac.manchester.tornado.drivers.spirv.levelzero;

// A context has a driver-handler associated with it
public class LevelZeroContext {

    ZeDriverHandle driver;
    ZeContextHandle contextHandle;
    ZeContextDesc contextDescription;

    static {
        // Use -Djava.library.path=./levelZeroLib/build/
        System.loadLibrary("tornado-levelzero");
    }

    native int zeContextCreate(long driverHandler, ZeContextDesc contextDescriptionPtr, long[] contextPtr);

    public int zeContextCreate_native(long driverHandler, int indexDriverHandler) {
        long[] contextPointers = new long[1];
        int status = zeContextCreate(driverHandler, contextDescription, contextPointers);
        this.contextHandle = new ZeContextHandle(contextPointers);
        return status;
    }

    public LevelZeroContext(ZeDriverHandle driver, ZeContextDesc contextDescription) {
        this.driver = driver;
        this.contextDescription = contextDescription;
    }

    public ZeDriverHandle getDriver() {
        return driver;
    }

    public ZeContextHandle getContextHandle() {
        return contextHandle;
    }

    native int zeCommandQueueCreate_native(long contextPtr, long deviceHandlerPtr, ZeCommandQueueDescription commandQueueDescription, ZeCommandQueueHandle commandQueue);

    public int zeCommandQueueCreate(long contextPtr, long deviceHandlerPtr, ZeCommandQueueDescription commandQueueDescription, ZeCommandQueueHandle commandQueue) {
        return zeCommandQueueCreate_native(contextPtr, deviceHandlerPtr, commandQueueDescription, commandQueue);
    }

    native int zeCommandListCreate_native(long contextPtr, long deviceHandlerPtr, ZeCommandListDescription commandListDescription, ZeCommandQueueListHandle commandList);

    public int zeCommandListCreate(long contextPtr, long deviceHandlerPtr, ZeCommandListDescription commandListDescription, ZeCommandQueueListHandle commandList) {
        return zeCommandListCreate_native(contextPtr, deviceHandlerPtr, commandListDescription, commandList);
    }

    public native int zeCommandListCreateImmediate_native(long contextPtr, long deviceHandlerPtr, ZeCommandQueueDescription commandQueueDescription, ZeCommandQueueListHandle commandList);

    public int zeCommandListCreateImmediate(long contextPtr, long deviceHandlerPtr, ZeCommandQueueDescription commandQueueDescription, ZeCommandQueueListHandle commandList) {
        return zeCommandListCreateImmediate_native(contextPtr, deviceHandlerPtr, commandQueueDescription, commandList);
    }

    native int zeMemAllocShared_native(long contextPtr, ZeDeviceMemAllocDesc deviceMemAllocDesc, ZeHostMemAllocDesc hostMemAllocDesc, int bufferSize, int alignment, long deviceHandlerPtr, LevelZeroBufferInteger buffer);

    public int zeMemAllocShared(long contextPtr, ZeDeviceMemAllocDesc deviceMemAllocDesc, ZeHostMemAllocDesc hostMemAllocDesc, int bufferSize, int alignment, long deviceHandlerPtr, LevelZeroBufferInteger buffer) {
        return zeMemAllocShared_native(contextPtr, deviceMemAllocDesc,  hostMemAllocDesc,  bufferSize,  alignment,  deviceHandlerPtr,  buffer);
    }

    native int zeModuleCreate_native(long contextPtr, long deviceHandlerPtr, LevelZeroBinaryModule binaryModule, ZeModuleDesc moduleDesc, ZeModuleHandle module, ZeBuildLogHandle buildLog);

    public int zeModuleCreate(long contextPtr, long deviceHandlerPtr, LevelZeroBinaryModule binaryModule, ZeModuleDesc moduleDesc, ZeModuleHandle module, ZeBuildLogHandle buildLog) {
        return zeModuleCreate_native(contextPtr, deviceHandlerPtr, binaryModule, moduleDesc, module, buildLog);
    }

    public long getDefaultContextPtr() {
        return getContextHandle().getContextPtr()[0];
    }

    native int zeMemFree_native(long contextHandlePtr, long bufferA);

    public int zeMemFree(long contextHandlePtr, LevelZeroBufferInteger bufferA) {
        int result = zeMemFree_native(contextHandlePtr, bufferA.getPtrBuffer());
        bufferA.initPtr();
        return result;
    }

    native int zeCommandListDestroy_native(long ptrZeCommandListHandle);

    public int zeCommandListDestroy(ZeCommandQueueListHandle ptrZeCommandListHandle) {
        int result = zeCommandListDestroy_native(ptrZeCommandListHandle.getPtrZeCommandListHandle());
        ptrZeCommandListHandle.initPtr();
        return result;
    }

    native int zeCommandQueueDestroy_native(long ptrZeCommandListHandle);

    public int zeCommandQueueDestroy(ZeCommandQueueHandle commandQueueHandle) {
        int result = zeCommandQueueDestroy_native(commandQueueHandle.getCommandQueueHandlerPointer());
        commandQueueHandle.initPtr();
        return result;
    }

    public void initPtr() {
        this.contextHandle.initPtr();
    }

    native int zeModuleBuildLogGetString_native(ZeBuildLogHandle buildLog, int[] sizeLog, String errorMessage);

    public int zeModuleBuildLogGetString(ZeBuildLogHandle buildLog, int[] sizeLog, String errorMessage) {
        return zeModuleBuildLogGetString_native(buildLog, sizeLog, errorMessage);
    }

    native int zeEventPoolCreate_native(long defaultContextPtr, ZeEventPoolDescription eventPoolDesc, int numDevices, long deviceHandlerPtr, ZeEventPoolHandle eventPool);

    public int zeEventPoolCreate(long defaultContextPtr, ZeEventPoolDescription eventPoolDesc, int numDevices, long deviceHandlerPtr, ZeEventPoolHandle eventPool) {
        return zeEventPoolCreate_native(defaultContextPtr, eventPoolDesc, numDevices, deviceHandlerPtr, eventPool);
    }

    native int zeEventCreate_native(ZeEventPoolHandle eventPool, ZeEventDescription eventDescription, ZeEventHandle event);

    public int zeEventCreate(ZeEventPoolHandle eventPool, ZeEventDescription eventDescription, ZeEventHandle event) {
        return zeEventCreate_native(eventPool, eventDescription, event);
    }

    native int zeEventPoolDestroy_native(long eventPool);

    public int zeEventPoolDestroy(ZeEventPoolHandle event) {
        int result = zeEventPoolDestroy_native(event.getPtrZeEventPoolHandle());
        event.initPtr();
        return result;
    }

    native int zeEventDestroy_native(long event);

    public int zeEventDestroy(ZeEventHandle event) {
        int result = zeEventDestroy_native(event.getPtrZeEventHandle());
        event.initPtr();
        return result;
    }
}
