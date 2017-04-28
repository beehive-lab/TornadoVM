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

import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.memory.FloatingReadNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.phases.common.AddressLoweringPhase.AddressLowering;
import tornado.drivers.opencl.graal.OCLArchitecture;
import tornado.drivers.opencl.graal.OCLArchitecture.OCLMemoryBase;
import tornado.drivers.opencl.graal.nodes.FixedArrayNode;

import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLAddressLowering extends AddressLowering {

    @Override
    public AddressNode lower(ValueNode address) {
        return lower(address, null);
    }

    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {

        OCLMemoryBase memoryRegister = OCLArchitecture.hp;
        if (base instanceof FixedArrayNode) {
            memoryRegister = ((FixedArrayNode) base).getMemoryRegister();
        } else if (!((base instanceof ParameterNode) || (base instanceof FloatingReadNode))) {
            unimplemented("address origin unimplemented: %s", base.getClass().getName());
        }

        OCLAddressNode result = new OCLAddressNode(base, offset, memoryRegister);
        return base.graph().unique(result);
    }

}
