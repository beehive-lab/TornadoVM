package uk.ac.manchester.tornado.runtime.graal.nodes;

import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.loop.LoopEx;
import org.graalvm.compiler.nodes.loop.LoopsData;

public class TornadoLoopsData extends LoopsData {

    protected TornadoLoopsData(ControlFlowGraph cfg, List<LoopEx> loops, EconomicMap<LoopBeginNode, LoopEx> loopBeginToEx) {
        super(cfg, loops, loopBeginToEx);
    }

    public TornadoLoopsData(StructuredGraph graph) {
        super(graph);
    }
}
