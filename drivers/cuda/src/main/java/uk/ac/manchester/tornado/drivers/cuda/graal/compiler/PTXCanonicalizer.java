package uk.ac.manchester.tornado.drivers.cuda.graal.compiler;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;

public class PTXCanonicalizer implements CanonicalizerPhase.CustomCanonicalization {
    @Override
    public Node canonicalize(Node node) {
        return node;
    }
}
