/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.runtime.graph;

import java.util.List;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.loop.BasicInductionVariable;
import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.StructuredGraph;
import tornado.graal.nodes.ParallelRangeNode;
import tornado.runtime.api.CompilableTask;

/**
 *
 * @author James Clarkson
 */
class TornadoTaskUtil {

    private static LoopEx findParallelLoop(StructuredGraph graph) {
        final LoopsData data = new LoopsData(graph);
        data.detectedCountedLoops();

        final List<LoopEx> loops = data.outerFirst();

        List<ParallelRangeNode> parRanges = graph.getNodes().filter(ParallelRangeNode.class).snapshot();
        for (LoopEx loop : loops) {
            for (ParallelRangeNode parRange : parRanges) {
                for (Node n : parRange.offset().usages()) {
                    if (loop.getInductionVariables().containsKey(n)) {
                        BasicInductionVariable iv = (BasicInductionVariable) loop.getInductionVariables().get(n);
                        System.out.printf("[%d] parallel loop: %s -> init=%s, cond=%s, stride=%s, op=%s\n", parRange.index(), loop.loopBegin(), parRange.offset().value(), parRange.value(), parRange.stride(), iv.getOp());
                        return loop;
                    }
                }
            }
        }
        return null;
    }

    public static StructuredGraph merge(CompilableTask t1, CompilableTask t2, StructuredGraph g1, StructuredGraph g2, int[] merges) {

        LoopEx l1 = findParallelLoop(g1);
        LoopEx l2 = findParallelLoop(g2);
        return null;
    }

}
