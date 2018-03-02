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
package uk.ac.manchester.tornado.drivers.opencl.graal.lir;

import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
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

    public OCLAddressNode(ValueNode base, OCLMemoryBase memoryRegister) {
        this(base, null, memoryRegister);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        OCLLIRGenerator tool = (OCLLIRGenerator) gen.getLIRGeneratorTool();

        Value baseValue = base == null ? Value.ILLEGAL : gen.operand(base);
        Value indexValue = index == null ? Value.ILLEGAL : gen.operand(index);

        if (index == null) {
            gen.setResult(this, new MemoryAccess(memoryRegister, baseValue, false));
        }

        Variable addressValue = tool.getArithmetic().emitAdd(baseValue, indexValue, false);
        gen.setResult(this, new MemoryAccess(memoryRegister, addressValue, false));
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

}
