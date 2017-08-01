/* 
 * Copyright 2012 James Clarkson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import tornado.drivers.opencl.graal.lir.OCLUnary;

public class OCLBarrierNode {

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
           gen.getLIRGeneratorTool().append(new OCLLIRStmt.ExprStmt(new OCLUnary.Barrier(OCLUnaryIntrinsic.MEM_FENCE, flags)));
        }

    }

}
