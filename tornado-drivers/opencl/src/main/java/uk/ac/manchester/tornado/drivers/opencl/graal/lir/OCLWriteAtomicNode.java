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

package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.AbstractWriteNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStamp;
import uk.ac.manchester.tornado.drivers.providers.TornadoMemoryOrder;

/**
 * Writes a given {@linkplain #value() value} a {@linkplain FixedAccessNode
 * memory location}.
 */
@NodeInfo(nameTemplate = "OCLAtomicWrite#{p#location/s}")
public class OCLWriteAtomicNode extends AbstractWriteNode implements LIRLowerableAccess {

    public static final NodeClass<OCLWriteAtomicNode> TYPE = NodeClass.create(OCLWriteAtomicNode.class);
    @Input(InputType.Association)
    private AddressNode address;
    @Input
    private ValueNode accumulator;
    private Stamp accStamp;
    private JavaKind elementKind;
    private ATOMIC_OPERATION operation;

    public OCLWriteAtomicNode(AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType, ValueNode acc, Stamp accStamp, JavaKind elementKind,
            ATOMIC_OPERATION operation) {
        super(TYPE, address, location, value, barrierType);

        this.address = address;
        this.accumulator = acc;
        this.accStamp = accStamp;
        this.elementKind = elementKind;
        this.operation = operation;
    }
    //@formatter:on

    protected OCLWriteAtomicNode(NodeClass<? extends OCLWriteAtomicNode> c, AddressNode address, LocationIdentity location, ValueNode value, BarrierType barrierType) {
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

    public OCLStamp getStampInt() {
        return switch (operation) {
            case ADD -> new OCLStamp(OCLKind.ATOMIC_ADD_INT);
            case MUL -> new OCLStamp(OCLKind.ATOMIC_MUL_INT);
            default -> throw new RuntimeException("Operation for reduction not supported yet: " + operation);
        };
    }

    public OCLStamp getStampFloat() {
        OCLStamp oclStamp = null;
        switch (operation) {
            case ADD:
                oclStamp = new OCLStamp(OCLKind.ATOMIC_ADD_FLOAT);
                break;
            default:
                throw new RuntimeException("Operation for reduction not supported yet: " + operation);
        }
        return oclStamp;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {

        // New OpenCL nodes for atomic add
        OCLStamp oclStamp = switch (elementKind) {
            case Int -> getStampInt();
            case Long ->
                // DUE TO UNSUPPORTED FEATURE IN INTEL OpenCL PLATFORM
                new OCLStamp(OCLKind.ATOMIC_ADD_INT);
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
