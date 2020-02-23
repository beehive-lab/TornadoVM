package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDACallStack;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDAMemoryManager;
import uk.ac.manchester.tornado.drivers.cuda.runtime.CUDATornadoDevice;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.tasks.meta.Coarseness;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class CUDADeviceContext
        extends TornadoLogger implements Initialisable, TornadoDeviceContext {

    private final CUDADevice device;
    private final CUDAMemoryManager memoryManager;
    private final CUDAStream stream;
    private boolean wasReset;

    public CUDADeviceContext(CUDADevice device, CUDAStream stream) {
        this.device = device;
        this.stream = stream;

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

    }

    @Override public boolean isInitialised() {
        return false;
    }

    public CUDATornadoDevice asMapping() {
        return new CUDATornadoDevice(device.getIndex());
    }

    public TornadoInstalledCode installCode(PTXCompilationResult result) {
        CUDAModule module = new CUDAModule(result.getTargetCode());
        return new PTXInstalledCode(result.getName(), module, this);
    }

    public CUDADevice getDevice() {
        return device;
    }

    public ByteOrder getByteOrder() {
        return device.getByteOrder();
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

    /*
     * ASYNC READS
     */

    public int enqueueReadBuffer(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncRead(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
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

    /*
     * ASYNC WRITES
     */

    public int enqueueWriteBuffer(long bufferId, long offset, long length, byte[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long length, int[] array, long hostOffset, int[] waitEvents) {
        return stream.enqueueAsyncWrite(bufferId, offset, length, array, hostOffset, waitEvents);
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

    public int enqueueKernelLaunch(CUDAModule module, String functionName, CallStack stack, TaskMetaData meta, long batchThreads) {
        return stream.enqueueKernelLaunch(module,
                                          functionName,
                                          getKernelParams((CUDACallStack) stack),
                                          calculateGrids(meta),
                                          calculateBlocks(meta)
        );
    }

    private int[] calculateBlocks(TaskMetaData meta) {
        int[] defaultBlocks = {1, 1, 1};
        int dims = 0; // meta.getDims(); - This is not yet implemented as it comes from the compiler.

        for (int i = 0; i < dims && i < 3; i++) {
            //TODO: Use meta info to calculate this
            defaultBlocks[i] = 1;
        }

        return defaultBlocks;
    }

    private int[] calculateGrids(TaskMetaData meta) {
        int[] defaultGrids = {1, 1, 1};
        int dims = 0; // meta.getDims(); - This is not yet implemented as it comes from the compiler.

        for (int i = 0 ; i < dims && i < 3; i++) {
            //TODO: Use meta info to calculate this
            defaultGrids[i] = 1;
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
}
