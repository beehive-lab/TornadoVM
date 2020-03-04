package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class CUDAEvent extends TornadoLogger implements Event {

    private byte[] eventWrapper;

    public CUDAEvent(byte[] bytes) {
        eventWrapper = bytes;
    }

    private native static void cuEventDestroy(byte[] eventWrapper);

    @Override
    public void waitForEvents() {
        unimplemented();
    }

    @Override
    public String getName() {
        unimplemented();
        return null;
    }

    @Override
    public long getSubmitTime() {
        unimplemented();
        return 0;
    }

    @Override
    public long getStartTime() {
        unimplemented();
        return 0;
    }

    @Override
    public long getEndTime() {
        unimplemented();
        return 0;
    }

    @Override
    public long getExecutionTime() {
        unimplemented();
        return 0;
    }

    @Override
    public double getExecutionTimeInSeconds() {
        unimplemented();
        return 0;
    }

    @Override
    public TornadoExecutionStatus getStatus() {
        unimplemented();
        return null;
    }

    @Override
    public double getTotalTimeInSeconds() {
        unimplemented();
        return 0;
    }

    @Override
    public void waitOn() {
        unimplemented();
    }

    public void destroy() {
        cuEventDestroy(eventWrapper);
    }
}
