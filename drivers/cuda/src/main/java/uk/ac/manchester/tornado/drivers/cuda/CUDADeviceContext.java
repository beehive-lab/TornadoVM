package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
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

public class CUDADeviceContext
        extends TornadoLogger implements Initialisable, TornadoDeviceContext {

    private final CUDADevice device;
    private final CUDAMemoryManager memoryManager;
    private final CUDAStream stream;
    private final CUDAOccupancyCalculator occupancyCalculator;
    private final CUDACodeCache codeCache;
    private boolean wasReset;

    public CUDADeviceContext(CUDADevice device, CUDAStream stream) {
        this.device = device;
        this.stream = stream;

        codeCache = new CUDACodeCache(this);
        memoryManager = new CUDAMemoryManager(this);
        wasReset = false;
        occupancyCalculator = new CUDAOccupancyCalculator(device);
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


    public Event resolveEvent(int event) {
        //TODO: Implement
        unimplemented();
        return null;
    }

    public void flushEvents() {
        //TODO: Implement
        //unimplemented();
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
        //TODO: Implement
        //unimplemented();
    }

    public void flush() {
        //TODO: Implement
        //unimplemented();
    }

    public void reset() {
        //TODO: Implement
        //stream.reset();
        memoryManager.reset();
        wasReset = true;
    }

    public void dumpEvents() {
        //TODO: Implement
        unimplemented();
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
        int[] defaultBlocks = {1, 1, 1};
        int dims = module.dims;
        int threadLimitByModule = module.getNumberOfRegisters();

        for (int i = 0; i < dims && i < 3; i++) {
            int maxBlocks = Math.min(module.maxThreadsPerBlock(), module.domain.get(i).cardinality());
            defaultBlocks[i] = occupancyCalculator.getMaximalBlockSize(threadLimitByModule, maxBlocks, i);
        }

        System.out.println("Executing with blocks: " + Arrays.toString(defaultBlocks));
        return defaultBlocks;
    }

    private int[] calculateGrids(CUDAModule module, int[] blocks) {
        int[] defaultGrids = {1, 1, 1};
        int dims = module.dims;
        int[] maxGridSizes = device.getDeviceMaxGridSizes();

        for (int i = 0 ; i < dims && i < 3; i++) {
            int workSize = module.domain.get(i).cardinality();
            defaultGrids[i] = Math.min(workSize / blocks[i], maxGridSizes[i]);
        }

        System.out.println("Executing with grids: " + Arrays.toString(defaultGrids));
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

    /*
     * SYNC READS
     */

    public int readBuffer(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int readBuffer(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC READS
     */

    public int enqueueReadBuffer(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    /*
     * SYNC WRITES
     */

    public void writeBuffer(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        stream.enqueueWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long length, long[] array, int hostOffset, int[] waitEvents) {
        stream.enqueueWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    /*
     * ASYNC WRITES
     */

    public int enqueueWriteBuffer(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long length, long[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public boolean shouldCompile(String name) {
        return !codeCache.isCached(name);
    }
}
