package tornado.drivers.opencl.mm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tornado.runtime.api.CallStack;
import tornado.api.Event;
import tornado.common.ObjectBuffer;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.enums.Access;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.runtime.ObjectReference;
import tornado.runtime.api.TaskUtils;

public class OCLCallStack extends OCLByteBuffer implements CallStack<OCLDeviceContext> {

    private final Set<ObjectReference<OCLDeviceContext,?>> objectReads;
    private final Set<ObjectReference<OCLDeviceContext,?>> objectWrites;

    private final static int RESERVED_SLOTS = 4;

    private Event event;

    public OCLCallStack(long offset, int maxArgs, OCLDeviceContext device) {
        super(device, offset, (maxArgs + RESERVED_SLOTS) << 3);

        objectReads = new HashSet<ObjectReference<OCLDeviceContext,?>>();
        objectWrites = new HashSet<ObjectReference<OCLDeviceContext,?>>();

        // clear the buffer and set the mark at the beginning of the arguments
        buffer.clear();
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putInt(0);
        buffer.mark();
    }

    @Override
    public long getBufferOffset() {
        return 0;
    }

    public int getReservedSlots() {
        return RESERVED_SLOTS;
    }

    public int getMaxArgs() {
        return (int) ((bytes >> 3) - getReservedSlots());
    }

    public int getSlotCount() {
        return (int) bytes >> 3;
    }

    public void pushArgs(final Object[] args, final Access[] access,
            List<Event> waitEvents) {

        // System.out.println("call stack: push args waiting...");
        TaskUtils.waitForEvents(waitEvents);

        // long t0 = System.nanoTime();
        objectReads.clear();
        objectWrites.clear();

        buffer.putInt(args.length);

        List<Event> argTransfers = new ArrayList<Event>();
        argTransfers.addAll(waitEvents);

        // long t1 = System.nanoTime();
        for (int i = 0; i < args.length; i++) {
            final Object arg = args[i];

            if (arg == null) {
                buffer.putLong(0);
            } else if (RuntimeUtilities.isBoxedPrimitive(arg)
                    || arg.getClass().isPrimitive()) {
                Tornado.debug("arg[%d]: type=%s, value=%s", i, arg.getClass()
                        .getName(), arg.toString());
                PrimitiveSerialiser.put(buffer, arg, 8);
            } else if (arg instanceof ObjectReference<?,?>) {
                final ObjectReference<OCLDeviceContext,?> ref = (ObjectReference<OCLDeviceContext,?>) arg;
                Tornado.debug("arg[%d]: %s", i, ref.toString());

                final ObjectBuffer<?> argBuffer = ref.requestAccess(
                        deviceContext, access[i]);
                if (ref.hasOutstandingWrite()) {
                    Tornado.debug(
                            "arg[%d]: %s - waiting for outstanding write to complete",
                            i, ref.toString());
                    argTransfers.add(ref.getLastWrite().getEvent());
                }

                if (access[i] == Access.READ)
                    objectReads.add(ref);
                else
                    objectWrites.add(ref);

                Tornado.trace("arg[%d]: buffer @ 0x%x (0x%x)", i,
                        argBuffer.toAbsoluteAddress(),
                        argBuffer.toRelativeAddress());
                buffer.putLong(argBuffer.toAbsoluteAddress());
            }
        }
        // long t2 = System.nanoTime();

        event = enqueueWriteAfterAll(argTransfers);

        // argTransfers.add(enqueueWrite());
        // event = deviceContext.enqueueMarker(argTransfers);

        // long t3 = System.nanoTime();
        // System.out.printf("pushArgs: %f, %f, %f\n",RuntimeUtilities.elapsedTimeInSeconds(t0,
        // t1),RuntimeUtilities.elapsedTimeInSeconds(t1,
        // t2),RuntimeUtilities.elapsedTimeInSeconds(t2, t3));
    }

    public void reset() {
        for (int i = 0; i < 2; i++)
            buffer.putLong(i, 0);

        buffer.reset();
    }

    public long getDeoptValue() {
        return buffer.getLong(0);
    }

    public long getReturnValue() {
        return buffer.getLong(8);
    }

    public int getArgCount() {
        return buffer.getInt(8);
    }

    public String toString() {
        return String
                .format("Call Stack: max args = %d, device = %s, size = %s @ 0x%x (0x%x)",
                        getMaxArgs(), deviceContext.getDevice().getName(),
                        RuntimeUtilities.humanReadableByteCount(bytes, true),
                        toAbsoluteAddress(), toRelativeAddress());
    }

    @Override
    public void dump() {
        super.dump(8);
    }

    @Override
    public Event getEvent() {
        return event;
    }

    @Override
    public Set<ObjectReference<OCLDeviceContext,?>> getReadSet() {
        return objectReads;
    }

    @Override
    public Set<ObjectReference<OCLDeviceContext,?>> getWriteSet() {
        return objectWrites;
    }

}
