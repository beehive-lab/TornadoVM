package uk.ac.manchester.tornado.drivers.ptx;

import uk.ac.manchester.tornado.api.common.Event;
import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.runtime.common.RuntimeUtilities;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class PTXEvent extends TornadoLogger implements Event {

    protected static final long DEFAULT_TAG = 0x12;

    // @formatter:off
    protected static final String[] EVENT_DESCRIPTIONS = {
            "kernel - serial",
            "kernel - parallel",
            "writeToDevice - byte[]",
            "writeToDevice - short[]",
            "writeToDevice - int[]",
            "writeToDevice - long[]",
            "writeToDevice - float[]",
            "writeToDevice - double[]",
            "readFromDevice - byte[]",
            "readFromDevice - short[]",
            "readFromDevice - int[]",
            "readFromDevice - long[]",
            "readFromDevice - float[]",
            "readFromDevice - double[]",
            "sync - marker",
            "sync - barrier",
            "none"
    };
    // @formatter:on

    protected static final int DESC_SERIAL_KERNEL = 0;
    protected static final int DESC_PARALLEL_KERNEL = 1;
    protected static final int DESC_WRITE_BYTE = 2;
    protected static final int DESC_WRITE_SHORT = 3;
    protected static final int DESC_WRITE_INT = 4;
    protected static final int DESC_WRITE_LONG = 5;
    protected static final int DESC_WRITE_FLOAT = 6;
    protected static final int DESC_WRITE_DOUBLE = 7;
    protected static final int DESC_READ_BYTE = 8;
    protected static final int DESC_READ_SHORT = 9;
    protected static final int DESC_READ_INT = 10;
    protected static final int DESC_READ_LONG = 11;
    protected static final int DESC_READ_FLOAT = 12;
    protected static final int DESC_READ_DOUBLE = 13;
    protected static final int DESC_SYNC_MARKER = 14;
    protected static final int DESC_SYNC_BARRIER = 15;
    protected static final int EVENT_NONE = 16;


    /**
     * Wrapper containing two serialized CUevent structs.
     * Between the two events, on the same CUDA stream has been registered another API call described by the value of {@link PTXEvent#description}.
     * We measure the time difference between the two events to get the duration of the API call.
     *
     * <p>
     * The first position (eventWrapper[0]) contains the beforeEvent
     * The second position eventWrapper[1] contains the afterEvent.
     */
    private final byte[][] eventWrapper;

    private boolean isCompleted;
    private final String description;
    private final long tag;
    private final String name;

    public PTXEvent(byte[][] bytes, int descriptorId, long tag) {
        eventWrapper = bytes;
        this.description = EVENT_DESCRIPTIONS[descriptorId];
        this.tag = tag;
        this.name = String.format("%s: 0x%x", description, tag);
        isCompleted = false;
    }

    private native static long cuEventDestroy(byte[] eventWrapper);

    private native static void cuEventSynchronize(byte[][] wrappers);

    private native static boolean cuEventQuery(byte[] eventWrapper);

    private native static long cuEventElapsedTime(byte[][] wrappers);

    public static void waitForEventArray(PTXEvent[] events) {
        byte[][] wrappers = new byte[events.length][];
        for (int i = 0; i < events.length; i++) {
            wrappers[i] = events[i].eventWrapper[1];
        }

        cuEventSynchronize(wrappers);
    }

    @Override
    public void waitForEvents() {
        waitForEventArray(new PTXEvent[]{this});
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSubmitTime() {
        return -1;
    }

    @Override
    public long getStartTime() {
        return -1;
    }

    @Override
    public long getEndTime() {
        return -1;
    }

    @Override
    public long getExecutionTime() {
        return cuEventElapsedTime(eventWrapper);
    }

    @Override
    public double getExecutionTimeInSeconds() {
        return RuntimeUtilities.elapsedTimeInSeconds(cuEventElapsedTime(eventWrapper));
    }

    @Override
    public TornadoExecutionStatus getStatus() {
        if (!isCompleted) isCompleted = cuEventQuery(eventWrapper[1]);

        return isCompleted ? TornadoExecutionStatus.COMPLETE : TornadoExecutionStatus.QUEUED;
    }

    @Override
    public double getTotalTimeInSeconds() {
        return getExecutionTimeInSeconds();
    }

    @Override
    public void waitOn() {
        waitForEvents();
    }

    public void destroy() {
        cuEventDestroy(eventWrapper[0]);
        cuEventDestroy(eventWrapper[1]);
    }
}
