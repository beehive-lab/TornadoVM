package tornado.graal.backend;


import com.oracle.graal.compiler.target.Backend;
import com.oracle.graal.phases.util.Providers;

public abstract class TornadoBackend<P extends Providers> extends Backend {

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
