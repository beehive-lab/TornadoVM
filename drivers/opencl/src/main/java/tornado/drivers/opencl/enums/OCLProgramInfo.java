package tornado.drivers.opencl.enums;

public enum OCLProgramInfo {

	CL_PROGRAM_REFERENCE_COUNT(0x1160),
	CL_PROGRAM_CONTEXT(0x1161),
	CL_PROGRAM_NUM_DEVICES(0x1162),
	CL_PROGRAM_DEVICES(0x1163),
	CL_PROGRAM_SOURCE(0x1164),
	CL_PROGRAM_BINARY_SIZES(0x1165),
	CL_PROGRAM_BINARIES(0x1166),
	CL_PROGRAM_NUM_KERNELS(0x1167),
	CL_PROGRAM_KERNEL_NAMES(0x1168);

	private final int	value;

	OCLProgramInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
