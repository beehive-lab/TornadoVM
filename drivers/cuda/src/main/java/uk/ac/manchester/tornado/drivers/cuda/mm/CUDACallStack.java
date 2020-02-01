package uk.ac.manchester.tornado.drivers.cuda.mm;

import uk.ac.manchester.tornado.runtime.common.CallStack;
import uk.ac.manchester.tornado.runtime.common.DeviceObjectState;

public class CUDACallStack implements CallStack {
    @Override public void reset() {

    }

    @Override public long getDeoptValue() {
        return 0;
    }

    @Override public long getReturnValue() {
        return 0;
    }

    @Override public int getArgCount() {
        return 0;
    }

    @Override public void push(Object arg) {

    }

    @Override public void push(Object arg, DeviceObjectState state) {

    }

    @Override public boolean isOnDevice() {
        return false;
    }

    @Override public void dump() {

    }

    @Override public void clearProfiling() {

    }

    @Override public long getInvokeCount() {
        return 0;
    }

    @Override public double getTimeTotal() {
        return 0;
    }

    @Override public double getTimeMean() {
        return 0;
    }

    @Override public double getTimeMin() {
        return 0;
    }

    @Override public double getTimeMax() {
        return 0;
    }

    @Override public double getTimeSD() {
        return 0;
    }
}
