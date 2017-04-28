/* 
 * Copyright 2012 James Clarkson.
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
 */
package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Value;
import tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import tornado.drivers.opencl.graal.lir.OCLUnary.MemoryAccess;

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

}
