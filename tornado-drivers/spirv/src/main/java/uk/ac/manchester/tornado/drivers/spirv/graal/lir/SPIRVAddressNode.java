/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStamp;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary.MemoryAccess;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVUnary.MemoryIndexedAccess;

@NodeInfo
public class SPIRVAddressNode extends AddressNode implements LIRLowerable {

    public static final NodeClass<SPIRVAddressNode> TYPE = NodeClass.create(SPIRVAddressNode.class);

    @OptionalInput
    private ValueNode base;

    @OptionalInput
    private ValueNode index;

    private SPIRVArchitecture.SPIRVMemoryBase memoryRegion;

    protected SPIRVAddressNode(ValueNode base, ValueNode index, SPIRVArchitecture.SPIRVMemoryBase memoryRegion) {
        super(TYPE);
        this.base = base;
        this.index = index;
        this.memoryRegion = memoryRegion;
    }

    @Override
    public ValueNode getBase() {
        return base;
    }

    @Override
    public ValueNode getIndex() {
        return index;
    }

    @Override
    public long getMaxConstantDisplacement() {
        return 0;
    }

    private Value genValue(NodeLIRBuilderTool generator, ValueNode value) {
        return (value == null) ? Value.ILLEGAL : generator.operand(value);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        SPIRVLIRGenerator tool = (SPIRVLIRGenerator) generator.getLIRGeneratorTool();
        Value baseValue = genValue(generator, base);
        if (base instanceof ParameterNode && base.stamp(NodeView.DEFAULT) instanceof SPIRVStamp) {
            SPIRVStamp stamp = (SPIRVStamp) base.stamp(NodeView.DEFAULT);
            SPIRVKind kind = stamp.getSPIRVKind();
            if (kind.isVector()) {
                baseValue = tool.getSPIRVGenTool().getParameterToVariable().get(base);
            }
        }

        Value indexValue = genValue(generator, index);
        if (index == null) {
            generator.setResult(this, new SPIRVUnary.MemoryAccess(memoryRegion, baseValue));
        } else {
            setMemoryAccess(generator, baseValue, indexValue, tool);
        }
    }

    private boolean isPrivateMemoryAccess() {
        return this.memoryRegion.getNumber() == SPIRVArchitecture.privateSpace.getNumber();
    }

    private boolean isLocalMemoryAccess() {
        return this.memoryRegion.getNumber() == SPIRVArchitecture.localSpace.getNumber();
    }

    private void setMemoryAccess(NodeLIRBuilderTool generator, Value baseValue, Value indexValue, SPIRVLIRGenerator tool) {

        if (isPrivateMemoryAccess() || isLocalMemoryAccess()) {
            generator.setResult(this, new MemoryIndexedAccess(memoryRegion, baseValue, indexValue));
        } else {
            Variable addressNode = tool.getArithmetic().emitAdd(baseValue, indexValue, false);
            generator.setResult(this, new MemoryAccess(memoryRegion, addressNode));
        }
    }
}
