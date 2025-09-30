/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.metal.graal.nodes;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalStampFactory;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalArithmeticTool;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalBuiltinTool;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt.AssignStmt;
import uk.ac.manchester.tornado.runtime.graal.nodes.interfaces.Floatable;

public class MetalIntrinsicNode {

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

        public static final NodeClass<FixedBinaryGeometricOp> TYPE = NodeClass.create(FixedBinaryGeometricOp.class);
        private final GeometricOp operation;
        @Input
        private ValueNode x;
        @Input
        private ValueNode y;

        public FixedBinaryGeometricOp(MetalKind kind, GeometricOp op, ValueNode x, ValueNode y) {
            super(TYPE, MetalStampFactory.getStampFor(kind.getElementKind()));
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

        public static final NodeClass<BinaryGeometricOp> TYPE = NodeClass.create(BinaryGeometricOp.class);
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
            return stamp(NodeView.DEFAULT);
        }

        @Override
        public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool arithmeticTool) {
            MetalBuiltinTool gen = ((MetalArithmeticTool) builder).getGen().getMetalBuiltinTool();
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
                    unimplemented("Default case intrinsics not implemented yet.");
                    break;

            }

            Variable var = builder.getLIRGeneratorTool().newVariable(result.getValueKind());
            builder.getLIRGeneratorTool().append(new AssignStmt(var, result));
            builder.setResult(this, var);
        }

        @Override
        public void generate(NodeLIRBuilderTool builder) {
            generate(builder, builder.getLIRGeneratorTool().getArithmetic());
        }

    }

}
