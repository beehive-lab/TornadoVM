package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.FixedArrayNode;

public class SPIRVAddressLowering extends AddressLoweringPhase.AddressLowering {

    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {
        SPIRVArchitecture.SPIRVMemoryBase memoryRegister = SPIRVArchitecture.globalSpace;
        if (base instanceof FixedArrayNode) {
            memoryRegister = ((FixedArrayNode) base).getMemoryRegister();
        } else if (!((base instanceof ParameterNode) || (base instanceof ReadNode) || (base instanceof FloatingReadNode) || (base instanceof PiNode))) {
            TornadoInternalError.unimplemented("address origin unimplemented: %s", base.getClass().getName());
        }

        SPIRVAddressNode result = new SPIRVAddressNode(base, offset, memoryRegister);
        return base.graph().unique(result);
    }
}
