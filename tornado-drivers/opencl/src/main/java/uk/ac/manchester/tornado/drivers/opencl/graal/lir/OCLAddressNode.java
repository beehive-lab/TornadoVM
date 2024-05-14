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
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

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
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLStamp;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;

@NodeInfo
public class OCLAddressNode extends AddressNode implements LIRLowerable {

    public static final NodeClass<OCLAddressNode> TYPE = NodeClass.create(OCLAddressNode.class);

    @OptionalInput
    private ValueNode base;

    @OptionalInput
    private ValueNode index;

    private OCLMemoryBase memoryRegister;

    public OCLAddressNode(ValueNode base, ValueNode index, OCLMemoryBase memoryRegister) {
        super(TYPE);
        this.base = base;
        this.index = index;
        this.memoryRegister = memoryRegister;
    }

    public OCLAddressNode(ValueNode base, ValueNode index) {
        super(TYPE);
        this.base = base;
        this.index = index;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        OCLLIRGenerator tool = (OCLLIRGenerator) gen.getLIRGeneratorTool();

        Value baseValue = base == null ? Value.ILLEGAL : gen.operand(base);
        if (base instanceof ParameterNode && base.stamp(NodeView.DEFAULT) instanceof OCLStamp) {
            OCLStamp stamp = (OCLStamp) base.stamp(NodeView.DEFAULT);
            OCLKind kind = stamp.getOCLKind();
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
        return this.memoryRegister.getName().equals(OCLAssemblerConstants.LOCAL_REGION_NAME);
    }

    private boolean isPrivateMemoryAccess() {
        return this.memoryRegister.getName().equals(OCLAssemblerConstants.PRIVATE_REGION_NAME);
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

    private void setMemoryAccess(NodeLIRBuilderTool gen, Value baseValue, Value indexValue, OCLLIRGenerator tool) {
        Variable addressValue;

        if (isLocalMemoryAccess() || isPrivateMemoryAccess()) {
            gen.setResult(this, new MemoryAccess(memoryRegister, baseValue, indexValue));
        } else {
            addressValue = tool.getArithmetic().emitAdd(baseValue, indexValue, false);
            gen.setResult(this, new MemoryAccess(memoryRegister, addressValue));
        }
    }
}
