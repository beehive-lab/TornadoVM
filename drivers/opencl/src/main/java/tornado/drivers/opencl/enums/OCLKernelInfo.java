package tornado.drivers.opencl.enums;

public enum OCLKernelInfo {

	CL_KERNEL_FUNCTION_NAME(0x1190), CL_KERNEL_NUM_ARGS(0x1191), CL_KERNEL_REFERENCE_COUNT(
			0x1192), CL_KERNEL_CONTEXT(0x1193), CL_KERNEL_PROGRAM(0x1194), CL_KERNEL_ATTRIBUTES(
			0x1195);

	private final int	value;

	OCLKernelInfo(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
