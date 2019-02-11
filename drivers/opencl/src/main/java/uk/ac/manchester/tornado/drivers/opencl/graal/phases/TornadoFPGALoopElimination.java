package uk.ac.manchester.tornado.drivers.opencl.graal.phases;

import java.util.Iterator;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopPolicies;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.loop.phases.LoopPhase;
import org.graalvm.compiler.loop.phases.LoopTransformations;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public class TornadoFPGALoopElimination extends LoopPhase<LoopPolicies> {

    private static final DebugCounter FULLY_UNROLLED_LOOPS = Debug.counter("FullUnrolls");
    private final CanonicalizerPhase canonicalizer;

    public TornadoFPGALoopElimination(CanonicalizerPhase canonicalizer, LoopPolicies policies) {
        super(policies);
        this.canonicalizer = canonicalizer;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        // LoopPolicies loopPolicies = new DefaultLoopPolicies();
        // CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        boolean peeled;
        if (graph.hasLoops()) {
            do {
                peeled = false;
                LoopsData dataCounted = new LoopsData(graph);
                dataCounted.detectedCountedLoops();
                Iterator var5 = dataCounted.countedLoops().iterator();

                while (var5.hasNext()) {
                    LoopEx loop = (LoopEx) var5.next();
                    if (this.getPolicies().shouldFullUnroll(loop)) {
                        Debug.log("FullUnroll %s", loop);
                        LoopTransformations.fullUnroll(loop, context, this.canonicalizer);
                        FULLY_UNROLLED_LOOPS.increment();
                        Debug.dump(4, graph, "FullUnroll %s", loop);
                        peeled = true;
                        break;
                    }
                }

                dataCounted.deleteUnusedNodes();
            } while (peeled);
        }

    }

    @Override
    public boolean checkContract() {
        return false;
    }

}
