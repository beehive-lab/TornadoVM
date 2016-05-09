package tornado.drivers.opencl;

import tornado.drivers.opencl.enums.OCLBufferCreateType;
import tornado.drivers.opencl.enums.OCLCommandQueueProperties;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import sun.misc.Unsafe;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.TornadoLogger;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.exceptions.OCLException;

@SuppressWarnings("restriction")
public class OCLContext extends TornadoLogger {

	public static class OCLBufferResult {
		private final long oclBuffer;
		private final long address;
		private final int result;
	
		public OCLBufferResult(long oclBuffer, long address, int result){
			this.oclBuffer = oclBuffer;
			this.address = address;
			this.result = result;
		}
		
		public long getBuffer(){
			return oclBuffer;
		}
		
		public long getAddress(){
			return address;
		}
		
		public int getResult(){
			return result;
		}
	}
	
	private final long				id;
	private final List<OCLDevice>	devices;
	private final OCLCommandQueue[]	queues;
	private final List<OCLProgram>	programs;
	private final LongBuffer		allocatedRegions;
	private final ByteBuffer		buffer;
	private final Unsafe			unsafe;
	private final OCLPlatform		platform;

	public OCLContext(OCLPlatform platform, long id, List<OCLDevice> devices) {
		this.platform = platform;
		this.id = id;
		this.devices = devices;
		this.queues = new OCLCommandQueue[devices.size()];
		this.programs = new ArrayList<OCLProgram>();
		this.allocatedRegions = LongBuffer.allocate(64);
		this.buffer = ByteBuffer.allocate(128);
		this.buffer.order(OpenCL.BYTE_ORDER);
		this.unsafe = RuntimeUtilities.getUnsafe();
	}

	static {
		System.loadLibrary(OpenCL.OPENCL_LIBRARY);
	}

	native static void clReleaseContext(long id) throws OCLException;

	native static void clGetContextInfo(long id, int info, byte[] buffer)
			throws OCLException;

	native static long clCreateCommandQueue(long contextId, long deviceId,
			long properties) throws OCLException;

	native static long allocateOffHeapMemory(long size, long alignment);
	
	native static void freeOffHeapMemory(long address);
	
	native static ByteBuffer asByteBuffer(long address, long size);
	
	// creates an empty buffer on the device
	native static OCLBufferResult createBuffer(long contextId, long flags, long size, long  address)
			throws OCLException;

	native static long createSubBuffer(long buffer, long flags, int createType,
			byte[] createInfo) throws OCLException;

	native static void clReleaseMemObject(long memId) throws OCLException;

	native static long createArrayOnDevice(long contextId, long flags,
			byte[] buffer) throws OCLException;

	native static long createArrayOnDevice(long contextId, long flags,
			int[] buffer) throws OCLException;

	native static long createArrayOnDevice(long contextId, long flags,
			float[] buffer) throws OCLException;

	native static long createArrayOnDevice(long contextId, long flags,
			double[] buffer) throws OCLException;

	native static long clCreateProgramWithSource(long contextId, byte[] data,
			long lengths[]) throws OCLException;

	public int getNumDevices() {
		return devices.size();
	}

	public List<OCLDevice> devices() {
		return devices;
	}

	public OCLCommandQueue[] queues() {
		return queues;
	}

	public void createCommandQueue(int index, long properties) {
		OCLDevice device = devices.get(index);
		long queueId;
		try {
			queueId = clCreateCommandQueue(id, device.getId(), properties);
			
			final int platformVersion = Integer.parseInt(platform.getVersion().split(" ")[1].replace(".", "")) * 10;
			final int deviceVersion = Integer.parseInt(device.getVersion().split(" ")[1].replace(".", "")) * 10;
			Tornado.info("platform: version=%s (%s) on %s",platformVersion,platform.getVersion(), device.getName());
			Tornado.info("device  : version=%s (%s) on %s",deviceVersion,device.getVersion(), device.getName());
			
			queues[index] = new OCLCommandQueue(queueId, properties, deviceVersion);
		} catch (OCLException e) {
			error(e.getMessage());
		}
	}

	public void createCommandQueue(int index) {
		long properties = 0;
		properties |= OCLCommandQueueProperties.CL_QUEUE_PROFILING_ENABLE;
		if(Tornado.ENABLE_OOO_EXECUTION)
			properties |= OCLCommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
		createCommandQueue(index, properties);
	}

	public void createAllCommandQueues(long properties) {
		for (int i = 0; i < devices.size(); i++)
			createCommandQueue(i, properties);
	}

	public void createAllCommandQueues() {
		long properties = 0;
		properties |= OCLCommandQueueProperties.CL_QUEUE_PROFILING_ENABLE;
		if(Tornado.ENABLE_OOO_EXECUTION)
			properties |= OCLCommandQueueProperties.CL_QUEUE_OUT_OF_ORDER_EXEC_MODE_ENABLE;
		createAllCommandQueues(properties);
	}

	public OCLProgram createProgram(byte[] source, long[] lengths,
			OCLDeviceContext deviceContext) {
		OCLProgram program = null;

		try {
			program = new OCLProgram(clCreateProgramWithSource(id, source,
					lengths), deviceContext);
			programs.add(program);
		} catch (OCLException e) {
			error(e.getMessage());
		}

		return program;
	}

	public void cleanup() {

		try {
			for (OCLProgram program : programs)
				program.cleanup();

			while(allocatedRegions.hasRemaining())
				clReleaseMemObject(allocatedRegions.get());

			for (OCLCommandQueue queue : queues)
				if (queue != null) queue.cleanup();

			clReleaseContext(id);
		} catch (OCLException e) {
			error(e.getMessage());
			e.printStackTrace();
		}
	}

	@Override
	public String toString() {
		return String.format("id=0x%x, device count=%d", id, getNumDevices());
	}

	public OCLDeviceContext createDeviceContext(int index) {
		Tornado.debug("creating device context for device: %s", devices.get(index)
				.toString());
		createCommandQueue(index);
		return new OCLDeviceContext(devices.get(index), queues[index], this);
	}

	/**
	 * Allocates off-heap memory
	 * @param bytes
	 * @return
	 */
	public long allocate(long bytes, long alignment){
		final long address = allocateOffHeapMemory(bytes, alignment);
		if(address == 0)
			throw new TornadoInternalError("Unable to allocate off-heap memory");
		return address;
	}
	
	public ByteBuffer toByteBuffer(long address, long bytes){
		final ByteBuffer buffer = asByteBuffer(address,bytes);
		buffer.order(OpenCL.BYTE_ORDER);
		return buffer;
	}
	
	public long createBuffer(long flags, long bytes){
		return createBuffer(flags,bytes,0L);
	}
	
	
	
	public long createBuffer(long flags, long bytes, long address) {
		long devicePtr = 0;
		try {
			final OCLBufferResult result =  createBuffer(id, flags, bytes,address);
			devicePtr = result.getBuffer();
			allocatedRegions.put(devicePtr);
			info("buffer allocated %s @ 0x%x",
					RuntimeUtilities.humanReadableByteCount(bytes, true),
					devicePtr);
		} catch (OCLException e) {
			error(e.getMessage());
		}
		return devicePtr;
	}
	
	@Deprecated
	public long createSubBuffer(long bufferId, long flags, long offset, long bytes) {
		long devicePtr = 0;
		try {
			
			buffer.clear();
			buffer.putLong(offset);
			buffer.putLong(bytes);
			
			devicePtr = createSubBuffer(bufferId, flags, OCLBufferCreateType.CL_BUFFER_CREATE_TYPE_REGION.getValue(),buffer.array());
			debug("sub-buffer allocated %s @ 0x%x",
					RuntimeUtilities.humanReadableByteCount(bytes, true),
					devicePtr);
		} catch (OCLException e) {
			error(e.getMessage());
		}
		return devicePtr;
	}

	public int getPlatformIndex(){
		return platform.getIndex();
	}
}
