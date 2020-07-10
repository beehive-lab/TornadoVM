package uk.ac.manchester.tornado.drivers.ptx.graal.compiler;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;

public class PTXCanonicalizer implements CanonicalizerPhase.CustomCanonicalization {
    @Override
    public Node canonicalize(Node node) {
        return node;
    }
}
