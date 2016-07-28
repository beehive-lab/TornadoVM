package tornado.drivers.opencl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import tornado.api.Event;
import tornado.common.Tornado;
import static tornado.common.Tornado.*;
import tornado.common.TornadoLogger;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.enums.OCLCommandQueueInfo;
import tornado.drivers.opencl.exceptions.OCLException;
import tornado.runtime.EmptyEvent
;
public class OCLCommandQueue extends TornadoLogger {
	
	private final long				id;
	private final ByteBuffer		buffer;
	private final long				properties;
	private final List<OCLEvent>	events;
	private final int 				openclVersion;

	public OCLCommandQueue(long id, long properties, int version) {
		this.id = id;
		this.properties = properties;
		this.buffer = ByteBuffer.allocate(128);
		this.buffer.order(OpenCL.BYTE_ORDER);
		this.events = new ArrayList<>(8192);
		this.openclVersion = version;
	}

//	static {
//		System.loadLibrary(OpenCL.OPENCL_LIBRARY);
//	}

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

	
	private OCLEvent registerEvent(OCLEvent event){
		if(Tornado.LOG_EVENTS)
			events.add(event);
		return event;
	}
	
	public long getContextId() {
		long result = -1;
		buffer.clear();
		try {
			clGetCommandQueueInfo(id,
					OCLCommandQueueInfo.CL_QUEUE_CONTEXT.getValue(),
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
					OCLCommandQueueInfo.CL_QUEUE_DEVICE.getValue(),
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
	public Event enqueueBarrier() {
		return enqueueBarrier(null);
	}
	
	public Event enqueueMarker(){
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

	public OCLEvent enqueueKernel(OCLKernel kernel, int dims,
			long[] globalWorkOffset, long[] globalWorkSize,
			long[] localWorkSize, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent (new OCLEvent(
					clEnqueueNDRangeKernel(id, kernel.getId(), dims,
							globalWorkOffset, globalWorkSize, localWorkSize,
							waitEvents), "cl_kernel - " + kernel.getName()));
		} catch (OCLException e) {
			error(e.getMessage());
			e.printStackTrace();
		}
		
		if(FORCE_BLOCKING_API_CALLS)
			enqueueBarrier();

		return event;
	}

	public OCLEvent enqueueTask(OCLKernel kernel, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(clEnqueueTask(id, kernel.getId(), waitEvents),
					"cl_task - " + kernel.getName()));
		} catch (OCLException e) {
			error(e.getMessage());
		}

		if(FORCE_BLOCKING_API_CALLS)
			enqueueBarrier();

		return event;
	}

	public OCLEvent enqueueNDRangeKernel(OCLKernel kernel, int dim,
			long[] globalWorkOffset, long[] globalWorkSize,
			long[] localWorkSize, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(
					clEnqueueNDRangeKernel(id, kernel.getId(), dim, globalWorkOffset,
							globalWorkSize, localWorkSize, waitEvents),
					"kernel - " + kernel.getName()));
		} catch (OCLException e) {
			error(e.getMessage());
		}

		if(FORCE_BLOCKING_API_CALLS)
			enqueueBarrier();

		return event;
	}

	public OCLEvent enqueueWrite(long devicePtr, boolean blocking, long offset,
			long bytes, byte[] array, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("writeToDevice - byte[] 0x%x",offset)));
		} catch (OCLException e) {
			error(e.getMessage());
		}

		return event;
	}

	public OCLEvent enqueueWrite(long devicePtr, boolean blocking, long offset,
			long bytes, int[] array, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents),String.format("writeToDevice - int[] 0x%x",offset)));
		} catch (OCLException e) {
			error(e.getMessage());
		}

		return event;
	}
	
	public OCLEvent enqueueWrite(long devicePtr, boolean blocking, long offset,
			long bytes, short[] array, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("writeToDevice - short[] 0x%x",offset)));
		} catch (OCLException e) {
			error(e.getMessage());
		}

		return event;
	}
	
	public OCLEvent enqueueWrite(long devicePtr, boolean blocking, long offset,
			long bytes, long[] array, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("writeToDevice - long[] 0x%x",offset)));
		} catch (OCLException e) {
			error(e.getMessage());
		}

		return event;
	}

	public OCLEvent enqueueWrite(long devicePtr, boolean blocking, long offset,
			long bytes, float[] array, long[] waitEvents) {
		OCLEvent event = null;
		
		TornadoInternalError.guarantee(array != null, "null array");

		try {
			event = registerEvent(new OCLEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("writeToDevice - float[] 0x%x",offset)));
		} catch (OCLException e) {
			error(e.getMessage());
		}

		return event;
	}

	public OCLEvent enqueueWrite(long devicePtr, boolean blocking, long offset,
			long bytes, double[] array, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(writeArrayToDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("writeToDevice - double[] 0x%x",offset)));
		} catch (OCLException e) {
			error(e.getMessage());
		}

		return event;
	}

	public OCLEvent enqueueRead(long devicePtr, boolean blocking, long offset,
			long bytes, byte[] array, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("readFromDevice - byte[] 0x%x",offset)));
		} catch (OCLException e) {
			e.printStackTrace();
			error(e.getMessage());
		}

		return event;
	}

	public OCLEvent enqueueRead(long devicePtr, boolean blocking, long offset,
			long bytes, int[] array, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("readFromDevice - int[] 0x%x",offset)));
		} catch (OCLException e) {
			e.printStackTrace();
			error(e.getMessage());
		}

		return event;
	}
	
	public OCLEvent enqueueRead(long devicePtr, boolean blocking, long offset,
			long bytes, short[] array, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("readFromDevice - short[] 0x%x",offset)));
		} catch (OCLException e) {
			e.printStackTrace();
			error(e.getMessage());
		}

		return event;
	}
	
	public OCLEvent enqueueRead(long devicePtr, boolean blocking, long offset,
			long bytes, long[] array, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("readFromDevice - long[] 0x%x",offset)));
		} catch (OCLException e) {
			e.printStackTrace();
			error(e.getMessage());
		}

		return event;
	}

	public OCLEvent enqueueRead(long devicePtr, boolean blocking, long offset,
			long bytes, float[] array, long[] waitEvents) {
		OCLEvent event = null;

		TornadoInternalError.guarantee(array != null, "array is null");
		
		try {
			event = registerEvent(new OCLEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("readFromDevice - float[] 0x%x",offset)));
		} catch (OCLException e) {
			e.printStackTrace();
			error(e.getMessage());
		}

		return event;
	}

	public OCLEvent enqueueRead(long devicePtr, boolean blocking, long offset,
			long bytes, double[] array, long[] waitEvents) {
		OCLEvent event = null;

		try {
			event = registerEvent(new OCLEvent(readArrayFromDevice(id, array, (FORCE_BLOCKING_API_CALLS) ? true : blocking,
					offset, bytes, devicePtr, waitEvents), String.format("readFromDevice - double[] 0x%x",offset)));
		} catch (OCLException e) {
			e.printStackTrace();
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
		for (OCLEvent event : events) {
			System.out.println(event.toString());
		}
	}
	
	public Event enqueueBarrier(long[] events){
		return (openclVersion == 110) ? enqueueBarrier11(events) : enqueueBarrier12(events);
	}
	
	private Event enqueueBarrier11(long[] events){	
		try {
			clEnqueueWaitForEvents(id, events);
		} catch (OCLException e) {
			Tornado.fatal(e.getMessage());
		}
		return new EmptyEvent("barrier11");
	}
	
	private Event enqueueBarrier12(long[] waitEvents){
		OCLEvent event = null;
		try {
			event = registerEvent(new OCLEvent(clEnqueueBarrierWithWaitList(id,waitEvents),"barrier"));
		} catch (OCLException e) {
			Tornado.fatal(e.getMessage());
		}
		return event;
	}
	
	public Event enqueueMarker(long[] events){
		return (openclVersion == 110) ? enqueueMarker11(events) : enqueueMarker12(events);
	}
	
	private Event enqueueMarker11(long[] events){
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
	
	private OCLEvent enqueueMarker12(long[] waitEvents){
		OCLEvent event = null;
		try {
			event = registerEvent(new OCLEvent(clEnqueueMarkerWithWaitList(id,waitEvents),"marker"));
		} catch (OCLException e) {
			Tornado.fatal(e.getMessage());
		}
		return event;
	}

	public List<OCLEvent> getEvents(){
		return events;
	}
	
	public void reset(){
		events.clear();
	}
}
