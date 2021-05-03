package uk.ac.manchester.tornado.drivers.spirv.mm;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.isBoxedPrimitive;

import java.util.HashMap;

import uk.ac.manchester.tornado.drivers.common.mm.PrimitiveSerialiser;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;
import uk.ac.manchester.tornado.runtime.common.Tornado;

// FIXME <REFACTOR> THis class has similarities with the rest of the backends
public class SPIRVCallStack extends SPIRVByteBuffer implements CallStack {

    public static final int RESERVED_SLOTS = 3;

    private boolean onDevice;
    private byte argStart;

    public SPIRVCallStack(long offset, int numArgs, SPIRVDeviceContext deviceContext) {
        super(deviceContext, offset, (numArgs + RESERVED_SLOTS) << 3);
        buffer.clear();
        argStart = (byte) buffer.position();
        buffer.mark();
        onDevice = false;
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
            if (Tornado.DEBUG) {
                Tornado.debug("arg : (null)");
            }
            buffer.putLong(0);
        } else if (isBoxedPrimitive(arg) || arg.getClass().isPrimitive()) {
            if (Tornado.DEBUG) {
                Tornado.debug("arg : type=%s, value=%s", arg.getClass().getName(), arg.toString());
            }
            PrimitiveSerialiser.put(buffer, arg, 8);
        } else {
            shouldNotReachHere();
        }
    }

    @Override
    public void push(Object arg, DeviceObjectState state) {
        if (arg == null) {
            if (Tornado.DEBUG) {
                Tornado.debug("arg: (null)");
            }
            buffer.putLong(0);
        } else {
            if (Tornado.DEBUG) {
                Tornado.debug("arg : [0x%x] type=%s, value=%s, address=0x%x (0x%x)", arg.hashCode(), arg.getClass().getSimpleName(), arg, state.getAddress(), state.getOffset());
            }
            buffer.putLong(state.getAddress());
        }
    }

    @Override
    public boolean isOnDevice() {
        return onDevice;
    }

    @Override
    public void write() {
        super.write();
        this.onDevice = true;
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
}
