package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDACallStack;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDAMemoryManager;
import uk.ac.manchester.tornado.drivers.cuda.runtime.CUDATornadoDevice;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.api.runtime.TornadoRuntime.getProperty;

public class CUDADeviceContext
        extends TornadoLogger implements Initialisable, TornadoDeviceContext {

    private final CUDADevice device;
    private final CUDAMemoryManager memoryManager;
    private final CUDAStream stream;
    private final CUDACodeCache codeCache;
    private boolean wasReset;

    public CUDADeviceContext(CUDADevice device, CUDAStream stream) {
        this.device = device;
        this.stream = stream;

        codeCache = new CUDACodeCache(this);
        memoryManager = new CUDAMemoryManager(this);
        wasReset = false;
    }

    @Override public CUDAMemoryManager getMemoryManager() {
        return memoryManager;
    }

    @Override public boolean needsBump() {
        return false;
    }

    @Override public boolean wasReset() {
        return wasReset;
    }

    @Override public void setResetToFalse() {
        wasReset = false;
    }

    @Override public boolean isInitialised() {
        return false;
    }

    public CUDATornadoDevice asMapping() {
        return new CUDATornadoDevice(device.getIndex());
    }

    public TornadoInstalledCode installCode(PTXCompilationResult result) {
        return codeCache.installSource(result);
    }

    public CUDADevice getDevice() {
        return device;
    }

    public ByteOrder getByteOrder() {
        return device.getByteOrder();
    }


    public CUDAEvent resolveEvent(int event) {
        return stream.resolveEvent(event);
    }

    public void flushEvents() {
    }

    public void markEvent() {
        //TODO: Implement
        unimplemented();
    }

    public int enqueueBarrier() {
        //TODO: Implement
        unimplemented();
        return 0;
    }

    public int enqueueBarrier(int[] events) {
        //TODO: Implement
        unimplemented();
        return 0;
    }


    public int enqueueMarker() {
        //TODO: Implement
        unimplemented();
        return 0;
    }

    public int enqueueMarker(int[] events) {
        //TODO: Implement
        unimplemented();
        return 0;
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
        wasReset = true;
    }

    public void dumpEvents() {
        // TODO: Implement
        // This prints out all the current events
        //unimplemented();
    }

    public int enqueueKernelLaunch(CUDAModule module, CallStack stack, long batchThreads) {
        int[] blocks = calculateBlocks(module);
        return stream.enqueueKernelLaunch(
                module,
                getKernelParams((CUDACallStack) stack),
                calculateGrids(module, blocks),
                blocks
        );
    }

    private int[] calculateBlocks(CUDAModule module) {

        if (module.metaData.isLocalWorkDefined()) {
            return Arrays.stream(module.metaData.getLocalWork()).mapToInt(l -> (int) l).toArray();
        }

        // Otherwise calculate our own
        int[] defaultBlocks = {1, 1, 1};
        int dims = module.metaData.getDims();
        int totalWorkItems = 1;

        for (int i = 0; i < dims; i++) {
            totalWorkItems *= Math.max(module.metaData.getDomain().get(i).cardinality(), 1);
        }

        // This is the number of thread blocks in total, which is x*y*z
        int blocks = module.getMaximalBlocks(totalWorkItems);

        // For now give each dimension the same number of blocks
        // Ideally these would be proportionate to the domain cardinality
        for (int i = 0; i < dims; i++) {
            defaultBlocks[i] = (int) Math.pow(blocks, 1/(double)dims);
        }

        return defaultBlocks;
    }

    private int[] calculateGrids(CUDAModule module, int[] blocks) {
        int[] defaultGrids = {1, 1, 1};
        int dims = module.metaData.getDims();
        int[] maxGridSizes = device.getDeviceMaxGridSizes();

        for (int i = 0 ; i < dims && i < 3; i++) {
            int workSize = module.metaData.getDomain().get(i).cardinality();
            defaultGrids[i] = Math.max(Math.min(workSize / blocks[i], maxGridSizes[i]), 1);
        }

        return defaultGrids;
    }

    private byte[] getKernelParams(CUDACallStack stack) {
        ByteBuffer args = ByteBuffer.allocate(17);
        args.order(getByteOrder());

        // Heap pointer
        args.putLong(memoryManager.toBuffer());

        // Stack pointer
        if (!stack.isOnDevice()) stack.write();
        long address = stack.getAddress();
        args.putLong(address);

        // Arg start
        args.put(stack.getArgPos());


        return args.array();
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
    public int readBuffer(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long length, float[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long length, double[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC READS
     */
    public int enqueueReadBuffer(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long length, float[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long length, double[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    /*
     * SYNC WRITES
     */
    public void writeBuffer(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long length, long[] array, int hostOffset, int[] waitEvents) {
        stream.enqueueWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long length, float[] array, int hostOffset, int[] waitEvents) {
        stream.enqueueWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long length, double[] array, int hostOffset, int[] waitEvents) {
        stream.enqueueWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC WRITES
     */
    public int enqueueWriteBuffer(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long length, short[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long length, char[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long length, float[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long length, double[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }
}
