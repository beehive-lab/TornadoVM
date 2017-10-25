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

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
import tornado.drivers.opencl.graal.lir.OCLBuiltinTool;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;

import static tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class OCLIntUnaryIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable {

    protected OCLIntUnaryIntrinsicNode(ValueNode x, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), x);
        this.operation = op;
    }

    public static final NodeClass<OCLIntUnaryIntrinsicNode> TYPE = NodeClass
            .create(OCLIntUnaryIntrinsicNode.class);
    protected final Operation operation;

    public enum Operation {
        ABS,
        CLZ,
        POPCOUNT
    }

    public Operation operation() {
        return operation;
    }

    public static ValueNode create(ValueNode x, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(x, op, kind);
        if (c != null) {
            return c;
        }
        return new OCLIntUnaryIntrinsicNode(x, op, kind);
    }

    protected static ValueNode tryConstantFold(ValueNode x, Operation op, JavaKind kind) {
        ConstantNode result = null;

        if (x.isConstant()) {
            if (kind == JavaKind.Int) {
                int ret = doCompute(x.asJavaConstant().asInt(), op);
                result = ConstantNode.forInt(ret);
            } else if (kind == JavaKind.Long) {
                long ret = doCompute(x.asJavaConstant().asLong(), op);
                result = ConstantNode.forLong(ret);
            }
        }
        return result;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGen) {
        OCLBuiltinTool gen = ((OCLArithmeticTool) lirGen).getGen().getOCLBuiltinTool();
        Value x = builder.operand(getValue());
        Value result;
        switch (operation()) {
            case ABS:
                result = gen.genIntAbs(x);
                break;
            case CLZ:
                result = gen.genIntClz(x);
                break;
            case POPCOUNT:
                result = gen.genIntPopcount(x);
                break;
            default:
                throw shouldNotReachHere();
        }
        Variable var = builder.getLIRGeneratorTool().newVariable(result.getValueKind());
        builder.getLIRGeneratorTool().append(new AssignStmt(var, result));
        builder.setResult(this, var);

    }

    private static long doCompute(long value, Operation op) {
        switch (op) {
            case ABS:
                return Math.abs(value);
            case CLZ:
                return Long.numberOfLeadingZeros(value);
            case POPCOUNT:
                return Long.bitCount(value);
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    private static int doCompute(int value, Operation op) {
        switch (op) {
            case ABS:
                return Math.abs(value);
            case CLZ:
                return Integer.numberOfLeadingZeros(value);
            case POPCOUNT:
                return Integer.bitCount(value);
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode value) {
        ValueNode c = tryConstantFold(value, operation(), getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

}
