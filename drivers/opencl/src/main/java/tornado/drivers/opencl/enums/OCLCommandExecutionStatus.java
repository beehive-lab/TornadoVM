package tornado.drivers.opencl.enums;

import tornado.api.enums.TornadoExecutionStatus;

public enum OCLCommandExecutionStatus {
	CL_UNKNOWN									 (0x4),
	 CL_COMPLETE                                 (0x0),
	 CL_RUNNING                                  (0x1),
	 CL_SUBMITTED                                (0x2),
	 CL_QUEUED                                   (0x3),
	 CL_ERROR									 (-1);

	private final int	value;

	OCLCommandExecutionStatus(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
	
	public static OCLCommandExecutionStatus toEnum(final int v) {
		OCLCommandExecutionStatus result = OCLCommandExecutionStatus.CL_UNKNOWN;
		switch (v) {
			case 0:
				result = OCLCommandExecutionStatus.CL_COMPLETE;
				break;
			case 1:
				result = OCLCommandExecutionStatus.CL_RUNNING;
				break;
			case 2:
				result = OCLCommandExecutionStatus.CL_SUBMITTED;
				break;
			case 3:
				result = OCLCommandExecutionStatus.CL_QUEUED;
				break;
			default:
				result = OCLCommandExecutionStatus.CL_ERROR;
		}
		return result;
	}
	
	public TornadoExecutionStatus toTornadoExecutionStatus(){
		TornadoExecutionStatus result = TornadoExecutionStatus.UNKNOWN;
		switch(this){
			case CL_COMPLETE:
				result = TornadoExecutionStatus.COMPLETE;
				break;
			case CL_QUEUED:
				result = TornadoExecutionStatus.QUEUED;
				break;
			case CL_RUNNING:
				result = TornadoExecutionStatus.RUNNING;
				break;
			case CL_SUBMITTED:
				result = TornadoExecutionStatus.SUBMITTED;
				break;
			default:
				result = TornadoExecutionStatus.ERROR;
				break;	
		}
		return result;
	}
}
