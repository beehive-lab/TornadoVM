package tornado.drivers.opencl;

import java.nio.ByteOrder;
import java.util.List;
import tornado.api.Event;
import tornado.common.Initialisable;
import static tornado.common.Tornado.getProperty;
import tornado.common.TornadoLogger;
import tornado.drivers.opencl.enums.OCLMemFlags;
import tornado.drivers.opencl.mm.OCLMemoryManager;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;

public class OCLDeviceContext extends TornadoLogger implements Initialisable {

    private static final long BUMP_BUFFER_SIZE = Long.decode(getProperty("tornado.opencl.bump.size", "0x100000"));
    private static final String[] BUMP_DEVICES = parseDevices(getProperty("tornado.opencl.bump.devices","Iris Pro"));

    private final OCLDevice device;
    private final OCLCommandQueue queue;
    private final OCLContext context;
    private final OCLMemoryManager memoryManager;
    private boolean needsBump;
    private final long bumpBuffer;

    protected OCLDeviceContext(
            OCLDevice device,
            OCLCommandQueue queue,
            OCLContext context) {
        this.device = device;
        this.queue = queue;
        this.context = context;
        this.memoryManager = new OCLMemoryManager(this);

        needsBump = false;
        for (String bumpDevice : BUMP_DEVICES) {
            if (device.getName().equalsIgnoreCase(bumpDevice.trim())) {
                needsBump = true;
                break;
            }
        }

        if (needsBump) {
            bumpBuffer = context.createBuffer(OCLMemFlags.CL_MEM_READ_WRITE, BUMP_BUFFER_SIZE);
            warn("device requires bump buffer: %s", device.getName());
        } else {
            bumpBuffer = -1;
        }
    }

    private static String[] parseDevices(String str){
        return str.split(";");
    }
    public List<OCLEvent> events() {
        return queue.getEvents();
    }

    public OCLDevice getDevice() {
        return device;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s", getDevice().getIndex(), getDevice().getName());
    }

    public OCLContext getPlatformContext() {
        return context;
    }

    public OCLMemoryManager getMemoryManager() {
        return memoryManager;
    }

    public void sync() {
//                queue.flush();
        queue.finish();
    }

    public long getDeviceId() {
        return device.getId();
    }

    public int enqueueBarrier() {
        return queue.enqueueBarrier();
    }

    public OCLProgram createProgram(byte[] source, long[] lengths) {
        return context.createProgram(source, lengths, this);
    }

    public void printEvents() {
        queue.printEvents();
    }

    public int enqueueTask(OCLKernel kernel, int[] events) {
        return queue.enqueueTask(kernel, events);
    }

    public int enqueueTask(OCLKernel kernel) {
        return queue.enqueueTask(kernel, null);
    }

    public int enqueueNDRangeKernel(OCLKernel kernel, int dim,
            long[] globalWorkOffset, long[] globalWorkSize,
            long[] localWorkSize, int[] waitEvents) {
        return queue.enqueueNDRangeKernel(kernel, dim, globalWorkOffset,
                globalWorkSize, localWorkSize, waitEvents);
    }

    public ByteOrder getByteOrder() {
        return device.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN
                : ByteOrder.BIG_ENDIAN;
    }

    /*
     * Asynchronous writes to device
     */
    public int enqueueWriteBuffer(long bufferId, long offset, long bytes,
            byte[] array, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, false, offset, bytes, array,
                waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes,
            int[] array, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, false, offset, bytes, array,
                waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes,
            long[] array, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, false, offset, bytes, array,
                waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes,
            short[] array, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, false, offset, bytes, array,
                waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes,
            float[] array, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, false, offset, bytes, array,
                waitEvents);
    }

    public int enqueueWriteBuffer(long bufferId, long offset, long bytes,
            double[] array, int[] waitEvents) {
        return queue.enqueueWrite(bufferId, false, offset, bytes, array,
                waitEvents);
    }

    /*
     * Asynchronouse reads from device
     */
    public int enqueueReadBuffer(long bufferId, long offset, long bytes,
            byte[] array, int[] waitEvents) {
        return queue.enqueueRead(bufferId, false, offset, bytes, array,
                waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes,
            int[] array, int[] waitEvents) {
        return queue.enqueueRead(bufferId, false, offset, bytes, array,
                waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes,
            long[] array, int[] waitEvents) {
        return queue.enqueueRead(bufferId, false, offset, bytes, array,
                waitEvents);
    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes,
            float[] array, int[] waitEvents) {
        return queue.enqueueRead(bufferId, false, offset, bytes, array,
                waitEvents);

    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes,
            double[] array, int[] waitEvents) {
        return queue.enqueueRead(bufferId, false, offset, bytes, array,
                waitEvents);

    }

    public int enqueueReadBuffer(long bufferId, long offset, long bytes,
            short[] array, int[] waitEvents) {
        return queue.enqueueRead(bufferId, false, offset, bytes, array,
                waitEvents);

    }

    /*
     * Synchronous writes to device
     */
    public void writeBuffer(long bufferId, long offset, long bytes,
            byte[] array, int[] waitEvents) {
        queue.enqueueWrite(bufferId, true, offset, bytes, array, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes,
            int[] array, int[] waitEvents) {
        queue.enqueueWrite(bufferId, true, offset, bytes, array, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes,
            long[] array, int[] waitEvents) {
        queue.enqueueWrite(bufferId, true, offset, bytes, array, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes,
            short[] array, int[] waitEvents) {
        queue.enqueueWrite(bufferId, true, offset, bytes, array, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes,
            float[] array, int[] waitEvents) {
        queue.enqueueWrite(bufferId, true, offset, bytes, array, waitEvents);
    }

    public void writeBuffer(long bufferId, long offset, long bytes,
            double[] array, int[] waitEvents) {
        queue.enqueueWrite(bufferId, true, offset, bytes, array, waitEvents);
    }

    /*
	 * Synchronous reads from device
     */
    public void readBuffer(long bufferId, long offset, long bytes,
            byte[] array, int[] waitEvents) {
        queue.enqueueRead(bufferId, true, offset, bytes, array, waitEvents);
    }

    public void readBuffer(long bufferId, long offset, long bytes, int[] array,
            int[] waitEvents) {
        queue.enqueueRead(bufferId, true, offset, bytes, array, waitEvents);
    }

    public void readBuffer(long bufferId, long offset, long bytes,
            long[] array, int[] waitEvents) {
        queue.enqueueRead(bufferId, true, offset, bytes, array, waitEvents);
    }

    public void readBuffer(long bufferId, long offset, long bytes,
            float[] array, int[] waitEvents) {
        queue.enqueueRead(bufferId, true, offset, bytes, array, waitEvents);

    }

    public void readBuffer(long bufferId, long offset, long bytes,
            double[] array, int[] waitEvents) {
        queue.enqueueRead(bufferId, true, offset, bytes, array, waitEvents);

    }

    public void readBuffer(long bufferId, long offset, long bytes,
            short[] array, int[] waitEvents) {
        queue.enqueueRead(bufferId, true, offset, bytes, array, waitEvents);

    }

    

    public int enqueueBarrier(int[] events) {
        return queue.enqueueBarrier(events);
    }

    public int enqueueMarker(int[] events) {
        return queue.enqueueMarker(events);
    }

    @Override
    public boolean isInitialised() {
        return memoryManager.isInitialised();
    }

    public void reset() {
        queue.reset();
        memoryManager.reset();

    }

    public OCLDeviceMapping asMapping() {
        return new OCLDeviceMapping(context.getPlatformIndex(), device.getIndex());
    }

    public void dumpEvents() {
        List<OCLEvent> events = queue.getEvents();

        if (events.isEmpty()) {
            return;
        }

        events.sort((OCLEvent o1, OCLEvent o2) -> {
            int result = Long.compare(o1.getCLSubmitTime(), o2.getCLSubmitTime());
            if (result == 0) {
                result = Long.compare(o1.getCLStartTime(), o2.getCLStartTime());
            }
            return result;
        });
        System.out.printf("Found %d events:\n", events.size());
        long base = events.get(0).getCLSubmitTime();
        System.out.println("id,submitted,start,end,status");
        events.stream().forEach((e) -> {
            System.out.printf("%s - 0x%x, %9d, %9d, %9d, %s\n", e.getName(), e.getId(), e.getCLSubmitTime() - base, e.getCLStartTime() - base, e.getCLEndTime() - base, e.getStatus());
        });

    }

    public boolean needsBump() {
        return needsBump;
    }

    public long getBumpBuffer() {
        return bumpBuffer;
    }
    
    public Event resolveEvent(int event){
        return queue.resolveEvent(event);
    }
    
    public void markEvent(){
        queue.markEvent();
    }

    public void flushEvents() {
        queue.flushEvents();
    }
}
