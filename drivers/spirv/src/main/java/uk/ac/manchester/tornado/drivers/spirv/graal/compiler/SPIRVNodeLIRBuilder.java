package uk.ac.manchester.tornado.drivers.spirv.graal.compiler;

import java.util.List;

import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.core.gen.NodeLIRBuilder;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.BreakpointNode;
import org.graalvm.compiler.nodes.DirectCallTargetNode;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;

import jdk.vm.ci.meta.Value;

/**
 * It traverses the HIR instructions from the Graal CFP and it generates LIR for
 * the SPIR-V backend.
 */
public class SPIRVNodeLIRBuilder extends NodeLIRBuilder {

    public SPIRVNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, NodeMatchRules nodeMatchRules) {
        super(graph, gen, nodeMatchRules);
        System.out.println("Functionality pending - SPIRVNodeLIRBuilder ");
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        return false;
    }

    @Override
    protected void emitDirectCall(DirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {

    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {

    }

    @Override
    public void visitSafepointNode(SafepointNode i) {

    }

    @Override
    public void visitBreakpointNode(BreakpointNode i) {

    }

    public void doBlock(final Block block, final StructuredGraph graph, final BlockMap<List<Node>> blockMap, boolean isKernel) {

    }
}
