package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.tornado.api.enums.TornadoExecutionStatus;
import uk.ac.manchester.tornado.drivers.EventDescriptor;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.LevelZeroTransferTimeStamp;
import uk.ac.manchester.tornado.drivers.spirv.timestamps.TimeStamp;

public class SPIRVLevelZeroEvent extends SPIRVEvent {

    private int eventId;
    private EventDescriptor descriptorId;
    private LevelZeroTransferTimeStamp start;
    private LevelZeroTransferTimeStamp stop;
    private long startTime;
    private long endTime;

    public SPIRVLevelZeroEvent(EventDescriptor descriptorId, int eventId, TimeStamp start, TimeStamp end) {
        this.descriptorId = descriptorId;
        this.eventId = eventId;
        this.start = (LevelZeroTransferTimeStamp) start;
        this.stop = (LevelZeroTransferTimeStamp) end;
    }

    @Override
    public void waitForEvents() {
        start.readTimeStamp();
        stop.readTimeStamp();
        start.flush();
        startTime = start.getTimeStamp();
        endTime = stop.getTimeStamp();
    }

    @Override
    public String getName() {
        return descriptorId.getNameDescription();
    }

    @Override
    public long getQueuedTime() {
        return 0;
    }

    @Override
    public long getSubmitTime() {
        return 0;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

    @Override
    public long getElapsedTime() {
        long value = (endTime - startTime) * start.getTimeResolution();
        return value;
    }

    @Override
    public long getDriverDispatchTime() {
        return 0;
    }

    @Override
    public double getElapsedTimeInSeconds() {
        return 0;
    }

    @Override
    public TornadoExecutionStatus getStatus() {
        return null;
    }

    @Override
    public double getTotalTimeInSeconds() {
        return 0;
    }

    @Override
    public void waitOn() {

    }

    @Override
    public void destroy() {
    }

}
