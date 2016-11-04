/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tornado.drivers.opencl.graal.nodes;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.memory.MemoryNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction;
import tornado.drivers.opencl.graal.lir.OCLUnary;

public class OCLBarrier {

    public static enum OCLMemFenceFlags {
        GLOBAL, LOCAL;
    }

    public final static OCLMemFenceNode LOCAL_MEM_FENCE = new OCLMemFenceNode(OCLMemFenceFlags.LOCAL);
    public final static OCLMemFenceNode GLOBAL_MEM_FENCE = new OCLMemFenceNode(OCLMemFenceFlags.GLOBAL);

    @NodeInfo
    public static class OCLMemFenceNode extends FixedWithNextNode implements LIRLowerable,MemoryNode {

        public static final NodeClass<OCLMemFenceNode> TYPE = NodeClass.create(OCLMemFenceNode.class);

        private final OCLMemFenceFlags flags;

        public OCLMemFenceNode(OCLMemFenceFlags flags) {
            super(TYPE, StampFactory.forVoid());
            this.flags = flags;
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
           gen.getLIRGeneratorTool().append(new OCLLIRInstruction.ExprStmt(new OCLUnary.Barrier(OCLUnaryIntrinsic.MEM_FENCE, flags)));
        }

    }

}
