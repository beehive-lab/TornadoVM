/*
 * Copyright (c) 2018, 2020-2022 APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import tornado.graal.compiler.graph.NodeClass;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.nodeinfo.NodeInfo;
import tornado.graal.compiler.nodes.NodeView;
import tornado.graal.compiler.nodes.ParameterNode;
import tornado.graal.compiler.nodes.ValueNode;
import tornado.graal.compiler.nodes.memory.address.AddressNode;
import tornado.graal.compiler.nodes.spi.LIRLowerable;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture.CUDAMemoryBase;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAStamp;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDALIRGenerator;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAUnary.MemoryAccess;

@NodeInfo
public class CUDAAddressNode extends AddressNode implements LIRLowerable {

    public static final NodeClass<CUDAAddressNode> TYPE = NodeClass.create(CUDAAddressNode.class);

    @OptionalInput
    private ValueNode base;

    @OptionalInput
    private ValueNode index;

    private CUDAMemoryBase memoryRegister;

    public CUDAAddressNode(ValueNode base, ValueNode index, CUDAMemoryBase memoryRegister) {
        super(TYPE);
        this.base = base;
        this.index = index;
        this.memoryRegister = memoryRegister;
    }

    public CUDAAddressNode(ValueNode base, ValueNode index) {
        super(TYPE);
        this.base = base;
        this.index = index;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        CUDALIRGenerator tool = (CUDALIRGenerator) gen.getLIRGeneratorTool();

        Value baseValue = base == null ? Value.ILLEGAL : gen.operand(base);
        if (base instanceof ParameterNode && base.stamp(NodeView.DEFAULT) instanceof CUDAStamp) {
            CUDAStamp stamp = (CUDAStamp) base.stamp(NodeView.DEFAULT);
            CUDAKind kind = stamp.getCUDAKind();
            if (kind.isVector()) {
                baseValue = tool.getOclGenTool().getParameterToVariable().get(base);
            }
        }

        Value indexValue = index == null ? Value.ILLEGAL : gen.operand(index);
        if (index == null) {
            gen.setResult(this, new MemoryAccess(memoryRegister, baseValue));
        } else {
            setMemoryAccess(gen, baseValue, indexValue, tool);
        }
    }

    private boolean isLocalMemoryAccess() {
        return this.memoryRegister.getName().equals(CUDAAssemblerConstants.LOCAL_REGION_NAME);
    }

    private boolean isPrivateMemoryAccess() {
        return this.memoryRegister.getName().equals(CUDAAssemblerConstants.PRIVATE_REGION_NAME);
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

    private void setMemoryAccess(NodeLIRBuilderTool gen, Value baseValue, Value indexValue, CUDALIRGenerator tool) {
        Variable addressValue;

        if (isLocalMemoryAccess() || isPrivateMemoryAccess()) {
            gen.setResult(this, new MemoryAccess(memoryRegister, baseValue, indexValue));
        } else {
            addressValue = tool.getArithmetic().emitAdd(baseValue, indexValue, false);
            gen.setResult(this, new MemoryAccess(memoryRegister, addressValue));
        }
    }
}
