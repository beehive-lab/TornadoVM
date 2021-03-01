package uk.ac.manchester.tornado.drivers.spirv.mm;

import uk.ac.manchester.tornado.drivers.spirv.SPIRVDeviceContext;
import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;

import java.nio.ByteBuffer;
import java.util.HashMap;

// FIXME <REFACTOR> THis class has similarities with the rest of the backends
public class SPIRVCallStack extends SPIRVByteBuffer implements CallStack {

    public static final int RESERVED_SLOTS = 3;

    private boolean onDevice;
    private byte argStart;

    public SPIRVCallStack(long numBytes, long numArgs, SPIRVDeviceContext deviceContext) {
        super(numBytes, (numArgs + RESERVED_SLOTS) << 3, deviceContext);
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
        return 0;
    }

    @Override
    public int getArgCount() {
        return 0;
    }

    @Override
    public void push(Object arg) {

    }

    @Override
    public void push(Object arg, DeviceObjectState state) {

    }

    @Override
    public boolean isOnDevice() {
        return false;
    }

    @Override
    public void dump() {

    }

    @Override
    public void setHeader(HashMap<Integer, Integer> map) {

    }
}
