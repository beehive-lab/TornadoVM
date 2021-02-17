package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroKernel {

    private ZeKernelDesc kernelDesc;
    private ZeKernelHandle kernelHandle;

    public LevelZeroKernel(ZeKernelDesc kernelDesc, ZeKernelHandle kernelHandle) {
        this.kernelDesc = kernelDesc;
        this.kernelHandle = kernelHandle;
    }

    native int zeKernelSuggestGroupSize_native(long ptrZeKernelHandle, int globalSizeX, int globalSizeY, int globalSizeZ, int[] groupSizeX, int[] groupSizeY, int[] groupSizeZ);

    public int zeKernelSuggestGroupSize(long ptrZeKernelHandle, int globalSizeX, int globalSizeY, int globalSizeZ, int[] groupSizeX, int[] groupSizeY, int[] groupSizeZ) {
        int result = zeKernelSuggestGroupSize_native(ptrZeKernelHandle, globalSizeX, globalSizeY, globalSizeZ, groupSizeX, groupSizeY, groupSizeZ);
        return result;
    }

    native int zeKernelSetGroupSize_native(long ptrZeKernelHandle, int groupSizeX, int groupSizeY, int groupSizeZ);

    public int zeKernelSetGroupSize(long ptrZeKernelHandle, int[] groupSizeX, int[] groupSizeY, int[] groupSizeZ) {
        return zeKernelSetGroupSize_native(ptrZeKernelHandle, groupSizeX[0], groupSizeY[0], groupSizeZ[0]);
    }

    native int zeKernelSetArgumentValue_native(long ptrZeKernelHandle, int argIndex, int argSize, LevelZeroBufferInteger argValue);

    public int zeKernelSetArgumentValue(long ptrZeKernelHandle, int argIndex, int argSize, LevelZeroBufferInteger argValue) {
        return zeKernelSetArgumentValue_native(ptrZeKernelHandle, argIndex, argSize, argValue);
    }

    native int zeCommandListAppendLaunchKernel_native(long commandListHandler, long ptrZeKernelHandle, ZeGroupDispatch dispatch, Object signalEvent, int numWaitEvents, Object phWaitEvents);

    public int zeCommandListAppendLaunchKernel(long commandListHandler, long ptrZeKernelHandle, ZeGroupDispatch dispatch, Object signalEvent, int numWaitEvents, Object phWaitEvents) {
        return zeCommandListAppendLaunchKernel_native(commandListHandler, ptrZeKernelHandle, dispatch,signalEvent, numWaitEvents, phWaitEvents);
    }

    native int zeCommandListClose_native(long ptrZeCommandListHandle);

    public int zeCommandListClose(long ptrZeCommandListHandle) {
        return zeCommandListClose_native(ptrZeCommandListHandle);
    }

    native int zeCommandQueueExecuteCommandLists_native(long commandQueueHandlerPointer, int numCommandLists, long commandList, Object hFence);

    public int zeCommandQueueExecuteCommandLists(long commandQueueHandlerPointer, int numCommandLists, long commandList, Object hFence) {
        return zeCommandQueueExecuteCommandLists_native(commandQueueHandlerPointer, numCommandLists, commandList, hFence);
    }


    native int zeCommandQueueSynchronize_native(long commandQueueHandlerPointer, long timeOut);

    public int zeCommandQueueSynchronize(long commandQueueHandlerPointer, long timeOut) {
        return zeCommandQueueSynchronize_native(commandQueueHandlerPointer, timeOut);
    }
}
