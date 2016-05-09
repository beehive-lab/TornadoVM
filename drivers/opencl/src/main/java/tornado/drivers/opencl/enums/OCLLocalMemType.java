package tornado.drivers.opencl.enums;

public enum OCLLocalMemType {

	CL_LOCAL(1),
	CL_GLOBAL(2);

	private final int	value;

	OCLLocalMemType(final int v) {
		value = v;
	}

	public long getValue() {
		return value;
	}

	public static final OCLLocalMemType toLocalMemType(final int v) {
		OCLLocalMemType result = null;
		switch (v) {
			case 1 :
				result = OCLLocalMemType.CL_LOCAL;
				break;
			case 2:
				result = OCLLocalMemType.CL_GLOBAL;
				break;
		}
		return result;
	}
}
