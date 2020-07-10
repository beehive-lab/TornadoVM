package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

public class PTXNodeMatchRules extends NodeMatchRules {
    public PTXNodeMatchRules(LIRGeneratorTool lirGen) {
        super(lirGen);
    }
}
