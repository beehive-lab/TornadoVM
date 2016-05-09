package tornado.common.exceptions;

public class TornadoCompilationException extends Exception {

	private static final long	serialVersionUID	= -7515308573010965892L;
	private final String		message;

	public TornadoCompilationException(final String msg) {
		message = msg;
	}

	@Override
	public String getMessage() {
		return message;
	}

}
