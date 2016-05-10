package tornado.graal.compiler;

import tornado.common.TornadoLogger;

public final class TornadoCodeGenerator {

	public static final TornadoLogger	log	= new TornadoLogger(TornadoCodeGenerator.class);

	public static final void debug(final String msg) {
		log.debug(msg);
	}

	public static final void debug(final String pattern, final Object... args) {
		debug(String.format(pattern, args));
	}

	public static final void error(final String msg) {
		log.error(msg);
	}

	public static final void error(final String pattern, final Object... args) {
		error(String.format(pattern, args));
	}

	public static final void fatal(final String msg) {
		log.fatal(msg);
	}

	public static final void fatal(final String pattern, final Object... args) {
		fatal(String.format(pattern, args));
	}

	public static final void info(final String msg) {
		log.info(msg);
	}

	public static final void info(final String pattern, final Object... args) {
		info(String.format(pattern, args));
	}

	public static final void trace(final String msg) {
		log.trace(msg);
	}

	public static final void trace(final String pattern, final Object... args) {
		trace(String.format(pattern, args));
	}

	public static final void warn(final String msg) {
		log.warn(msg);
	}

	public static final void warn(final String pattern, final Object... args) {
		warn(String.format(pattern, args));
	}
}
