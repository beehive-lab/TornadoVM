package tornado.graal.backend;


import tornado.common.RuntimeUtilities;

import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.phases.util.Providers;

public abstract class TornadoBackend<P extends Providers> extends Backend {

	// % of global memory to allocate
		public static final long		DEFAULT_HEAP_ALLOCATION	= RuntimeUtilities
																		.parseSize(System
																				.getProperty(
																						"tornado.heap.allocation",
																						"512MB"));
	
		public final static boolean		ENABLE_EXCEPTIONS		= Boolean
				.parseBoolean(System
						.getProperty(
								"tornado.exceptions",
								"False"));
		
	protected TornadoBackend(Providers providers) {
		super(providers);
	}
	
	public abstract String decodeDeopt(long value);

	@SuppressWarnings("unchecked")
	@Override
	public Providers getProviders() {
		return (P) super.getProviders();
	}

}
