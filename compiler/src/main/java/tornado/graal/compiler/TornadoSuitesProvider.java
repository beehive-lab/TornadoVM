package tornado.graal.compiler;

import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.tiers.HighTierContext;

public interface TornadoSuitesProvider {

    public PhaseSuite<HighTierContext> getGraphBuilderSuite();

    public TornadoSketchTier getSketchTier();

}
