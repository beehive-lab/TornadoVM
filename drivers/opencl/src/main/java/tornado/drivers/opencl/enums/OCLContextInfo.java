package tornado.drivers.opencl.enums;

public enum OCLContextInfo {
	CL_CONTEXT_REFERENCE_COUNT(0x1080),
	CL_CONTEXT_DEVICES(0x1081),
	CL_CONTEXT_PROPERTIES(0x1082),
	CL_CONTEXT_NUM_DEVICES(0x1083);

	private final int	value;

	OCLContextInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
