/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVVectorElementSelect;

@NodeInfo(nameTemplate = "Op .s{p#lane}")
public class VectorElementOpNode extends FloatingNode implements LIRLowerable, Comparable<VectorElementOpNode> {

    public static final NodeClass<VectorElementOpNode> TYPE = NodeClass.create(VectorElementOpNode.class);
    protected final SPIRVKind kind;
    @Input(InputType.Extension)
    protected ValueNode vector;
    @Input
    protected ValueNode lane;

    public VectorElementOpNode(NodeClass<? extends FloatingNode> c, SPIRVKind kind, ValueNode vector, ValueNode lane) {
        super(c, StampFactory.forKind(kind.asJavaKind()));
        this.kind = kind;
        this.vector = vector;
        this.lane = lane;
        Stamp vectorStamp = vector.stamp(NodeView.DEFAULT);
        SPIRVKind vectorKind;
        if (vectorStamp instanceof SPIRVStamp) {
            final SPIRVStamp spirvStamp = (SPIRVStamp) vector.stamp(NodeView.DEFAULT);
            vectorKind = spirvStamp.getSPIRVKind();
            guarantee(vectorKind.isVector(), "Cannot apply vector operation to non-vector type: %s", vectorKind);
        } else if (vectorStamp instanceof ObjectStamp) {
            ObjectStamp objectStamp = (ObjectStamp) vector.stamp(NodeView.DEFAULT);
            if (objectStamp.type() != null) {
                vectorKind = SPIRVKind.fromResolvedJavaTypeToVectorKind(objectStamp.type());
                guarantee(vectorKind.isVector(), "Cannot apply vector operation to non-vector type: %s", vectorKind);
                guarantee(vectorKind.getVectorLength() >= laneId(), "Invalid lane %d on type %s", laneId(), kind);
            }
        } else {
            shouldNotReachHere("invalid type on vector operation: %s (stamp=%s (class=%s))", vector, vector.stamp(NodeView.DEFAULT), vector.stamp(NodeView.DEFAULT).getClass().getName());
        }

    }

    public final int laneId() {
        guarantee(lane instanceof ConstantNode, "Invalid lane: %s", lane);
        return (lane instanceof ConstantNode) ? lane.asJavaConstant().asInt() : -1;
    }

    @Override
    public int compareTo(VectorElementOpNode o) {
        return Integer.compare(laneId(), o.laneId());
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(StampFactory.forKind(kind.asJavaKind()));
    }

    public final ValueNode getLaneId() {
        return this.lane;
    }

    public ValueNode getVector() {
        return vector;
    }

    public SPIRVKind getSPIRVKind() {
        return kind;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        guarantee(vector != null, "vector is null");
        Value targetVector = gen.operand(getVector());
        guarantee(targetVector != null, "vector is null");

        Logger.traceBuildLIR(Logger.BACKEND.SPIRV, "emitVectorElementOp SELECT: targetVector=%s, laneId=%d", targetVector, laneId());
        assert targetVector instanceof Variable;

        LIRKind lirKind = gen.getLIRGeneratorTool().getLIRKind(stamp);

        // The LIR Kind corresponds to the type of the vector elements.
        // The SPIRVVectorElementSelect is a selection of an item within a vector type
        // (Vector -> Scalar) transformation. Therefore, the main LIR type to be
        // propagated is lirKind
        final SPIRVVectorElementSelect element = new SPIRVVectorElementSelect(lirKind, LIRKind.value(targetVector.getPlatformKind()), (Variable) targetVector, laneId());
        gen.setResult(this, element);
    }
}
