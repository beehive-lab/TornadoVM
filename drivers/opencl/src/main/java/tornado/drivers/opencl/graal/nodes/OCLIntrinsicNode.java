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
package tornado.drivers.opencl.graal.nodes;

import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
import tornado.drivers.opencl.graal.lir.OCLBuiltinTool;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.graal.nodes.Floatable;

public class OCLIntrinsicNode {

    public enum FloatingOp {
        ATAN2, ATAN2PI, COPYSIGN, FDIM, FMA, FMAX, FMIN, FMOD, FRACT, FREXP, HYPOT, LDEXP, MAD, MAXMAG, MINMAG, MODF, NEXTAFTER, POW, POWN, POWR, REMAINDER, REMQUO, ROOTN, SINCOS
    }

    public enum IntegerOp {
        ATAN2, ATAN2PI, COPYSIGN, FDIM, FMA, FMAX, FMIN, FMOD, FRACT, FREXP, HYPOT, LDEXP, MAD, MAXMAG, MINMAG, MODF, NEXTAFTER, POW, POWN, POWR, REMAINDER, REMQUO, ROOTN, SINCOS
    }

    public enum GeometricOp {
        CROSS, DISTANCE, DOT, LENGTH, NORMALISE, FAST_DISTANCE, FAST_LENGTH, FAST_NORMALISE
    }

    @NodeInfo(nameTemplate = "{p#operation/s}")
    public static final class FixedBinaryGeometricOp extends FixedNode implements Floatable {

        public static final NodeClass<FixedBinaryGeometricOp> TYPE = NodeClass
                .create(FixedBinaryGeometricOp.class);
        private final GeometricOp operation;
        @Input
        private ValueNode x;
        @Input
        private ValueNode y;

        public FixedBinaryGeometricOp(OCLKind kind, GeometricOp op, ValueNode x, ValueNode y) {
            super(TYPE, OCLStampFactory.getStampFor(kind.getElementKind()));
            this.operation = op;
            this.x = x;
            this.y = y;
        }

        @Override
        public FloatingNode asFloating() {
            return new BinaryGeometricOp(stamp, operation, x, y);
        }
    }

    @NodeInfo(nameTemplate = "{p#operation/s}")
    public static class BinaryGeometricOp extends BinaryNode implements ArithmeticLIRLowerable {

        public static final NodeClass<BinaryGeometricOp> TYPE = NodeClass
                .create(BinaryGeometricOp.class);
        protected final GeometricOp operation;

        public BinaryGeometricOp(Stamp stamp, GeometricOp op, ValueNode x, ValueNode y) {
            super(TYPE, stamp, x, y);
            this.operation = op;
        }

        @Override
        public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
            return this;
        }

        @Override
        public ValueNode canonical(CanonicalizerTool tool) {
            return this;
        }

        @Override
        public Stamp foldStamp(Stamp stampX, Stamp stampY) {
            return stamp();
        }

        @Override
        public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool arithmeticTool) {
            OCLBuiltinTool gen = ((OCLArithmeticTool) builder).getGen().getOCLBuiltinTool();
            final Value x = builder.operand(getX());
            final Value y = builder.operand(getY());
            Value result = null;
            switch (operation) {
                case CROSS:
                    result = gen.genGeometricCross(x, y);
                    break;
                case DISTANCE:
                    break;
                case DOT:
                    result = gen.genGeometricDot(x, y);
                    break;
                case FAST_DISTANCE:

                case FAST_LENGTH:

                case FAST_NORMALISE:

                case LENGTH:

                case NORMALISE:

                default:
                    unimplemented();
                    break;

            }

            Variable var = builder.getLIRGeneratorTool().newVariable(result.getValueKind());
            builder.getLIRGeneratorTool().append(new AssignStmt(var, result));

            //System.out.printf("result: kind=%s, kind=%s\n",result.getKind(), vectorKind.getElementKind());
            builder.setResult(this, var);

        }

        @Override
        public void generate(NodeLIRBuilderTool builder) {
            generate(builder, builder.getLIRGeneratorTool().getArithmetic());
        }

    }

}
