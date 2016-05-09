package tornado.drivers.opencl.enums;

public enum OCLBuildStatus {
	CL_BUILD_SUCCESS(0),
	CL_BUILD_NONE(-1),
	CL_BUILD_ERROR(-2),
	CL_BUILD_IN_PROGRESS(-3),
	CL_BUILD_UNKNOWN(-4);

	private final int	value;

	OCLBuildStatus(final int v) {
		value = v;
	}

	public int getValue() {
		return value;
	}

	public static OCLBuildStatus toEnum(final int v) {
		OCLBuildStatus result = OCLBuildStatus.CL_BUILD_UNKNOWN;
		switch (v) {
			case 0:
				result = OCLBuildStatus.CL_BUILD_SUCCESS;
				break;
			case -1:
				result = OCLBuildStatus.CL_BUILD_NONE;
				break;
			case -2:
				result = OCLBuildStatus.CL_BUILD_ERROR;
				break;
			case -3:
				result = OCLBuildStatus.CL_BUILD_IN_PROGRESS;
				break;
		}
		return result;
	}

}
