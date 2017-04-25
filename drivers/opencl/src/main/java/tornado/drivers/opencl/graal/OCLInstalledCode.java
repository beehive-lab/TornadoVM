package tornado.drivers.opencl.graal;

import java.nio.ByteBuffer;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import tornado.api.Event;
import tornado.common.CallStack;
import tornado.common.TornadoInstalledCode;
import tornado.drivers.opencl.*;
import tornado.drivers.opencl.mm.OCLByteBuffer;
import tornado.drivers.opencl.mm.OCLCallStack;
import tornado.drivers.opencl.runtime.OCLMemoryRegions;
import tornado.meta.Meta;

import static tornado.common.RuntimeUtilities.humanReadableByteCount;
import static tornado.common.Tornado.*;
import static tornado.common.exceptions.TornadoInternalError.guarantee;

public class OCLInstalledCode extends InstalledCode implements TornadoInstalledCode {

    //TODO replace with a system property/Tornado setting
    private final ByteBuffer buffer = ByteBuffer.allocate(8);
    private final byte[] code;
    private final OCLDeviceContext deviceContext;
    private final OCLKernel kernel;
    private boolean valid;

    private final OCLKernelScheduler scheduler;
    private final int[] internalEvents = new int[1];

    public OCLInstalledCode(
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

    @Override
    public void invalidate() {
        if (valid) {
            kernel.cleanup();
            valid = false;
        }

    }

    @Override
    public boolean isValid() {
        return valid;
    }

    public void execute(final OCLByteBuffer stack, final Meta meta) {
        debug("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getId(),
                kernel.getName(), deviceContext.getDevice().getName());
        debug("\tstack    : buffer id=0x%x, address=0x%x relative=0x%x", stack.toBuffer(),
                stack.toAbsoluteAddress(), stack.toRelativeAddress());

        internalEvents[0] = stack.enqueueWrite();

        OCLMemoryRegions memoryRegions = (meta == null) ? null : meta.getProvider(OCLMemoryRegions.class);
        setKernelArgs(stack, memoryRegions);

        if (meta != null && meta.isParallel()) {
            internalEvents[0] = scheduler.submit(kernel, meta, internalEvents);
        } else {
            internalEvents[0] = deviceContext.enqueueTask(kernel, internalEvents);
        }

        if (meta != null && DUMP_PROFILES) {
//            meta.addProfile(task);
        }

        final int task = stack.enqueueRead(internalEvents);
        final Event event = deviceContext.resolveEvent(task);
        event.waitOn();

        debug("kernel completed: id=0x%x, method = %s, device = %s", kernel.getId(),
                kernel.getName(), deviceContext.getDevice().getName());
        debug("\tstatus   : %s", event.getStatus());

        if (ENABLE_PROFILING) {
            debug("\texecuting: %f seconds", event.getExecutionTime());
            debug("\ttotal    : %f seconds", event.getTotalTime());
        }
    }

    public void execute(final OCLCallStack stack) {
        execute(stack, null);
        debug("\tdeopt    : 0x%x", stack.getDeoptValue());
        debug("\treturn   : 0x%x", stack.getReturnValue());
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

    private void setKernelArgs(final OCLByteBuffer stack, OCLMemoryRegions regions) {
        int index = 0;

        if (deviceContext.needsBump()) {
            buffer.clear();
            buffer.putLong(deviceContext.getBumpBuffer());
            kernel.setArg(index, buffer);
            index++;
        }

        // heap
        buffer.clear();
        buffer.putLong(stack.toBuffer());
        kernel.setArg(index, buffer);
        index++;

        // stack pointer
        buffer.clear();
        buffer.putLong(stack.toRelativeAddress());
        kernel.setArg(index, buffer);
        index++;

        // constant
        if (regions != null && regions.getConstantSize() > 0) {
            kernel.setArg(index, ByteBuffer.wrap(regions.getConstantData()));
        } else {
            kernel.setArgUnused(index);
        }
        index++;

        // local
        if (regions != null && regions.getLocalSize() > 0) {
            info("\tallocating %s of local memory", humanReadableByteCount(regions.getLocalSize(), true));
            kernel.setLocalRegion(index, regions.getLocalSize());
        } else {
            kernel.setArgUnused(index);
        }
        index++;

        // private
        kernel.setArgUnused(index);
    }

    public int submit(final OCLCallStack stack, final Meta meta,
            final int[] events) {

        if (DEBUG) {
            info("kernel submitted: id=0x%x, method = %s, device =%s", kernel.getId(),
                    kernel.getName(), deviceContext.getDevice().getName());
            info("\tstack    : buffer id=0x%x, device=0x%x (0x%x)", stack.toBuffer(),
                    stack.toAbsoluteAddress(), stack.toRelativeAddress());
        }

        final int[] waitEvents;
        OCLMemoryRegions regions = (meta == null) ? null : meta.getProvider(OCLMemoryRegions.class);
        setKernelArgs(stack, regions);
        if (!stack.isOnDevice()) {
            internalEvents[0] = stack.enqueueWrite(events);
            waitEvents = internalEvents;
        } else {
            waitEvents = events;
        }

        guarantee(kernel != null, "kernel is null");

        int task;
        if (meta != null && meta.isParallel()) {
            task = scheduler.submit(kernel, meta, waitEvents);
        } else {
            task = deviceContext.enqueueTask(kernel, waitEvents);
        }

        if (meta != null && DUMP_PROFILES) {
//            meta.addProfile(deviceContext.resolveEvent(task));
        }

        if (ENABLE_EXCEPTIONS) {
            internalEvents[0] = task;
            task = stack.enqueueRead(internalEvents);
//            deviceContext.resolveEvent(task).waitOn();
//            stack.dump();
        }

        return task;
    }

    @Override
    public int launch(CallStack stack, Meta meta, int[] waitEvents) {
        return submit((OCLCallStack) stack, meta, waitEvents);
    }

}
