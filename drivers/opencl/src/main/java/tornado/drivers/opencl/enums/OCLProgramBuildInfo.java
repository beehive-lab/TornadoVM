package tornado.drivers.opencl.enums;

public enum OCLProgramBuildInfo {

	CL_PROGRAM_BUILD_STATUS(0x1181),
	CL_PROGRAM_BUILD_OPTIONS(0x1182),
	CL_PROGRAM_BUILD_LOG(0x1183),
	CL_PROGRAM_BINARY_TYPE(0x1184);

	private final int	value;

	OCLProgramBuildInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
