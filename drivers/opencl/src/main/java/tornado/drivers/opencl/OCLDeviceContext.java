package tornado.drivers.opencl;

import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.Comparator;
import java.util.List;
import tornado.api.Event;
import tornado.common.Initialisable;
import tornado.common.TornadoLogger;
import tornado.drivers.opencl.mm.OCLMemoryManager;
import tornado.drivers.opencl.runtime.OCLDeviceMapping;

public class OCLDeviceContext extends TornadoLogger implements Initialisable {

	private final OCLDevice			device;
	private final OCLCommandQueue	queue;
	private final OCLContext		context;
	private final OCLMemoryManager  memoryManager;

	protected OCLDeviceContext(
			OCLDevice device,
			OCLCommandQueue queue,
			OCLContext context) {
		this.device = device;
		this.queue = queue;
		this.context = context;
		this.memoryManager = new OCLMemoryManager(this);
	}
	
	public List<OCLEvent> events(){
		return queue.getEvents();
	}
	
	public OCLDevice getDevice() {
		return device;
	}
	
	public String toString(){
		return String.format("[%d] %s",getDevice().getIndex(), getDevice().getName());
	}

	public OCLContext getPlatformContext() {
		return context;
	}
	
	public OCLMemoryManager getMemoryManager(){
		return memoryManager;
	}

	public void sync() {
//		queue.flush();
		queue.finish();
	}

	public long getDeviceId() {
		return device.getId();
	}

	public void enqueueBarrier() {
		queue.enqueueBarrier();
	}

	public OCLProgram createProgram(byte[] source, long[] lengths) {
		return context.createProgram(source, lengths, this);
	}

	public void printEvents() {
		queue.printEvents();
	}

	public OCLEvent enqueueTask(OCLKernel kernel, List<Event> events) {
		return queue.enqueueTask(kernel, serialiseEvents(events));
	}

	public OCLEvent enqueueTask(OCLKernel kernel) {
		return queue.enqueueTask(kernel, null);
	}

	public OCLEvent enqueueNDRangeKernel(OCLKernel kernel, int dim,
			long[] globalWorkOffset, long[] globalWorkSize,
			long[] localWorkSize, List<Event> waitEvents) {
		return queue.enqueueNDRangeKernel(kernel, dim, globalWorkOffset,
				globalWorkSize, localWorkSize,serialiseEvents(waitEvents));
	}

	@Deprecated
	public OCLEvent enqueueNDRangeKernel(OCLKernel kernel, int dim,
			long[] globalWorkOffset, long[] globalWorkSize, long[] localWorkSize) {
		return queue.enqueueNDRangeKernel(kernel, dim, globalWorkOffset,
				globalWorkSize, localWorkSize, null);
	}

	public ByteOrder getByteOrder() {
		return device.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN
				: ByteOrder.BIG_ENDIAN;
	}

	/*
	 * Asynchronous writes to device
	 */

	public OCLEvent enqueueWriteBuffer(long bufferId, long offset, long bytes,
			byte[] array, List<Event> waitEvents) {
		return queue.enqueueWrite(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));
	}

	public OCLEvent enqueueWriteBuffer(long bufferId, long offset, long bytes,
			int[] array, List<Event> waitEvents) {
		return queue.enqueueWrite(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));
	}

	public OCLEvent enqueueWriteBuffer(long bufferId, long offset, long bytes,
			long[] array, List<Event> waitEvents) {
		return queue.enqueueWrite(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));
	}

	public OCLEvent enqueueWriteBuffer(long bufferId, long offset, long bytes,
			short[] array, List<Event> waitEvents) {
		return queue.enqueueWrite(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));
	}

	public OCLEvent enqueueWriteBuffer(long bufferId, long offset, long bytes,
			float[] array, List<Event> waitEvents) {
		return queue.enqueueWrite(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));
	}

	public OCLEvent enqueueWriteBuffer(long bufferId, long offset, long bytes,
			double[] array, List<Event> waitEvents) {
		return queue.enqueueWrite(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));
	}

	/*
	 * Asynchronouse reads from device
	 */

	public OCLEvent enqueueReadBuffer(long bufferId, long offset, long bytes,
			byte[] array, List<Event> waitEvents) {
		return queue.enqueueRead(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));
	}

	public OCLEvent enqueueReadBuffer(long bufferId, long offset, long bytes,
			int[] array, List<Event> waitEvents) {
		return queue.enqueueRead(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));
	}

	public OCLEvent enqueueReadBuffer(long bufferId, long offset, long bytes,
			long[] array, List<Event> waitEvents) {
		return queue.enqueueRead(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));
	}

	public OCLEvent enqueueReadBuffer(long bufferId, long offset, long bytes,
			float[] array, List<Event> waitEvents) {
		return queue.enqueueRead(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));

	}

	public OCLEvent enqueueReadBuffer(long bufferId, long offset, long bytes,
			double[] array, List<Event> waitEvents) {
		return queue.enqueueRead(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));

	}

	public OCLEvent enqueueReadBuffer(long bufferId, long offset, long bytes,
			short[] array, List<Event> waitEvents) {
		return queue.enqueueRead(bufferId, false, offset, bytes, array,
				serialiseEvents(waitEvents));

	}

	/*
	 * Synchronous writes to device
	 */

	public void writeBuffer(long bufferId, long offset, long bytes,
			byte[] array, List<Event> waitEvents) {
		queue.enqueueWrite(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));
	}

	public void writeBuffer(long bufferId, long offset, long bytes,
			int[] array, List<Event> waitEvents) {
		queue.enqueueWrite(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));
	}

	public void writeBuffer(long bufferId, long offset, long bytes,
			long[] array, List<Event> waitEvents) {
		queue.enqueueWrite(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));
	}

	public void writeBuffer(long bufferId, long offset, long bytes,
			short[] array, List<Event> waitEvents) {
		queue.enqueueWrite(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));
	}

	public void writeBuffer(long bufferId, long offset, long bytes,
			float[] array, List<Event> waitEvents) {
		queue.enqueueWrite(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));
	}

	public void writeBuffer(long bufferId, long offset, long bytes,
			double[] array, List<Event> waitEvents) {
		queue.enqueueWrite(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));
	}

	/*
	 * Synchronous reads from device
	 */

	public void readBuffer(long bufferId, long offset, long bytes,
			byte[] array, List<Event> waitEvents) {
		queue.enqueueRead(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));
	}

	public void readBuffer(long bufferId, long offset, long bytes, int[] array,
			List<Event> waitEvents) {
		queue.enqueueRead(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));
	}

	public void readBuffer(long bufferId, long offset, long bytes,
			long[] array, List<Event> waitEvents) {
		queue.enqueueRead(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));
	}

	public void readBuffer(long bufferId, long offset, long bytes,
			float[] array, List<Event> waitEvents) {
		queue.enqueueRead(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));

	}

	public void readBuffer(long bufferId, long offset, long bytes,
			double[] array, List<Event> waitEvents) {
		queue.enqueueRead(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));

	}

	public void readBuffer(long bufferId, long offset, long bytes,
			short[] array, List<Event> waitEvents) {
		queue.enqueueRead(bufferId, true, offset, bytes, array, serialiseEvents(waitEvents));

	}
	
	private static long[] serialiseEvents(List<Event> events){
		if(events == null || events.isEmpty() )
			return null;
		
		int size = 0;
		for(Event e: events){
			if (e != null && e instanceof OCLEvent) size++;
		}
		
		final LongBuffer buffer = LongBuffer.allocate(size);
		for (Event e : events)
			if (e != null && e instanceof OCLEvent) buffer.put(((OCLEvent) e).getId());
		
		return (buffer.position() > 0) ? buffer.array() : null;
	}

	public Event enqueueBarrier(List<Event> events) {
		return queue.enqueueBarrier(serialiseEvents(events));
	}
	
	public Event enqueueMarker(List<Event> events) {
		return queue.enqueueMarker(serialiseEvents(events));
	}

	@Override
	public boolean isInitialised() {
		return memoryManager.isInitialised();
	}

	public void reset() {
		queue.reset();
		memoryManager.reset();
		
	}
	
	public OCLDeviceMapping asMapping(){
		return new OCLDeviceMapping(context.getPlatformIndex(),device.getIndex());
	}

	public void dumpEvents() {
		List<OCLEvent> events = queue.getEvents();
		
		if(events.isEmpty())
			return;
		
		events.sort(new Comparator<OCLEvent>(){

			@Override
			public int compare(OCLEvent o1, OCLEvent o2) {
				int result = Long.compare(o1.getCLSubmitTime(), o2.getCLSubmitTime());
				if(result == 0){
					result = Long.compare(o1.getCLStartTime(), o2.getCLStartTime());
				}
				return result;
			}
			
		});
		System.out.printf("Found %d events:\n",events.size());
		long base = events.get(0).getCLSubmitTime();
		System.out.println("id,submitted,start,end,status");
		for(OCLEvent e : events){
			System.out.printf("%s - 0x%x, %9d, %9d, %9d, %s\n",e.getName(),e.getId(),e.getCLSubmitTime() - base,e.getCLStartTime() - base,e.getCLEndTime()-base,e.getStatus() );
		}
		
		
	}
}
