package uk.ac.manchester.tornado.drivers.spirv.graal;

import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSketchTier;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

public class SPIRVSuitesProvider implements TornadoSuitesProvider {

    @Override
    public PhaseSuite<HighTierContext> getGraphBuilderSuite() {
        return null;
    }

    @Override
    public TornadoSketchTier getSketchTier() {
        return null;
    }
}
