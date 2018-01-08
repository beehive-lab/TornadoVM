/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2017 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
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
 *
 * Authors: James Clarkson
 *
 */
package tornado.drivers.opencl.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
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
            case I2D:
                return OCLUnaryOp.CAST_TO_DOUBLE;
            case F2I:
                return OCLUnaryOp.CAST_TO_INT;
            case I2F:
                return OCLUnaryOp.CAST_TO_FLOAT;
            case F2D:
                return OCLUnaryOp.CAST_TO_DOUBLE;
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
