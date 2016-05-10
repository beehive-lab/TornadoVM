package tornado.graal.phases;

import static com.oracle.graal.compiler.common.GraalOptions.MaximumDesiredSize;

import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.graph.Node;
import com.oracle.graal.loop.CountedLoopInfo;
import com.oracle.graal.loop.LoopEx;
import com.oracle.graal.loop.LoopTransformations;
import com.oracle.graal.loop.LoopsData;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.LoopBeginNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.debug.ControlFlowAnchorNode;
import com.oracle.graal.phases.BasePhase;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.tiers.PhaseContext;

import static com.oracle.graal.loop.LoopPolicies.*;

public class TornadoLoopUnroller extends BasePhase<PhaseContext> {

    private static final DebugMetric FULLY_UNROLLED_LOOPS = Debug.metric("FullUnrolls");
    private final CanonicalizerPhase canonicalizer;

    public TornadoLoopUnroller(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }
 
    public static boolean shouldFullUnroll(LoopEx loop) {
        if (!loop.isCounted() || !loop.counted().isConstantMaxTripCount()) {
            return false;
        }
        CountedLoopInfo counted = loop.counted();
        long maxTrips = counted.constantMaxTripCount();
        int maxNodes = (counted.isExactTripCount() && counted.isConstantExactTripCount()) ? ExactFullUnrollMaxNodes.getValue() : FullUnrollMaxNodes.getValue();
        maxNodes = Math.min(maxNodes, MaximumDesiredSize.getValue() - loop.loopBegin().graph().getNodeCount());
        int size = Math.max(1, loop.size() - 1 - loop.loopBegin().phis().count());
        if (size * maxTrips <= maxNodes) {
            // check whether we're allowed to unroll this loop
        	int loops = 0;
        	int ifs = 0;
            for (Node node : loop.inside().nodes()) {
                if (node instanceof ControlFlowAnchorNode) {
                    return false;
                } else if (node instanceof LoopBeginNode){
                	loops++;
                } else if (node instanceof IfNode){
                	ifs++;
                }
            }
            
            if(loops - ifs != 0) return false;
            
            return true;
        } else {
            return false;
        }
    }
    
    public void execute(StructuredGraph graph, PhaseContext context){
    	run(graph,context);
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        if (graph.hasLoops()) {
            //System.out.printf("loop-unroll: got loops\n");
            boolean peeled;
            do {
                peeled = false;
                final LoopsData dataCounted = new LoopsData(graph);
                dataCounted.detectedCountedLoops();
                //System.out.printf("loop-unroll: got %d counted loops out of %d\n", dataCounted.countedLoops().size(), dataCounted.loops().size());
                for (LoopEx loop : dataCounted.countedLoops()) {
                    if (shouldFullUnroll(loop)) {
                        Debug.log("FullUnroll %s", loop);
                        //System.out.printf("loop-unroll: policy permits unroll: %s\n", loop);
                        LoopTransformations.fullUnroll(loop, context, canonicalizer);
                        FULLY_UNROLLED_LOOPS.increment();
                        Debug.dump(graph, "After fullUnroll %s", loop);
                        peeled = true;
                        break;
                    } else {
                        //System.out.printf("loop-unroll: policy bars unroll: %s\n", loop);
                    }
                }
                dataCounted.deleteUnusedNodes();
            } while (peeled);
        } else {
            //System.out.printf("loop-unroll: no loops\n");
        }
    }
}
