package tornado.drivers.opencl.enums;

public enum OCLCommandQueueInfo {
	CL_QUEUE_CONTEXT(0x1090),
	CL_QUEUE_DEVICE(0x1091),
	CL_QUEUE_REFERENCE_COUNT(0x1092),
	CL_QUEUE_PROPERTIES(0x1093);

	private final int	value;

	OCLCommandQueueInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
