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

import com.oracle.graal.compiler.common.LIRKind;
import com.oracle.graal.compiler.common.calc.FloatConvert;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import tornado.drivers.opencl.graal.asm.OCLAssembler.OCLUnaryOp;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import tornado.drivers.opencl.graal.lir.OCLUnary;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

@NodeInfo
public class CastNode extends FloatingNode implements LIRLowerable {

    public static final NodeClass<CastNode> TYPE = NodeClass
            .create(CastNode.class);

    @Input
    protected ValueNode value;
    protected FloatConvert op;

    public CastNode(Stamp stamp, FloatConvert op, ValueNode value) {
        super(TYPE, stamp);
        this.op = op;
        this.value = value;
    }

    private OCLUnaryOp resolveOp() {
        switch (op) {
            case F2I:
                return OCLUnaryOp.CAST_TO_INT;
            case I2F:
                return OCLUnaryOp.CAST_TO_FLOAT;
            default:
                unimplemented("float convert: " + op.toString());
                break;
        }
        return null;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        /*
         * using as_T reinterprets the data as type T - consider: float x =
         * (float) 1; and int value = 1, float x = &(value);
         */
        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);
        OCLKind oclKind = (OCLKind) lirKind.getPlatformKind();
        final Variable result = gen.getLIRGeneratorTool().newVariable(lirKind);
        if (oclKind.isFloating()) {
            gen.getLIRGeneratorTool().append(new AssignStmt(result, new OCLUnary.Expr(resolveOp(), lirKind, gen.operand(value))));
        } else {
            gen.getLIRGeneratorTool().append(new AssignStmt(result, new OCLUnary.FloatCast(OCLUnaryOp.CAST_TO_INT, lirKind, gen.operand(value))));

        }

        gen.setResult(this, result);

    }
}
