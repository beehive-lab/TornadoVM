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

package uk.ac.manchester.tornado.drivers.metal.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.LIRLowerableAccess;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalStamp;
import uk.ac.manchester.tornado.drivers.providers.TornadoMemoryOrder;

/**
 * Writes a given {@linkplain #value() value} a {@linkplain FixedAccessNode
 * memory location}.
 */
@NodeInfo(nameTemplate = "MetalAtomicWrite#{p#location/s}")
public class MetalWriteAtomicNode extends AbstractWriteNode implements LIRLowerableAccess {

    public static final NodeClass<MetalWriteAtomicNode> TYPE = NodeClass.create(MetalWriteAtomicNode.class);
    @Input(InputType.Association)
    private AddressNode address;
    @Input
    private ValueNode accumulator;
    private Stamp accStamp;
    private JavaKind elementKind;
    private ATOMIC_OPERATION operation;

    public MetalWriteAtomicNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, ValueNode acc, Stamp accStamp, JavaKind elementKind,
            ATOMIC_OPERATION operation) {
        super(TYPE, address, location, value, barrierType);

        this.address = address;
        this.accumulator = acc;
        this.accStamp = accStamp;
        this.elementKind = elementKind;
        this.operation = operation;
    }
    //@formatter:on

    protected MetalWriteAtomicNode(NodeClass<? extends MetalWriteAtomicNode> c, AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
        super(c, address, location, value, barrierType);
        this.address = address;
    }

    public static void store() {

    }

    @Override
    public MemoryOrderMode getMemoryOrder() {
        return null;
    }

    @Override
    public Stamp getAccessStamp(NodeView view) {
        return value().stamp(view);
    }

    public MetalStamp getStampInt() {
        return switch (operation) {
            case ADD -> new MetalStamp(MetalKind.ATOMIC_ADD_INT);
            case MUL -> new MetalStamp(MetalKind.ATOMIC_MUL_INT);
            default -> throw new RuntimeException("Operation for reduction not supported yet: " + operation);
        };
    }

    public MetalStamp getStampFloat() {
        MetalStamp oclStamp = null;
        switch (operation) {
            case ADD:
                oclStamp = new MetalStamp(MetalKind.ATOMIC_ADD_FLOAT);
                break;
            default:
                throw new RuntimeException("Operation for reduction not supported yet: " + operation);
        }
        return oclStamp;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {

        // New Metal nodes for atomic add
        MetalStamp oclStamp = switch (elementKind) {
            case Int -> getStampInt();
            case Long ->
                // DUE TO UNSUPPORTED FEATURE IN INTEL Metal PLATFORM
                new MetalStamp(MetalKind.ATOMIC_ADD_INT);
            case Float -> getStampFloat();
            default -> throw new RuntimeException("Data type for reduction not supported yet: " + elementKind);
        };

        LIRKind writeKind = gen.getLIRGeneratorTool().getLIRKind(oclStamp);
        LIRKind accKind = gen.getLIRGeneratorTool().getLIRKind(accStamp);

        // Atomic Store
        gen.getLIRGeneratorTool().getArithmetic().emitStore(writeKind, gen.operand(address), gen.operand(value()), gen.state(this), TornadoMemoryOrder.GPU_MEMORY_MODE);

        // Update the accumulator
        gen.getLIRGeneratorTool().getArithmetic().emitStore(accKind, gen.operand(accumulator), gen.operand(value()), gen.state(this), TornadoMemoryOrder.GPU_MEMORY_MODE);
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        unimplemented();
        return null;
    }

    @Override
    public NodeIterable<FrameState> states() {
        unimplemented();
        return null;
    }

    //@formatter:off
    public enum ATOMIC_OPERATION {
        ADD,
        MUL,
        MAX,
        MIN,
        SUB,
        CUSTOM;
    }
    //@formatter:on
}
