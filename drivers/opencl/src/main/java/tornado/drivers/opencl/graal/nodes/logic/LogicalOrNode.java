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
package tornado.drivers.opencl.graal.nodes.logic;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.LogicNode;
import jdk.vm.ci.meta.Value;

import static tornado.drivers.opencl.graal.asm.OCLAssembler.OCLBinaryOp.LOGICAL_OR;

import tornado.drivers.opencl.graal.lir.OCLBinary;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import tornado.graal.nodes.logic.BinaryLogicalNode;

@NodeInfo(shortName = "||")
public class LogicalOrNode extends BinaryLogicalNode {

    @SuppressWarnings("FieldNameHidesFieldInSuperclass")
    public static final NodeClass<LogicalOrNode> TYPE = NodeClass.create(LogicalOrNode.class);

    public LogicalOrNode(LogicNode x, LogicNode y) {
        super(TYPE, x, y);
    }

    @Override
    public Value generate(LIRGeneratorTool tool, Value x, Value y) {
        Variable result = tool.newVariable(tool.getLIRKind(stamp));
        AssignStmt assign = new AssignStmt(result, new OCLBinary.Expr(LOGICAL_OR, tool.getLIRKind(stamp), x, y));
        tool.append(assign);
        return result;
    }
}
