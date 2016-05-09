package tornado.drivers.opencl.enums;

public enum OCLEventInfo {
	CL_EVENT_COMMAND_QUEUE                      (0x11D0),
	CL_EVENT_COMMAND_TYPE                       (0x11D1),
	CL_EVENT_REFERENCE_COUNT                    (0x11D2),
	CL_EVENT_COMMAND_EXECUTION_STATUS           (0x11D3),
	CL_EVENT_CONTEXT                            (0x11D4);

	private final int	value;

	OCLEventInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
