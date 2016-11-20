package tornado.graal.phases;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeBitMap;
import com.oracle.graal.loop.InductionVariable;
import com.oracle.graal.loop.LoopEx;
import com.oracle.graal.loop.LoopsData;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.BasePhase;
import java.util.ArrayDeque;
import java.util.Queue;
import tornado.graal.phases.TornadoHighTierContext;

public class TornadoParallelAnalysis extends BasePhase<TornadoHighTierContext> {

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {

        if (!context.hasMeta()) {
            return;
        }

        if (graph.hasLoops()) {
            final LoopsData data = new LoopsData(graph);
            data.detectedCountedLoops();

            int loopIndex = 0;
            for (LoopEx loop : data.innerFirst()) {
                for (Node n : loop.getInductionVariables().keySet()) {
                    NodeBitMap nodes = graph.createNodeBitMap();
                    nodes.clearAll();
                    InductionVariable iv = loop.getInductionVariables().get(n);
                    System.out.printf("iv: node=%s, iv=%s\n", n, iv);

                    Queue<Node> nf = new ArrayDeque<>();
//                    nodes.mark(n);
                    n.usages().forEach(nf::add);

                    while (!nf.isEmpty()) {
                        Node currentNode = nf.remove();
                        if (currentNode.hasUsages()) {
                            currentNode.usages().forEach(node -> {
                                if (!nodes.isMarked(node)) {
                                    nodes.mark(node);
                                    nf.add(node);
                                }
                            });
                        }

//                        if (currentNode instanceof AccessIndexedNode) {
                        System.out.printf("flood: %s\n", currentNode);
//                        }
                    }

                }

            }

        }

    }

}
