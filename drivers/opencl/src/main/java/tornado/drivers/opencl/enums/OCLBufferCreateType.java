package tornado.drivers.opencl.enums;

public enum OCLBufferCreateType {
	CL_BUFFER_CREATE_TYPE_REGION(0x1220);

	private final int	value;

	OCLBufferCreateType(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}
}
