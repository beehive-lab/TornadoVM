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

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryIntrinsic;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt;
import tornado.drivers.opencl.graal.lir.OCLUnary;

@NodeInfo
public class LocalThreadIdNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<LocalThreadIdNode> TYPE = NodeClass
            .create(LocalThreadIdNode.class);

    @Node.Input
    protected ConstantNode index;

    public LocalThreadIdNode(ConstantNode value) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        assert stamp != null;
        index = value;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        tool.append(new OCLLIRStmt.AssignStmt(result, new OCLUnary.Intrinsic(OCLUnaryIntrinsic.LOCAL_ID, tool.getLIRKind(stamp), gen.operand(index))));
        gen.setResult(this, result);
    }
}
