/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
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
package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.TernaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.collections.math.TornadoMath;
import uk.ac.manchester.tornado.common.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLBuiltinTool;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class OCLIntTernaryIntrinsicNode extends TernaryNode implements ArithmeticLIRLowerable {

    protected OCLIntTernaryIntrinsicNode(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), x, y, z);
        this.operation = op;
    }

    public static final NodeClass<OCLIntTernaryIntrinsicNode> TYPE = NodeClass
            .create(OCLIntTernaryIntrinsicNode.class);
    protected final Operation operation;

    public enum Operation {
        CLAMP,
        MAD_HI,
        MAD_SAT,
        MAD24
    }

    public Operation operation() {
        return operation;
    }

    public static ValueNode create(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(x, y, z, op, kind);
        if (c != null) {
            return c;
        }
        return new OCLIntTernaryIntrinsicNode(x, y, z, op, kind);
    }

    protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
        ConstantNode result = null;

        if (x.isConstant() && y.isConstant() && z.isConstant()) {
            if (kind == JavaKind.Int) {
                int ret = doCompute(x.asJavaConstant().asInt(), y.asJavaConstant().asInt(), z.asJavaConstant().asInt(), op);
                result = ConstantNode.forInt(ret);
            } else if (kind == JavaKind.Long) {
                long ret = doCompute(x.asJavaConstant().asLong(), y.asJavaConstant().asLong(), z.asJavaConstant().asInt(), op);
                result = ConstantNode.forLong(ret);
            }
        }
        return result;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGen) {
        OCLBuiltinTool gen = ((OCLArithmeticTool) lirGen).getGen().getOCLBuiltinTool();

        Value x = getX().isConstant() ? builder.operand(getX()) : builder.operand(getX());
        Value y = getY().isConstant() ? builder.operand(getY()) : builder.operand(getY());
        Value z = getZ().isConstant() ? builder.operand(getZ()) : builder.operand(getZ());
        LIRKind lirKind = builder.getLIRGeneratorTool().getLIRKind(stamp);
        Variable result = builder.getLIRGeneratorTool().newVariable(lirKind);
        Value expr;
        switch (operation()) {
            case CLAMP:
                expr = gen.genIntClamp(x, y, z);
                break;
            case MAD24:
                expr = gen.genIntMad24(x, y, z);
                break;
            case MAD_HI:
                expr = gen.genIntMadHi(x, y, z);
                break;
            case MAD_SAT:
                expr = gen.genIntMadSat(x, y, z);
                break;
            default:
                throw shouldNotReachHere();
        }
        builder.getLIRGeneratorTool().append(new AssignStmt(result, expr));
        builder.setResult(this, result);

    }

    private static long doCompute(long x, long y, long z, Operation op) {
        switch (op) {
            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    private static int doCompute(int x, int y, int z, Operation op) {
        switch (op) {
            case CLAMP:
                return TornadoMath.clamp(x, y, z);

            default:
                throw new TornadoInternalError("unknown op %s", op);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode x, ValueNode y, ValueNode z) {
        ValueNode c = tryConstantFold(x, y, z, operation(), getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

}
