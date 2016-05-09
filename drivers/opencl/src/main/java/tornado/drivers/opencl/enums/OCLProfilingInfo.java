package tornado.drivers.opencl.enums;

public enum OCLProfilingInfo {
	CL_PROFILING_COMMAND_QUEUED(0x1280),
	CL_PROFILING_COMMAND_SUBMIT(0x1281),
	CL_PROFILING_COMMAND_START(0x1282),
	CL_PROFILING_COMMAND_END(0x1283);

	private final int	value;

	OCLProfilingInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
