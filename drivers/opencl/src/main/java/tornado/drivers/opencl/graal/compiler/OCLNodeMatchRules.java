package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.compiler.gen.NodeMatchRules;
import com.oracle.graal.lir.gen.LIRGeneratorTool;

public class OCLNodeMatchRules extends NodeMatchRules {

    public OCLNodeMatchRules(LIRGeneratorTool gen) {
        super(gen);
    }

}
