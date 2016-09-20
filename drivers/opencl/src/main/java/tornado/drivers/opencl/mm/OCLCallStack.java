package tornado.drivers.opencl.mm;

import tornado.common.CallStack;
import tornado.common.DeviceObjectState;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import static tornado.common.Tornado.DEBUG;
import static tornado.common.Tornado.debug;
import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import tornado.drivers.opencl.OCLDeviceContext;

public class OCLCallStack extends OCLByteBuffer implements CallStack {

    private final static int RESERVED_SLOTS = 6;

    private final int numArgs;

    private boolean onDevice;

    public OCLCallStack(long offset, int numArgs, OCLDeviceContext device) {
        super(device, offset, (numArgs + RESERVED_SLOTS) << 3);
        this.numArgs = numArgs;

        // clear the buffer and set the mark at the beginning of the arguments
        buffer.clear();
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putLong(0);
        buffer.putLong(toAbsoluteAddress());
        buffer.putInt(0);
        buffer.putInt(numArgs);
        buffer.mark();

        onDevice = false;
    }

    @Override
    public boolean isOnDevice() {
        return onDevice;
    }

    @Override
    public long getBufferOffset() {
        return 0;
    }

    @Override
    public void write() {
        super.write();
        onDevice = true;
    }

    @Override
    public int enqueueWrite() {
        return enqueueWrite(null);
    }

    
    @Override
    public int enqueueWrite(int[] events) {
        onDevice = true;
        return super.enqueueWrite(events);
    }

    public int getReservedSlots() {
        return RESERVED_SLOTS;
    }

    public int getSlotCount() {
        return (int) bytes >> 3;
    }

    @Override
    public void reset() {
        for (int i = 0; i < 2; i++) {
            buffer.putLong(i, 0);
        }

        buffer.reset();
        onDevice = false;
    }

    @Override
    public long getDeoptValue() {
        return buffer.getLong(0);
    }

    @Override
    public long getReturnValue() {
        return buffer.getLong(8);
    }

    @Override
    public int getArgCount() {
        return buffer.getInt(10);
    }

    @Override
    public String toString() {
        return String
                .format("Call Stack: num args = %d, device = %s, size = %s @ 0x%x (0x%x)",
                        numArgs, deviceContext.getDevice().getName(),
                        RuntimeUtilities.humanReadableByteCount(bytes, true),
                        toAbsoluteAddress(), toRelativeAddress());
    }

    @Override
    public void dump() {
        super.dump(8);
    }

    @Override
    public void push(Object arg) {

        if (arg == null) {
            if (DEBUG) {
                debug("arg : (null)");
            }
            buffer.putLong(0);
        } else if (RuntimeUtilities.isBoxedPrimitive(arg)
                || arg.getClass().isPrimitive()) {
            if (DEBUG) {
                debug("arg : type=%s, value=%s", arg.getClass()
                        .getName(), arg.toString());
            }
            PrimitiveSerialiser.put(buffer, arg, 8);
        } else {
            shouldNotReachHere();
        }

    }

    @Override
    public void push(Object arg, DeviceObjectState state) {

        if (arg == null) {
            if (DEBUG) {
                debug("arg : (null)");
            }
            buffer.putLong(0);
        } else {
            if (DEBUG) {
                debug("arg : [0x%x] type=%s, value=%s, address=0x%x (0x%x)", arg.hashCode(), arg.getClass()
                        .getSimpleName(), arg, state.getAddress(), state.getOffset());
            }
            if (Tornado.OPENCL_USE_RELATIVE_ADDRESSES) {
                buffer.putLong(state.getOffset());
            } else {
                buffer.putLong(state.getAddress());
            }
        }

    }

    @Override
    public void clearProfiling() {
        // TODO Auto-generated method stub

    }

    @Override
    public long getInvokeCount() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getTimeTotal() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getTimeMean() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getTimeMin() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getTimeMax() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public double getTimeSD() {
        // TODO Auto-generated method stub
        return 0;
    }

}
