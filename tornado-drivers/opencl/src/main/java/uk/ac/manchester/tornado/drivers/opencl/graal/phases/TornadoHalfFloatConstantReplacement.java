package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.Phase;
import uk.ac.manchester.tornado.runtime.graal.HalfFloatConstant;

import java.util.Optional;

public class TornadoHalfFloatConstantReplacement extends Phase {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    protected void run(StructuredGraph graph) {

        for (HalfFloatConstant halfFloatConstant : graph.getNodes().filter(HalfFloatConstant.class)) {
            ValueNode input = halfFloatConstant.getValue();
            halfFloatConstant.replaceAtUsages(input);
            halfFloatConstant.safeDelete();
        }

    }
}
