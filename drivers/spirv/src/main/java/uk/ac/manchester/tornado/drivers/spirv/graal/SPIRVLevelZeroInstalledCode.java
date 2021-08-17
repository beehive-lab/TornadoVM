package uk.ac.manchester.tornado.drivers.spirv.graal;

import java.util.Arrays;

import uk.ac.manchester.tornado.api.WorkerGrid;
import uk.ac.manchester.tornado.api.mm.ObjectBuffer;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVLevelZeroCommandQueue;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVLevelZeroModule;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVModule;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroByteBuffer;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroCommandList;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroContext;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroDevice;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.LevelZeroKernel;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.Sizeof;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeDeviceProperties;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolDescription;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventPoolHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeEventScopeFlags;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeGroupDispatch;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeHostMemAllocDesc;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelHandle;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.ZeKernelTimeStampResult;
import uk.ac.manchester.tornado.drivers.spirv.levelzero.utils.LevelZeroUtils;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVByteBuffer;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVLevelZeroInstalledCode extends SPIRVInstalledCode {

    public static final String WARNING_THREAD_LOCAL = "[TornadoVM OCL] Warning: TornadoVM changed the user-defined local size to null. Now, the OpenCL driver will select the best configuration.";

    private static final int WARP_SIZE = 32;
    private boolean valid;
    private boolean ADJUST_IRREGULAR = false;
    private ZeKernelTimeStampResult resultKernel;
    private LevelZeroByteBuffer timeStampBuffer;
    private ZeEventPoolHandle eventPoolHandle;

    public SPIRVLevelZeroInstalledCode(String name, SPIRVModule spirvModule, SPIRVDeviceContext deviceContext) {
        super(name, spirvModule, deviceContext);
        this.valid = true;
    }

    @Override
    public int launchWithDependencies(CallStack stack, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads, int[] waitEvents) {
        throw new RuntimeException("Unimplemented");
    }

    private void updateProfiler(ZeKernelTimeStampResult resultKernel, final TaskMetaData meta) {
        long timer = meta.getProfiler().getTimer(ProfilerType.TOTAL_KERNEL_TIME);
        long kernelElapsedTime = (long) resultKernel.getKernelElapsedTime();
        // Register globalTime
        meta.getProfiler().setTimer(ProfilerType.TOTAL_KERNEL_TIME, timer + kernelElapsedTime);
        // Register the time for the task
        meta.getProfiler().setTaskTimer(ProfilerType.TASK_KERNEL_TIME, meta.getId(), kernelElapsedTime);
    }

    private void setKernelArgs(final SPIRVByteBuffer stack, final ObjectBuffer atomicSpace, TaskMetaData meta) {
        // Enqueue write
        stack.enqueueWrite(null);

        SPIRVLevelZeroModule module = (SPIRVLevelZeroModule) spirvModule;
        LevelZeroKernel levelZeroKernel = module.getKernel();
        ZeKernelHandle kernel = levelZeroKernel.getKernelHandle();

        int index = 0;
        // device's heap (on the device global's memory)
        int result = levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), index, Sizeof.LONG.getNumBytes(), stack.toBuffer());
        LevelZeroUtils.errorLog("zeKernelSetArgumentValue", result);
        index++;

        // index of the stack pointer (it is usually zero)
        result = levelZeroKernel.zeKernelSetArgumentValue(kernel.getPtrZeKernelHandle(), index, Sizeof.LONG.getNumBytes(), stack.toRelativeAddress());
        LevelZeroUtils.errorLog("zeKernelSetArgumentValue", result);
        index++;
    }

    @Override
    public int launchWithoutDependencies(CallStack stack, ObjectBuffer atomicSpace, TaskMetaData meta, long batchThreads) {
        SPIRVLevelZeroModule module = (SPIRVLevelZeroModule) spirvModule;
        LevelZeroKernel levelZeroKernel = module.getKernel();
        ZeKernelHandle kernel = levelZeroKernel.getKernelHandle();

        if (!stack.isOnDevice()) {
            setKernelArgs((SPIRVByteBuffer) stack, null, meta);
        }

        final long[] globalWork = new long[3];
        final long[] localWork = new long[3];
        Arrays.fill(globalWork, 1);
        Arrays.fill(localWork, 1);

        if (!meta.isGridSchedulerEnabled()) {
            int dims = meta.getDims();
            if (!meta.isGlobalWorkDefined()) {
                calculateGlobalWork(meta, batchThreads);
            }
            if (!meta.isLocalWorkDefined()) {
                calculateLocalWork(meta);
            }
            System.arraycopy(meta.getGlobalWork(), 0, globalWork, 0, dims);
            System.arraycopy(meta.getLocalWork(), 0, localWork, 0, dims);
        } else {
            checkLocalWorkGroupFitsOnDevice(meta);

            WorkerGrid grid = meta.getWorkerGrid(meta.getId());
            int dims = grid.dimension();

            System.arraycopy(grid.getGlobalWork(), 0, globalWork, 0, dims);

            if (grid.getLocalWork() != null) {
                System.arraycopy(grid.getLocalWork(), 0, localWork, 0, dims);
            }
        }

        if (meta.isThreadInfoEnabled()) {
            meta.printThreadDims();
        }

        // Prepare kernel for launch
        // A) Suggest scheduling parameters to level-zero
        int[] groupSizeX = new int[] { (int) localWork[0] };
        int[] groupSizeY = new int[] { (int) localWork[1] };
        int[] groupSizeZ = new int[] { (int) localWork[2] };

        int result = levelZeroKernel.zeKernelSuggestGroupSize(kernel.getPtrZeKernelHandle(), (int) globalWork[0], (int) globalWork[1], (int) globalWork[2], groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSuggestGroupSize", result);

        result = levelZeroKernel.zeKernelSetGroupSize(kernel.getPtrZeKernelHandle(), groupSizeX, groupSizeY, groupSizeZ);
        LevelZeroUtils.errorLog("zeKernelSetGroupSize", result);

        if (meta.isDebug()) {
            System.out.println("GLOBAL WORK:  " + Arrays.toString(globalWork));
            System.out.println("LOCAL WORK :  " + Arrays.toString(localWork));
            System.out.println("GroupX:  " + Arrays.toString(groupSizeX));
            System.out.println("GroupY:  " + Arrays.toString(groupSizeY));
            System.out.println("GroupZ:  " + Arrays.toString(groupSizeZ));
        }

        // Dispatch SPIR-V Kernel
        ZeGroupDispatch dispatch = new ZeGroupDispatch();
        dispatch.setGroupCountX(globalWork[0] / groupSizeX[0]);
        dispatch.setGroupCountY(globalWork[1] / groupSizeY[0]);
        dispatch.setGroupCountZ(globalWork[2] / groupSizeZ[0]);

        SPIRVLevelZeroCommandQueue commandQueue = (SPIRVLevelZeroCommandQueue) deviceContext.getSpirvContext().getCommandQueueForDevice(deviceContext.getDeviceIndex());
        LevelZeroCommandList commandList = commandQueue.getCommandList();

        ZeEventHandle kernelEventTimer = null;
        LevelZeroDevice device = commandQueue.getDevice();
        LevelZeroContext context = commandList.getContext();
        if (TornadoOptions.isProfilerEnabled()) {
            if (eventPoolHandle == null) {
                eventPoolHandle = new ZeEventPoolHandle();
            }
            kernelEventTimer = new ZeEventHandle();
            createEventPoolAndEvents(context, device, eventPoolHandle, ZeEventPoolFlags.ZE_EVENT_POOL_FLAG_KERNEL_TIMESTAMP, 1, kernelEventTimer);
        }

        // Launch the kernel on the Intel Integrated GPU
        result = commandList.zeCommandListAppendLaunchKernel(commandList.getCommandListHandlerPtr(), kernel.getPtrZeKernelHandle(), dispatch, kernelEventTimer, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendLaunchKernel", result);

        result = commandList.zeCommandListAppendBarrier(commandList.getCommandListHandlerPtr(), null, 0, null);
        LevelZeroUtils.errorLog("zeCommandListAppendBarrier", result);

        if (TornadoOptions.isProfilerEnabled()) {
            timeStampBuffer = new LevelZeroByteBuffer();
            ZeHostMemAllocDesc hostMemAllocDesc = new ZeHostMemAllocDesc();
            result = context.zeMemAllocHost(context.getDefaultContextPtr(), hostMemAllocDesc, Sizeof.ze_kernel_timestamp_result_t.getNumBytes(), 1, timeStampBuffer);
            LevelZeroUtils.errorLog("zeMemAllocHost", result);

            result = commandList.zeCommandListAppendQueryKernelTimestamps(commandList.getCommandListHandlerPtr(), 1, kernelEventTimer, timeStampBuffer, null, null, 0, null);
            LevelZeroUtils.errorLog("zeCommandListAppendQueryKernelTimestamps", result);

            solveKernelEvent(device);
            updateProfiler(resultKernel, meta);
        }

        return 0;
    }

    public void solveKernelEvent(LevelZeroDevice device) {
        ZeDeviceProperties deviceProperties = new ZeDeviceProperties();
        int result = device.zeDeviceGetProperties(device.getDeviceHandlerPtr(), deviceProperties);
        LevelZeroUtils.errorLog("zeDeviceGetProperties", result);
        resultKernel = new ZeKernelTimeStampResult(deviceProperties);
        deviceContext.flush(device.getDeviceIndex());

        resultKernel.resolve(timeStampBuffer);

        if (Tornado.DEBUG) {
            resultKernel.printTimers();
        }
    }

    private static void createEventPoolAndEvents(LevelZeroContext context, LevelZeroDevice device, ZeEventPoolHandle eventPoolHandle, int poolEventFlags, int poolSize, ZeEventHandle kernelEvent) {

        ZeEventPoolDescription eventPoolDescription = new ZeEventPoolDescription();

        eventPoolDescription.setCount(poolSize);
        eventPoolDescription.setFlags(poolEventFlags);

        int result = context.zeEventPoolCreate(context.getDefaultContextPtr(), eventPoolDescription, 1, device.getDeviceHandlerPtr(), eventPoolHandle);
        LevelZeroUtils.errorLog("zeEventPoolCreate", result);

        // Create Kernel Event
        ZeEventDescription eventDescription = new ZeEventDescription();
        eventDescription.setIndex(0);
        eventDescription.setSignal(ZeEventScopeFlags.ZE_EVENT_SCOPE_FLAG_HOST);
        eventDescription.setWait(ZeEventScopeFlags.ZE_EVENT_SCOPE_FLAG_HOST);
        result = context.zeEventCreate(eventPoolHandle, eventDescription, kernelEvent);
        LevelZeroUtils.errorLog("zeEventCreate", result);
    }

    private void calculateLocalWork(TaskMetaData meta) {
        final long[] localWork = meta.initLocalWork();

        switch (meta.getDims()) {
            case 3:
                localWork[2] = 1;
                localWork[1] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[1], meta.getGlobalWork()[1]);
                localWork[0] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[0], meta.getGlobalWork()[0]);
                break;
            case 2:
                localWork[1] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[1], meta.getGlobalWork()[1]);
                localWork[0] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[0], meta.getGlobalWork()[0]);
                break;
            case 1:
                localWork[0] = calculateGroupSize(calculateEffectiveMaxWorkItemSizes(meta)[0], meta.getGlobalWork()[0]);
                break;
            default:
                break;
        }
    }

    private int calculateGroupSize(long maxBlockSize, long globalWorkSize) {
        if (maxBlockSize == globalWorkSize) {
            maxBlockSize /= 4;
        }

        int value = (int) Math.min(maxBlockSize, globalWorkSize);
        if (value == 0) {
            return 1;
        }
        while (globalWorkSize % value != 0) {
            value--;
        }
        return value;
    }

    private long[] calculateEffectiveMaxWorkItemSizes(TaskMetaData metaData) {
        long[] intermediates = new long[] { 1, 1, 1 };

        long[] maxWorkItemSizes = deviceContext.getDevice().getDeviceMaxWorkItemSizes();

        switch (metaData.getDims()) {
            case 3:
                intermediates[2] = (long) Math.sqrt(maxWorkItemSizes[2]);
                intermediates[1] = (long) Math.sqrt(maxWorkItemSizes[1]);
                intermediates[0] = (long) Math.sqrt(maxWorkItemSizes[0]);
                break;
            case 2:
                intermediates[1] = (long) Math.sqrt(maxWorkItemSizes[1]);
                intermediates[0] = (long) Math.sqrt(maxWorkItemSizes[0]);
                break;
            case 1:
                intermediates[0] = maxWorkItemSizes[0];
                break;
            default:
                break;

        }
        return intermediates;
    }

    private void calculateGlobalWork(TaskMetaData meta, long batchThreads) {
        final long[] globalWork = meta.getGlobalWork();

        for (int i = 0; i < meta.getDims(); i++) {
            long value = (batchThreads <= 0) ? (long) (meta.getDomain().get(i).cardinality()) : batchThreads;
            if (ADJUST_IRREGULAR && (value % WARP_SIZE != 0)) {
                value = ((value / WARP_SIZE) + 1) * WARP_SIZE;
            }
            globalWork[i] = value;
        }
    }

    private void checkLocalWorkGroupFitsOnDevice(final TaskMetaData meta) {
        WorkerGrid grid = meta.getWorkerGrid(meta.getId());
        long[] local = grid.getLocalWork();
        if (local != null) {
            LevelZeroGridInfo gridInfo = new LevelZeroGridInfo(deviceContext, local);
            boolean checkedDimensions = gridInfo.checkGridDimensions();
            if (!checkedDimensions) {
                System.out.println(WARNING_THREAD_LOCAL);
                grid.setLocalWorkToNull();
                grid.setNumberOfWorkgroupsToNull();
            }
        }
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public void invalidate() {
        valid = false;
    }
}
