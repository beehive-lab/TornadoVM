package tornado.common.exceptions;

public class TornadoOutOfMemoryException extends Exception {

	private static final long	serialVersionUID	= 1609608023741117577L;
	private final String		message;

	public TornadoOutOfMemoryException(final String msg) {
		message = msg;
	}

	@Override
	public String getMessage() {
		return message;
	}
}
