package tornado.drivers.opencl;

import java.nio.ByteBuffer;
import tornado.api.Event;
import tornado.api.enums.TornadoExecutionStatus;
import tornado.common.RuntimeUtilities;
import static tornado.common.Tornado.ENABLE_PROFILING;
import static tornado.common.Tornado.OPENCL_WAIT_ACTIVE;
import tornado.common.TornadoLogger;
import tornado.drivers.opencl.enums.OCLCommandExecutionStatus;
import tornado.drivers.opencl.enums.OCLEventInfo;
import tornado.drivers.opencl.enums.OCLProfilingInfo;
import tornado.drivers.opencl.exceptions.OCLException;

public class OCLEvent extends TornadoLogger implements Event {
	private final long			id;
	private static final ByteBuffer	buffer = ByteBuffer.allocate(8);
	private final String		name;
	

	public OCLEvent(final long id, final String name) {
		this.id = id;
		this.name = name;
		//this.buffer = ByteBuffer.allocate(8);
		buffer.order(OpenCL.BYTE_ORDER);
	}

	native static void clGetEventInfo(long eventId, int param, byte[] buffer) throws OCLException;
	
	native static void clGetEventProfilingInfo(long eventId, int param,
			byte[] buffer) throws OCLException;
	
	native static void clWaitForEvents(long[] events) throws OCLException;

	private long readEventTime(int param) {
	
            if(!ENABLE_PROFILING || getCLStatus() != OCLCommandExecutionStatus.CL_COMPLETE){
                return -1;
            }
            
            long time = 0;
		buffer.clear();

		try {
			clGetEventProfilingInfo(id, param, buffer.array());
			time = buffer.getLong();
		} catch (OCLException e) {
			error(e.getMessage());
		}

		return time;
	}

	public long getCLQueuedTime() {
		return readEventTime(OCLProfilingInfo.CL_PROFILING_COMMAND_QUEUED
				.getValue());
	}

	public long getCLSubmitTime() {
		return readEventTime(OCLProfilingInfo.CL_PROFILING_COMMAND_SUBMIT
				.getValue());
	}

	public long getCLStartTime() {
		return readEventTime(OCLProfilingInfo.CL_PROFILING_COMMAND_START
				.getValue());
	}

	public long getCLEndTime() {
		return readEventTime(OCLProfilingInfo.CL_PROFILING_COMMAND_END
				.getValue());
	}
	
	public double getExecutionTime(){
		return RuntimeUtilities.elapsedTimeInSeconds(getCLStartTime(),
				getCLEndTime());
	}
	
	public double getTotalTime(){
		return RuntimeUtilities.elapsedTimeInSeconds(
					getCLSubmitTime(), getCLEndTime());
	}
	
	protected OCLCommandExecutionStatus getCLStatus(){
		int status = 0;
		buffer.clear();

		try {
			clGetEventInfo(id, OCLEventInfo.CL_EVENT_COMMAND_EXECUTION_STATUS.getValue(), buffer.array());
			status = buffer.getInt();
		} catch (OCLException e) {
			error(e.getMessage());
		}

		return OCLCommandExecutionStatus.toEnum(status);
	}
	
        @Override
	public void waitOn(){
		if(OPENCL_WAIT_ACTIVE){
                    waitOnActive();
                } else {
                    waitOnPassive();
                }
		

	}
        
        private void waitOnPassive(){
            try {
			clWaitForEvents(new long[]{id});
		} catch (OCLException e) {
			error(e.getMessage());
		}
        }
        
        private void waitOnActive(){
		

		try {
                    buffer.clear();
                    clGetEventInfo(id, OCLEventInfo.CL_EVENT_COMMAND_EXECUTION_STATUS.getValue(), buffer.array());
                    int status = buffer.getInt();
                    while(status > 0){
                        buffer.clear();
                        clGetEventInfo(id, OCLEventInfo.CL_EVENT_COMMAND_EXECUTION_STATUS.getValue(), buffer.array());
                        status = buffer.getInt();
                    }
		} catch (OCLException e) {
			error(e.getMessage());
		}

        }

	@Override
	public String toString() {
		return String.format("event: name=%s, active time=%.9f, total time=%.9f",
				name,getExecutionTime() , getTotalTime());
	}

	public long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public double getQueuedTime() {
		return RuntimeUtilities.elapsedTimeInSeconds(getCLSubmitTime(),
				getCLStartTime());
	}

	public TornadoExecutionStatus getStatus() {
		return getCLStatus().toTornadoExecutionStatus();
	}

	@Override
	public long getSubmitTime() {
		return getCLQueuedTime();
	}

	@Override
	public long getStartTime() {
		return getCLStartTime();
	}

	@Override
	public long getEndTime() {
		return getCLEndTime();
	}
}
