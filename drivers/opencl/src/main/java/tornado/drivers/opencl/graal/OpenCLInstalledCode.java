package tornado.drivers.opencl.graal;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import tornado.api.Event;
import tornado.common.CallStack;
import tornado.common.Tornado;
import tornado.common.TornadoInstalledCode;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLEvent;
import tornado.drivers.opencl.OCLKernel;
import tornado.drivers.opencl.OCLKernelScheduler;
import tornado.drivers.opencl.OCLProgram;
import tornado.drivers.opencl.OCLScheduler;
import tornado.drivers.opencl.mm.OCLByteBuffer;
import tornado.drivers.opencl.mm.OCLCallStack;
import tornado.meta.Meta;
import tornado.meta.domain.DomainTree;
import com.oracle.graal.api.code.InstalledCode;
import com.oracle.graal.api.code.InvalidInstalledCodeException;
import static tornado.common.Tornado.DEBUG;

public class OpenCLInstalledCode extends InstalledCode implements TornadoInstalledCode {

	//TODO replace with a system property/Tornado setting

	private final ByteBuffer		buffer		= ByteBuffer.allocate(8);
	private final byte[]			code;
	private final OCLDeviceContext	deviceContext;
	private final OCLKernel			kernel;
	private boolean					valid;

	private final List<Event>		waitEvents	= new ArrayList<Event>();
	
	private final OCLKernelScheduler scheduler;

	public OpenCLInstalledCode(
			final String entryPoint,
			final byte[] code,
			final OCLDeviceContext deviceContext,
			final OCLProgram program,
			final OCLKernel kernel) {
		super(entryPoint);
		this.code = code;
		this.deviceContext = deviceContext;
		this.scheduler = OCLScheduler.create(deviceContext);
		this.kernel = kernel;
		valid = kernel != null;
		buffer.order(deviceContext.getByteOrder());
	}

	
	public void invalidate() {
		if (valid) {
			kernel.cleanup();
			valid = false;
		}

	}

	
	public boolean isValid() {
		return valid;
	}

	public void execute(final OCLByteBuffer stack, final Meta meta) {
		Tornado.debug("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getId(),
				kernel.getName(), deviceContext.getDevice().getName());
		Tornado.debug("\tstack    : buffer id=0x%x, address=0x%x relative=0x%x", stack.toBuffer(),
				stack.toAbsoluteAddress(), stack.toRelativeAddress());

		List<Event> waitEvents = new ArrayList<Event>(1);
		waitEvents.add(stack.enqueueWrite());

		setKernelArgs(stack);

		OCLEvent task = null;
		if (meta != null && meta.isParallel()) {
			task = scheduler.submit(kernel, meta, waitEvents);
		} else {
			task = deviceContext.enqueueTask(kernel, waitEvents);
		} 

		if(meta!=null){
			meta.addProfile(task);
		}
		
		stack.readAfter(task);

		Tornado.debug("kernel completed: id=0x%x, method = %s, device = %s", kernel.getId(),
				kernel.getName(), deviceContext.getDevice().getName());
		Tornado.debug("\tstatus   : %s", task.getStatus());
		Tornado.debug("\texecuting: %f seconds", task.getExecutionTime());
		Tornado.debug("\ttotal    : %f seconds", task.getTotalTime());
	}

	public void execute(final OCLCallStack stack) {
		execute(stack, null);
		Tornado.debug("\tdeopt    : 0x%x", stack.getDeoptValue());
		Tornado.debug("\treturn   : 0x%x", stack.getReturnValue());
	}

	@Override
	public Object executeVarargs(final Object... args) throws InvalidInstalledCodeException {

		// final OCLCallStack callStack = memoryManager.createCallStack(args.length);
		//
		// callStack.reset();
		// callStack.pushArgs(args);
		//
		// execute(callStack);

		// return callStack.getReturnValue();
		return null;
	}

	private String formatArray(final long[] array) {
		final StringBuilder sb = new StringBuilder();

		sb.append("[");
		for (final long value : array) {
			sb.append(" " + value);
		}
		sb.append(" ]");

		return sb.toString();
	}

	@Override
	public byte[] getCode() {
		return code;
	}

	@Override
	public long getCodeSize() {
		return code.length;
	}

	private final void setKernelArgs(final OCLByteBuffer stack) {
		buffer.clear();
		buffer.putLong(stack.toBuffer());
		kernel.setArg(0, buffer);

		buffer.clear();
		buffer.putLong(stack.toRelativeAddress());
		kernel.setArg(1, buffer);
	}

	public Event submit(final OCLCallStack stack, final Meta meta,
			final List<Event> events) {

		if (DEBUG) {
			Tornado.info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getId(),
					kernel.getName(), deviceContext.getDevice().getName());
			Tornado.info("\tstack    : buffer id=0x%x, device=0x%x (0x%x)", stack.toBuffer(),
					stack.toAbsoluteAddress(), stack.toRelativeAddress());
//			stack.dump();
		}


		setKernelArgs(stack);
		if(!stack.isOnDevice()){
			events.add( stack.enqueueWrite());
		}

		TornadoInternalError.guarantee(kernel != null, "kernel is null");
		
		OCLEvent task = null;
		if (meta.isParallel()) {
			task = scheduler.submit(kernel, meta, events);
		} else {
			task = deviceContext.enqueueTask(kernel, events);
		} 	
		
//		meta.addProfile(task);
		
		return (Tornado.ENABLE_EXCEPTIONS) ? stack.enqueueReadAfter(task) : task;
	}


	@Override
	public Event launch(CallStack stack, Meta meta, List<Event> waitEvents) {
		return submit((OCLCallStack) stack, meta, waitEvents);
	}

}
