package tornado.drivers.opencl;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import tornado.api.Event;
import static tornado.common.Tornado.*;
import tornado.common.TornadoLogger;
import static tornado.common.exceptions.TornadoInternalError.*;
import static tornado.drivers.opencl.enums.OCLCommandQueueInfo.*;
import tornado.drivers.opencl.exceptions.OCLException;
import tornado.runtime.EmptyEvent;

public class OCLCommandQueue extends TornadoLogger {

    protected static final Event EMPTY_EVENT = new EmptyEvent();
    private static final int NUM_EVENTS_BUFFERS = 2;
    protected static final int EVENT_WINDOW = Integer.parseInt(getProperty("tornado.opencl.eventwindow","8192"));
    protected static final int MAX_WAIT_EVENTS = Integer.parseInt(getProperty("tornado.opencl.maxwaitevents","32"));
    
    protected static final String[] EVENT_DESCRIPTIONS = {
        "kernel - serial",
        "kernel - parallel",
        "writeToDevice - byte[]",
        "writeToDevice - short[]",
        "writeToDevice - int[]",
        "writeToDevice - long[]",
        "writeToDevice - float[]",
        "writeToDevice - double[]",
        "readFromDevice - byte[]",
        "readFromDevice - short[]",
        "readFromDevice - int[]",
        "readFromDevice - long[]",
        "readFromDevice - float[]",
        "readFromDevice - double[]",
        "sync - marker",
        "sync - barrier"
    };
    
    protected static final int DESC_SERIAL_KERNEL = 0;
    protected static final int DESC_PARALLEL_KERNEL = 1;
    protected static final int DESC_WRITE_BYTE = 2;
    protected static final int DESC_WRITE_SHORT = 3;
    protected static final int DESC_WRITE_INT = 4;
    protected static final int DESC_WRITE_LONG = 5;
    protected static final int DESC_WRITE_FLOAT = 6;
    protected static final int DESC_WRITE_DOUBLE = 7;
    protected static final int DESC_READ_BYTE = 8;
    protected static final int DESC_READ_SHORT = 9;
    protected static final int DESC_READ_INT = 10;
    protected static final int DESC_READ_LONG = 11;
    protected static final int DESC_READ_FLOAT = 12;
    protected static final int DESC_READ_DOUBLE = 13;
    protected static final int DESC_SYNC_MARKER = 14;
    protected static final int DESC_SYNC_BARRIER = 15;

    private final long[] waitEventsBuffer;
      
    private final long id;
    private final ByteBuffer buffer;
    private final long properties;
    protected long[] events;
    protected final long[][] eventsBuffers;
    protected final int[] descriptors;
    protected final long[] tags;
    private  int eventIndex;
    private final int openclVersion;
    
    private int eventsBufferIndex;
    private int eventMark;
    
    private final OCLEvent internalEvent;

    public OCLCommandQueue(long id, long properties, int version) {
        this.id = id;
        this.properties = properties;
        this.buffer = ByteBuffer.allocate(128);
        this.buffer.order(OpenCL.BYTE_ORDER);
        this.eventsBuffers = new long[NUM_EVENTS_BUFFERS][EVENT_WINDOW];
        this.eventsBufferIndex = 0;
        this.events = eventsBuffers[eventsBufferIndex];
        this.descriptors = new int[EVENT_WINDOW];
        this.tags = new long[EVENT_WINDOW];
        this.eventIndex = 0;
        this.eventMark = -1;
        this.openclVersion = version;
        this.waitEventsBuffer = new long[MAX_WAIT_EVENTS];
        this.internalEvent = new OCLEvent(this,0,0);
    }

    native static void clReleaseCommandQueue(long queueId) throws OCLException;

    native static void clGetCommandQueueInfo(long queueId, int info,
            byte[] buffer) throws OCLException;

    native static void clSetCommandQueueProperty(long queueId, long property,
            boolean value) throws OCLException;

    /**
     * Enqueues a kernel for execution on the specified command queue
     *
     * @param queueId
     * @param kernelId
     * @param dim
     * @param global_work_offset
     * @param global_work_size
     * @param local_work_size
     * @param events
     * @return eventId of this command
     * @throws OCLException
     */
    native static long clEnqueueNDRangeKernel(long queueId, long kernelId,
            int dim, long[] global_work_offset, long[] global_work_size,
            long[] local_work_size, long[] events) throws OCLException;

    native static long clEnqueueTask(long queueID, long kernelId, long[] events)
            throws OCLException;

    native static long clEnqueueReadBuffer(long queueId, long buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long clEnqueueWriteBuffer(long queueId, long buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long writeArrayToDevice(long queueId, byte[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long writeArrayToDevice(long queueId, short[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long writeArrayToDevice(long queueId, int[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long writeArrayToDevice(long queueId, long[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long writeArrayToDevice(long queueId, float[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long writeArrayToDevice(long queueId, double[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long readArrayFromDevice(long queueId, byte[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long readArrayFromDevice(long queueId, short[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long readArrayFromDevice(long queueId, int[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long readArrayFromDevice(long queueId, long[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long readArrayFromDevice(long queueId, float[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    native static long readArrayFromDevice(long queueId, double[] buffer,
            boolean blocking, long offset, long bytes, long ptr, long[] events)
            throws OCLException;

    /*
	 * for OpenCL 1.1 compatability
     */
    native static long clEnqueueMarker(long queueId) throws OCLException;

    native static void clEnqueueBarrier(long queueId) throws OCLException;

    native static void clEnqueueWaitForEvents(long queueId, long[] events) throws OCLException;

    /*
	 * for OpenCL 1.2 implemetations
     */
    native static long clEnqueueMarkerWithWaitList(long queueId, long[] events) throws OCLException;

    native static long clEnqueueBarrierWithWaitList(long queueId, long[] events) throws OCLException;

    native static void clFlush(long queueId) throws OCLException;

    native static void clFinish(long queueId) throws OCLException;

    public Event resolveEvent(int event){
        if(event == -1){
            return EMPTY_EVENT;
        }
        return new OCLEvent(this, event, events[event]);
    }
    
    
    public void markEvent(){
        eventMark = eventIndex;
    }
    
    public void flushEvents(){
//        System.out.println("flush");
        if(eventIndex >= EVENT_WINDOW - MAX_WAIT_EVENTS){
//            System.out.println("hit threshold releasing events...");
//            final int nextBuffer = (eventsBufferIndex + 1) % NUM_EVENTS_BUFFERS;
//            for(int i=eventMark;i<eventIndex;i++){
//                eventsBuffers[nextBuffer][i] = events[i];
//                eventsBuffers[nextBuffer][i-eventMark] = events[i];
//                events[i] = 0;
//            }
//            events = eventsBuffers[nextBuffer];
//            releaseEvents(eventsBufferIndex);
//            eventsBufferIndex = nextBuffer;
//            eventIndex = eventIndex - eventMark;
        }
    }
    
    private void releaseEvents(int index){
        
        final Runnable eventWalker = () -> {
            final long[] ids = eventsBuffers[index];
            for(int i=0;i<EVENT_WINDOW;i++){
                final long eventId = ids[i];
                if(eventId <= 0) continue;
                
                final OCLEvent e = new OCLEvent(this,i,eventId);
                
                switch(e.getStatus()){
                    default:
                        e.waitOn();
                    case COMPLETE:
//                        System.out.println("releasing: " + e.toString());
//                        e.release();
                        events[i] = 0;
                        break;
                    case ERROR:
                        shouldNotReachHere("Found event with error: %s",e.getName());
//                    default:
                }
            }
        };
        
//        TornadoRuntime.EXECUTOR.execute(eventWalker);
    }
    
    private int registerEvent(long eventId, int descriptorId, long tag) {
        final int currentEvent = eventIndex;
       
        if(events[currentEvent] != 0){
            internalEvent.setEventId(currentEvent, events[currentEvent]);
            
            internalEvent.release();
        }
        events[currentEvent] = eventId;
        descriptors[currentEvent] = descriptorId;
        tags[currentEvent] = tag;
        
        
        eventIndex = (eventIndex + 1) % EVENT_WINDOW;
        return currentEvent;
    }
    
    private boolean serialiseEvents(int[] dependencies) {
        if (dependencies == null || dependencies.length == 0 || !ENABLE_OOO_EXECUTION) {
            return false;
        }

        Arrays.fill(waitEventsBuffer,0);
//      System.out.printf("waitlist:\n");
        int index = 0;
        for (int i=0;i<dependencies.length;i++) {
            final int value = dependencies[i];
            if (value != -1) {
                index++;
                waitEventsBuffer[index] = events[value];
//                System.out.printf("[%d] 0x%x - %s 0x%x\n",index,events[value],EVENT_DESCRIPTIONS[descriptors[value]], tags[value]);
            }
        }
        waitEventsBuffer[0] = index;
        
        
        

        return (index > 0);
    }

    public long getContextId() {
        long result = -1;
        buffer.clear();
        try {
            clGetCommandQueueInfo(id,
                    CL_QUEUE_CONTEXT.getValue(),
                    buffer.array());
            result = buffer.getLong();
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return result;
    }

    public long getDeviceId() {
        long result = -1;
        buffer.clear();
        try {
            clGetCommandQueueInfo(id,
                    CL_QUEUE_DEVICE.getValue(),
                    buffer.array());
            result = buffer.getLong();
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return result;
    }

    public void setProperties(long properties, boolean value) {
        try {
            clSetCommandQueueProperty(id, properties, value);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    /**
     * Enqueues a barrier into the command queue of the specified device
     *
     */
    public int enqueueBarrier() {
        return enqueueBarrier(null);
    }

    public int enqueueMarker() {
        return enqueueMarker(null);
    }

    public long getProperties() {
        return properties;
    }

    public void cleanup() {
        try {
            clReleaseCommandQueue(id);
        } catch (OCLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return String.format("Queue: context=0x%x, device=0x%x",
                getContextId(), getDeviceId());
    }

    public long getId() {
        return id;
    }

    public int enqueueTask(OCLKernel kernel, int[] waitEvents) {
        int event = -1;

        try {
            event = registerEvent(clEnqueueTask(id, kernel.getId(), serialiseEvents(waitEvents)? waitEventsBuffer : null), DESC_SERIAL_KERNEL, kernel.getId());
        } catch (OCLException e) {
            error(e.getMessage());
        }

        if (FORCE_BLOCKING_API_CALLS) {
            enqueueBarrier();
        }

        return event;
    }

    public int enqueueNDRangeKernel(OCLKernel kernel, int dim,
            long[] globalWorkOffset, long[] globalWorkSize,
            long[] localWorkSize, int[] waitEvents) {
        int event = -1;

        try {
            event = registerEvent(
                    clEnqueueNDRangeKernel(id, kernel.getId(), dim, globalWorkOffset,
                            globalWorkSize, localWorkSize, serialiseEvents(waitEvents)? waitEventsBuffer : null),
                    DESC_PARALLEL_KERNEL, kernel.getId());
        } catch (OCLException e) {
            error(e.getMessage());
        }

        if (FORCE_BLOCKING_API_CALLS) {
            enqueueBarrier();
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset,
            long bytes, byte[] array, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;

        try {
            event = registerEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null), DESC_WRITE_BYTE, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset,
            long bytes, int[] array, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;

        try {
            event = registerEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null), DESC_WRITE_INT , offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset,
            long bytes, short[] array, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;

        try {
            event = registerEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null), DESC_WRITE_SHORT, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset,
            long bytes, long[] array, int[] waitEvents) {
        guarantee(array != null, "null array");
        
        int event = -1;

        try {
            event = registerEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null),DESC_WRITE_LONG, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset,
            long bytes, float[] array, int[] waitEvents) {
        guarantee(array != null, "null array");
        
        int event = -1;

        try {
            event = registerEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null), DESC_WRITE_FLOAT, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueWrite(long devicePtr, boolean blocking, long offset,
            long bytes, double[] array, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;

        try {
            event = registerEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null), DESC_WRITE_DOUBLE, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset,
            long bytes, byte[] array, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;

        try {
            event = registerEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null), DESC_READ_BYTE, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset,
            long bytes, int[] array, int[] waitEvents) {
        guarantee(array != null, "null array");
        int event = -1;

        try {
            event = registerEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null), DESC_READ_INT, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset,
            long bytes, short[] array, int[] waitEvents) {
        guarantee(array != null, "array is null");
        int event = -1;

        try {
            event = registerEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null), DESC_READ_SHORT, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset,
            long bytes, long[] array, int[] waitEvents) {
        guarantee(array != null, "array is null");
        int event = -1;

        try {
            event = registerEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null),DESC_READ_LONG, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset,
            long bytes, float[] array, int[] waitEvents) {
        guarantee(array != null, "array is null");
        int event = -1;

        try {
            event = registerEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null), DESC_READ_FLOAT, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public int enqueueRead(long devicePtr, boolean blocking, long offset,
            long bytes, double[] array, int[] waitEvents) {
        guarantee(array != null, "array is null");
        int event = -1;

        try {
            event = registerEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
                    offset, bytes, devicePtr, serialiseEvents(waitEvents)? waitEventsBuffer : null),DESC_READ_DOUBLE, offset);
        } catch (OCLException e) {
            error(e.getMessage());
        }

        return event;
    }

    public void finish() {
        try {
            clFinish(id);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void flush() {
        try {
            clFlush(id);
        } catch (OCLException e) {
            error(e.getMessage());
        }
    }

    public void printEvents() {
//        for (OCLEvent event : events) {
//            System.out.println(event.toString());
//        }
    }

    public int enqueueBarrier(int[] events) {
        final long[] waitEvents = serialiseEvents(events)? waitEventsBuffer : null;
        return (openclVersion == 110) ? enqueueBarrier11(waitEvents) : enqueueBarrier12(waitEvents);
    }

    private int enqueueBarrier11(long[] events) {
        try {
            clEnqueueWaitForEvents(id, events);
        } catch (OCLException e) {
           fatal(e.getMessage());
        }
        return -1;
    }

    private int enqueueBarrier12(long[] waitEvents) {
        int event = -1;
        try {
            event = registerEvent(clEnqueueBarrierWithWaitList(id, waitEvents), DESC_SYNC_BARRIER, 0x12);
        } catch (OCLException e) {
            fatal(e.getMessage());
        }
        return event;
    }

    public int enqueueMarker(int[] events) {
        final long[] waitEvents = serialiseEvents(events)? waitEventsBuffer : null;
        return (openclVersion == 110) ? enqueueMarker11(waitEvents) : enqueueMarker12(waitEvents);
    }

    private int enqueueMarker11(long[] events) {
        return enqueueBarrier11(events);
//		OCLEvent event = null;
//		
//		try {
//			event = registerEvent(new OCLEvent(clEnqueueMarker(id),"barrier"));
//		} catch (OCLException e) {
//			Tornado.fatal(e.getMessage());
//		}
//		return event;
    }

    private int enqueueMarker12(long[] waitEvents) {
        int event = -1;
        try {
            event = registerEvent(clEnqueueMarkerWithWaitList(id, waitEvents), DESC_SYNC_MARKER, 0x12);
        } catch (OCLException e) {
            fatal(e.getMessage());
        }
        return event;
    }

    public List<OCLEvent> getEvents() {
        return Collections.emptyList();
    }

    public void reset() {
        Arrays.fill(events,0);
        eventIndex = 0;
    }
}
