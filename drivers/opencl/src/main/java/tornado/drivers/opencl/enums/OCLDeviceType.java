package tornado.drivers.opencl.enums;

public enum OCLDeviceType {

	CL_DEVICE_TYPE_DEFAULT(1 << 0),
	CL_DEVICE_TYPE_CPU(1 << 1),
	CL_DEVICE_TYPE_GPU(1 << 2),
	CL_DEVICE_TYPE_ACCELERATOR(1 << 3),
	CL_DEVICE_TYPE_CUSTOM(1 << 4),
	CL_DEVICE_TYPE_ALL(0xFFFFFFFF);

	private final long	value;

	OCLDeviceType(final long v) {
		value = v;
	}

	public long getValue() {
		return value;
	}

	public static final OCLDeviceType toDeviceType(final long v) {
		OCLDeviceType result = null;
		switch ((int) v) {
			case 1 << 0:
				result = OCLDeviceType.CL_DEVICE_TYPE_DEFAULT;
				break;
			case 1 << 1:
				result = OCLDeviceType.CL_DEVICE_TYPE_CPU;
				break;
			case 1 << 2:
				result = OCLDeviceType.CL_DEVICE_TYPE_GPU;
				break;
			case 1 << 3:
				result = OCLDeviceType.CL_DEVICE_TYPE_ACCELERATOR;
				break;
			case 1 << 4:
				result = OCLDeviceType.CL_DEVICE_TYPE_CUSTOM;
				break;
			case 0xFFFFFFFF:
				result = OCLDeviceType.CL_DEVICE_TYPE_ALL;
				break;
		}
		return result;
	}
}
