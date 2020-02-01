package uk.ac.manchester.tornado.drivers.cuda.mm;

import uk.ac.manchester.tornado.api.mm.TornadoMemoryProvider;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

public class CUDAMemoryManager extends TornadoLogger implements TornadoMemoryProvider {

    @Override public long getCallStackSize() {
        return 0;
    }

    @Override public long getCallStackAllocated() {
        return 0;
    }

    @Override public long getCallStackRemaining() {
        return 0;
    }

    @Override public long getHeapSize() {
        return 0;
    }

    @Override public long getHeapRemaining() {
        return 0;
    }

    @Override public long getHeapAllocated() {
        return 0;
    }

    @Override public boolean isInitialised() {
        return false;
    }

    public CUDACallStack createCallStack(final int maxArgs) {
        CUDACallStack callStack = new CUDACallStack();
        return callStack;
    }
}
