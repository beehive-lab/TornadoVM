package tornado.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TornadoLogger {

	private final Logger	logger;

	public TornadoLogger(Class<?> clazz){
		logger = LogManager.getLogger(clazz == null ? this.getClass() : clazz);
	}
	
	public TornadoLogger(){
		this(null);
	}

	public void debug(final String msg) {
		logger.debug(msg);
	}

	public void debug(final String pattern, final Object... args) {
		debug(String.format(pattern, args));
	}

	public void error(final String msg) {
		logger.error(msg);
	}

	public void error(final String pattern, final Object... args) {
		error(String.format(pattern, args));
	}

	public void fatal(final String msg) {
		logger.fatal(msg);
	}

	public void fatal(final String pattern, final Object... args) {
		fatal(String.format(pattern, args));
	}

	public void info(final String msg) {
		logger.info(msg);
	}

	public void info(final String pattern, final Object... args) {
		info(String.format(pattern, args));
	}

	public void trace(final String msg) {
		logger.trace(msg);
	}

	public void trace(final String pattern, final Object... args) {
		trace(String.format(pattern, args));
	}

	public void warn(final String msg) {
		logger.warn(msg);
	}

	public void warn(final String pattern, final Object... args) {
		warn(String.format(pattern, args));
	}

}
