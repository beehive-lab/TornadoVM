package uk.ac.manchester.tornado.drivers.spirv.levelzero;

public class LevelZeroCommandList {

    private LevelZeroContext context;
    private ZeCommandQueueListHandle zeCommandList;

    public LevelZeroCommandList(LevelZeroContext context, ZeCommandQueueListHandle zeCommandList) {
        this.context = context;
        this.zeCommandList = zeCommandList;
    }

    public ZeCommandQueueListHandle getCommandListHandler() {
        return this.zeCommandList;
    }

    public LevelZeroContext getContext() {
        return this.context;
    }

    native int zeCommandListAppendLaunchKernel_native(long commandListHandler, long ptrZeKernelHandle, ZeGroupDispatch dispatch, Object signalEvent, int numWaitEvents, Object phWaitEvents);

    public int zeCommandListAppendLaunchKernel(long commandListHandler, long ptrZeKernelHandle, ZeGroupDispatch dispatch, Object signalEvent, int numWaitEvents, Object phWaitEvents) {
        return zeCommandListAppendLaunchKernel_native(commandListHandler, ptrZeKernelHandle, dispatch, signalEvent, numWaitEvents, phWaitEvents);
    }

    native int zeCommandListClose_native(long ptrZeCommandListHandle);

    public int zeCommandListClose(long ptrZeCommandListHandle) {
        return zeCommandListClose_native(ptrZeCommandListHandle);
    }

}
