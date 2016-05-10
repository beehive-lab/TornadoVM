package tornado.graal.compiler;

import tornado.graal.phases.TornadoHighTierContext;

import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;

public abstract class TornadoHighTier extends PhaseSuite<TornadoHighTierContext> {

	protected final CustomCanonicalizer customCanonicalizer;
	
	public CustomCanonicalizer getCustomCanonicalizer(){
		return customCanonicalizer;
	}
	
    public TornadoHighTier(CustomCanonicalizer customCanonicalizer){
    	this.customCanonicalizer = customCanonicalizer;
    }
}
