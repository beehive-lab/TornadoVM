package uk.ac.manchester.tornado.drivers.ptx.mm;

import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;

import java.nio.ByteBuffer;
import java.util.HashMap;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.isBoxedPrimitive;
import static uk.ac.manchester.tornado.runtime.common.Tornado.DEBUG;
import static uk.ac.manchester.tornado.runtime.common.Tornado.debug;

public class PTXCallStack extends PTXByteBuffer implements CallStack {

    public final static int RETURN_VALUE_INDEX_WITHIN_STACK = 0;
    public static final int RESERVED_SLOTS = 3;

    private boolean onDevice;
    private byte argStart;

    public PTXCallStack(long offset, int numArgs, PTXDeviceContext deviceContext) {
        super((numArgs + RESERVED_SLOTS) << 3, offset, deviceContext);

        // clear the buffer and set the mark at the beginning of the arguments
        buffer.clear();
        setArgStart(buffer);

        onDevice = false;
    }

    private void setArgStart(ByteBuffer buffer) {
        argStart = (byte) buffer.position();
        buffer.mark();
    }

    @Override
    public void reset() {
        buffer.mark();
        buffer.reset();
        onDevice = false;
    }

    @Override
    public long getDeoptValue() {
        return buffer.getLong(8);
    }

    @Override
    public long getReturnValue() {
        read();
        return buffer.getLong(0);
    }

    @Override
    public int getArgCount() {
        return buffer.getInt(10);
    }

    @Override
    public void push(Object arg) {
        if (arg == null) {
            if (DEBUG) {
                debug("arg : (null)");
            }
            buffer.putLong(0);
        } else if (isBoxedPrimitive(arg) || arg.getClass().isPrimitive()) {
            if (DEBUG) {
                debug("arg : type=%s, value=%s", arg.getClass().getName(), arg.toString());
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
                debug("arg : [0x%x] type=%s, value=%s, address=0x%x (0x%x)", arg.hashCode(), arg.getClass().getSimpleName(), arg, state.getAddress(), state.getOffset());
            }
            buffer.putLong(state.getAddress());
        }
    }

    @Override
    public boolean isOnDevice() {
        return onDevice;
    }

    @Override
    public void dump() {
        super.dump(8);
    }

    @Override
    public void setHeader(HashMap<Integer, Integer> map) {
        buffer.clear();
        for (int i = 0; i < RESERVED_SLOTS; i++) {
            if (map.containsKey(i)) {
                buffer.putLong(map.get(i));
            } else {
                buffer.putLong(0);
            }
        }
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

    public long getAddress() {
        return super.toAbsoluteAddress();
    }

    public byte getArgPos() {
        return argStart;
    }
}
