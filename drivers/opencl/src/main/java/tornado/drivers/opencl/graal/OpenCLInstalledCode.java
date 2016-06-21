package tornado.drivers.opencl.graal;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import tornado.api.Event;
import tornado.api.enums.TornadoExecutionStatus;
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
import tornado.runtime.EmptyEvent;
import tornado.runtime.ObjectReference;
import tornado.runtime.api.TaskUtils;

import com.oracle.graal.api.code.InstalledCode;
import com.oracle.graal.api.code.InvalidInstalledCodeException;
import com.oracle.graal.api.meta.ResolvedJavaMethod;

public class OpenCLInstalledCode extends InstalledCode implements TornadoInstalledCode {

	//TODO replace with a system property/Tornado setting
	private final static boolean	DEBUG		= false;

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

	public void execute(final OCLByteBuffer stack, final DomainTree domainTree) {
		Tornado.debug("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getId(),
				kernel.getName(), deviceContext.getDevice().getName());
		Tornado.debug("\tstack    : buffer id=0x%x, address=0x%x relative=0x%x", stack.toBuffer(),
				stack.toAbsoluteAddress(), stack.toRelativeAddress());

		List<Event> waitEvents = new ArrayList<Event>(1);
		waitEvents.add(stack.enqueueWrite());

		setKernelArgs(stack);

		OCLEvent task = null;
		if (domainTree == null || domainTree.getDepth() == 0) {
			task = deviceContext.enqueueTask(kernel, waitEvents);
		} else {	
			task = scheduler.submit(kernel, domainTree, waitEvents);
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

	public Event submit(final OCLCallStack stack, final DomainTree domainTree,
			final List<Event> events) {
//		long t0 = System.nanoTime();

		if (DEBUG) {
			Tornado.info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getId(),
					kernel.getName(), deviceContext.getDevice().getName());
			Tornado.info("\tstack    : buffer id=0x%x, device=0x%x (0x%x)", stack.toBuffer(),
					stack.toAbsoluteAddress(), stack.toRelativeAddress());
//			stack.dump();
		}

//		System.out.println("code submit wait for...");
//		TaskUtils.waitForEvents(events);
		
//		waitEvents.clear();
//		waitEvents.addAll(events);
//		if(stack.getEvent() != null && stack.getEvent().getStatus() != TornadoExecutionStatus.COMPLETE)
//			waitEvents.add(stack.getEvent());
//		long t1 = System.nanoTime();

//		long address = deviceContext.asMapping().getBackend().readHeapBaseAddress();
//		if(address != deviceContext.getMemoryManager().toAbsoluteAddress()){
//			Tornado.fatal("heap has moved!");
//		}
//		stack.write();
		setKernelArgs(stack);
		events.add( stack.enqueueWrite());
		
		if(Tornado.FORCE_BLOCKING_API_CALLS)
			deviceContext.sync();

		TornadoInternalError.guarantee(kernel != null, "kernel is null");
		
//		long t2 = System.nanoTime();
		OCLEvent task = null;
		if (domainTree == null || domainTree.getDepth() == 0) {
			task = deviceContext.enqueueTask(kernel, events);
		} else {
			task = scheduler.submit(kernel, domainTree, events);
		}
//		long t3 = System.nanoTime();
		/*
		 * this will update the deopt status on the host after task execution
		 */
		
		if(Tornado.FORCE_BLOCKING_API_CALLS)
			deviceContext.sync();
		
//		long t4 = System.nanoTime();
		/*
		 * update object refs with write event
		 */
//		for (ObjectReference<OCLDeviceContext,?> ref : stack.getWriteSet())
//			ref.setLastWrite(deviceContext, task);
//		long t5 = System.nanoTime();

//		System.out.printf("code-submit: %f, %f, %f, %f, %f\n",
//				RuntimeUtilities.elapsedTimeInSeconds(t0, t1),
//				RuntimeUtilities.elapsedTimeInSeconds(t1, t2),
//				RuntimeUtilities.elapsedTimeInSeconds(t2, t3),
//				RuntimeUtilities.elapsedTimeInSeconds(t3, t4),
//				RuntimeUtilities.elapsedTimeInSeconds(t4, t5));
		
		return (Tornado.ENABLE_EXCEPTIONS) ? stack.enqueueReadAfter(task) : task;
	}


	@Override
	public Event launch(CallStack stack, Meta meta, List<Event> waitEvents) {
		return submit((OCLCallStack) stack, meta.getProvider(DomainTree.class), waitEvents);
	}

}
