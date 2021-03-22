package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

public class SPIRVNodeMatchRules extends NodeMatchRules {

    public SPIRVNodeMatchRules(LIRGeneratorTool lirGen) {
        super(lirGen);
    }
}
