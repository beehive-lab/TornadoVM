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
