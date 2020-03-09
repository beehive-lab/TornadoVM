package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class CUDAEvent extends TornadoLogger implements Event {

    private final byte[] eventWrapper;
    private boolean isCompleted;
    private final EventDescription description;

    public CUDAEvent(byte[] bytes, EventDescription description) {
        eventWrapper = bytes;
        this.description = description;
        isCompleted = false;
    }

    private native static void cuEventDestroy(byte[] eventWrapper);
    private native static void cuEventSynchronize(byte[][] wrappers);
    private native static boolean cuEventQuery(byte[] eventWrapper);

    public static void waitForEventArray(CUDAEvent[] events) {
        byte[][] wrappers = new byte[events.length][];
        for (int i = 0; i < events.length; i++) {
            wrappers[i] = events[i].eventWrapper;
        }

        cuEventSynchronize(wrappers);
    }

    @Override
    public void waitForEvents() {
        waitForEventArray(new CUDAEvent[] {this});
    }

    @Override
    public String getName() {
        return description.name();
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
        if (!isCompleted) isCompleted = cuEventQuery(eventWrapper);

        return isCompleted ? TornadoExecutionStatus.COMPLETE : TornadoExecutionStatus.QUEUED;
    }

    @Override
    public double getTotalTimeInSeconds() {
        unimplemented();
        return 0;
    }

    @Override
    public void waitOn() {
        waitForEvents();
    }

    public void destroy() {
        cuEventDestroy(eventWrapper);
    }

    public enum EventDescription {
        KERNEL,
        MEMCPY_D_TO_H_BYTE,
        MEMCPY_D_TO_H_SHORT,
        MEMCPY_D_TO_H_CHAR,
        MEMCPY_D_TO_H_INT,
        MEMCPY_D_TO_H_LONG,
        MEMCPY_D_TO_H_FLOAT,
        MEMCPY_D_TO_H_DOUBLE,
        MEMCPY_H_TO_D_BYTE,
        MEMCPY_H_TO_D_SHORT,
        MEMCPY_H_TO_D_CHAR,
        MEMCPY_H_TO_D_INT,
        MEMCPY_H_TO_D_LONG,
        MEMCPY_H_TO_D_FLOAT,
        MEMCPY_H_TO_D_DOUBLE,
        BARRIER,
    }
}
