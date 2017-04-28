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
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.NodeInputList;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.ExprStmt;
import tornado.drivers.opencl.graal.lir.OCLTPrintf;

@NodeInfo(shortName = "tprintf")
public class TPrintfNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<TPrintfNode> TYPE = NodeClass
            .create(TPrintfNode.class);

    @Input
    private NodeInputList<ValueNode> inputs;

    public TPrintfNode(ValueNode... values) {
        super(TYPE, StampFactory.forVoid());
        this.inputs = new NodeInputList<>(this, values.length);
        for (int i = 0; i < values.length; i++) {
            inputs.set(i, values[i]);
        }
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value[] args = new Value[inputs.size()];
        for (int i = 0; i < args.length; i++) {

            ValueNode param = inputs.get(i);
            if (param.isConstant()) {
                args[i] = gen.operand(param);
            } else {
                args[i] = gen.getLIRGeneratorTool().load(gen.operand(param));
            }
        }
        gen.getLIRGeneratorTool().append(new ExprStmt(new OCLTPrintf(args)));
    }

}
