package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDACallStack;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDAMemoryManager;
import uk.ac.manchester.tornado.drivers.cuda.runtime.CUDATornadoDevice;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;
import static uk.ac.manchester.tornado.runtime.common.Tornado.FULL_DEBUG;

public class CUDADeviceContext extends TornadoLogger implements Initialisable, TornadoDeviceContext {

    private final CUDADevice device;
    private final CUDAMemoryManager memoryManager;
    private final CUDAStream stream;
    private final CUDACodeCache codeCache;
    private final CUDAScheduler scheduler;
    private boolean wasReset;

    public CUDADeviceContext(CUDADevice device, CUDAStream stream) {
        this.device = device;
        this.stream = stream;

        this.scheduler = new CUDAScheduler(device);
        codeCache = new CUDACodeCache(this);
        memoryManager = new CUDAMemoryManager(this);
        wasReset = false;
    }

    @Override
    public CUDAMemoryManager getMemoryManager() {
        return memoryManager;
    }

    @Override
    public boolean needsBump() {
        return false;
    }

    @Override
    public boolean wasReset() {
        return wasReset;
    }

    @Override
    public void setResetToFalse() {
        wasReset = false;
    }

    @Override
    public boolean isPlatformFPGA() {
        return false;
    }

    @Override
    public boolean useRelativeAddresses() {
        return false;
    }

    @Override
    public boolean isInitialised() {
        return false;
    }

    public CUDATornadoDevice asMapping() {
        return new CUDATornadoDevice(device.getIndex());
    }

    public TornadoInstalledCode installCode(PTXCompilationResult result, String resolvedMethodName) {
        return codeCache.installSource(result.getName(), result.getTargetCode(), result.getTaskMeta(), resolvedMethodName);
    }

    public TornadoInstalledCode installCode(String name, byte[] code, TaskMetaData taskMeta, String resolvedMethodName) {
        return codeCache.installSource(name, code, taskMeta, resolvedMethodName);
    }

    public TornadoInstalledCode getInstalledCode(String name) {
        return codeCache.getCachedCode(name);
    }

    public CUDADevice getDevice() {
        return device;
    }

    public ByteOrder getByteOrder() {
        return device.getByteOrder();
    }

    public Event resolveEvent(int event) {
        return stream.resolveEvent(event);
    }

    public void flushEvents() {
        // I don't think there is anything like this in CUDA so I am calling sync
        sync();
    }

    public void markEvent() {
        // TODO: Implement
        unimplemented();
    }

    public int enqueueBarrier() {
        return stream.enqueueBarrier();
    }

    public int enqueueBarrier(int[] events) {
        return stream.enqueueBarrier(events);
    }

    public int enqueueMarker() {
        // Since streams are always in-order in CUDA there is no difference
        // between marker and barrier
        return stream.enqueueBarrier();
    }

    public int enqueueMarker(int[] events) {
        // Since streams are always in-order in CUDA there is no difference
        // between marker and barrier
        return stream.enqueueBarrier(events);
    }

    public void sync() {
        stream.sync();
    }

    public void flush() {
        // I don't think there is anything like this in CUDA so I am calling sync
        sync();
    }

    public void reset() {
        stream.reset();
        memoryManager.reset();
        codeCache.reset();
        wasReset = true;
    }

    public void dumpEvents() {
        // TODO: Implement
        // This prints out all the current events
    }

    public int enqueueKernelLaunch(CUDAModule module, CallStack stack, long batchThreads) {
        int[] blocks = { 1, 1, 1 };
        int[] grids = { 1, 1, 1 };
        if (module.metaData.isParallel()) {
            scheduler.calculateGlobalWork(module.metaData, batchThreads);
            blocks = scheduler.calculateBlocks(module);
            grids = scheduler.calculateGrids(module, blocks);
        }
        int kernelLaunchEvent = stream.enqueueKernelLaunch(module, getKernelParams((CUDACallStack) stack), grids, blocks);
        updateProfiler(kernelLaunchEvent, module.metaData);
        return kernelLaunchEvent;
    }

    private byte[] getKernelParams(CUDACallStack stack) {
        ByteBuffer args = ByteBuffer.allocate(8);
        args.order(getByteOrder());

        // Stack pointer
        if (!stack.isOnDevice())
            stack.write();
        long address = stack.getAddress();
        args.putLong(address);

        return args.array();
    }

    private void updateProfiler(final int taskEvent, final TaskMetaData meta) {
        if (TornadoOptions.isProfilerEnabled()) {
            Event tornadoKernelEvent = resolveEvent(taskEvent);
            tornadoKernelEvent.waitForEvents();
            long timer = meta.getProfiler().getTimer(ProfilerType.TOTAL_KERNEL_TIME);
            // Register globalTime
            meta.getProfiler().setTimer(ProfilerType.TOTAL_KERNEL_TIME, timer + tornadoKernelEvent.getExecutionTime());
            // Register the time for the task
            meta.getProfiler().setTaskTimer(ProfilerType.TASK_KERNEL_TIME, meta.getId(), tornadoKernelEvent.getExecutionTime());
        }
    }

    public boolean shouldCompile(String name) {
        return !codeCache.isCached(name);
    }

    public void cleanup() {
        stream.cleanup();
    }

    /*
     * SYNC READS
     */
    public int readBuffer(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(address, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC READS
     */
    public int enqueueReadBuffer(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(address, length, array, hostOffset, waitEvents);
    }

    /*
     * SYNC WRITES
     */
    public void writeBuffer(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, long[] array, int hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, float[] array, int hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long address, long length, double[] array, int hostOffset, int[] waitEvents) {
        stream.enqueueWrite(address, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC WRITES
     */
    public int enqueueWriteBuffer(long address, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, short[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, char[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, float[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long address, long length, double[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(address, length, array, hostOffset, waitEvents);
    }
}
