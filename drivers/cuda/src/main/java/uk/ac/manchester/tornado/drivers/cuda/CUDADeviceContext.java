package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.TornadoDeviceContext;
import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.mm.CUDAMemoryManager;
import uk.ac.manchester.tornado.drivers.cuda.runtime.CUDATornadoDevice;
import uk.ac.manchester.tornado.runtime.common.Initialisable;
import uk.ac.manchester.tornado.runtime.common.TornadoInstalledCode;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import java.nio.ByteOrder;

public class CUDADeviceContext
        extends TornadoLogger implements Initialisable, TornadoDeviceContext {

    private final CUDADevice device;
    private final CUDAContext context;
    private final CUDAMemoryManager memoryManager;

    public CUDADeviceContext(CUDADevice device, CUDAContext context) {
        this.device = device;
        this.context = context;
        this.memoryManager = new CUDAMemoryManager(this);
    }

    @Override public CUDAMemoryManager getMemoryManager() {
        return memoryManager;
    }

    @Override public boolean needsBump() {
        return false;
    }

    @Override public boolean wasReset() {
        return false;
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
        return new PTXInstalledCode("foo");
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

    public int readBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        //TODO: Implement
        return 0;
    }

    public int readBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        //TODO: Implement
        return 0;
    }

    /*
     * ASYNC READS
     */

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        //TODO: Implement
        return 0;
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        //TODO: Implement
        return 0;
    }

    /*
     * SYNC WRITES
     */

    public void writeBuffer(long bifferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        //TODO: Implement
    }

    public void writeBuffer(long bifferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        //TODO: Implement
    }

    /*
     * ASYNC WRITES
     */

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, byte[] array, long hostOffset, int[] waitEvents) {
        //TODO: Implement
        return 0;
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes, int[] array, long hostOffset, int[] waitEvents) {
        //TODO: Implement
        return 0;
    }

    public Event resolveEvent(int event) {
        //TODO: Implement
        return null;
    }

    public void flushEvents() {
        //TODO: Implement
    }

    public void markEvent() {
        //TODO: Implement
    }

    public int enqueueBarrier() {
        //TODO: Implement
        return 0;
    }

    public int enqueueBarrier(int[] events) {
        //TODO: Implement
        return 0;
    }

    public int enqueueMarker() {
        //TODO: Implement
        return 0;
    }

    public int enqueueMarker(int[] events) {
        //TODO: Implement
        return 0;
    }

    public void sync() {
        //TODO: Implement
    }

    public void flush() {
        //TODO: Implement
    }

    public void reset() {
        //TODO: Implement
    }

    public void dumpEvents() {
        //TODO: Implement
    }
}
