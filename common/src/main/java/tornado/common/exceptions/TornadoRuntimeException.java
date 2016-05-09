package tornado.common.exceptions;

public class TornadoRuntimeException extends Exception {

	private static final long	serialVersionUID	= -7515308573010965892L;
	private final String		message;

	public TornadoRuntimeException(final String msg) {
		message = msg;
	}

	public TornadoRuntimeException(Exception e) {
		message = e.getMessage();
		this.initCause(e.getCause());
	}

	@Override
	public String getMessage() {
		return message;
	}

}
