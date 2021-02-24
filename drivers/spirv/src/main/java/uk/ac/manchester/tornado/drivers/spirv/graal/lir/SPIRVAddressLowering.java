package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;

// FIXME: REVISIT 
public class SPIRVAddressLowering extends AddressLoweringPhase.AddressLowering {

    // FIXME: Revisit SPIRVAddressLowering During Graal Optimization Phases

    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {
        SPIRVArchitecture.SPIRVMemoryBase memoryRegister = SPIRVArchitecture.globalSpace;
        // if (base instanceof FixedArrayNode) {
        // memoryRegister = ((FixedArrayNode) base).getMemoryRegister();
        // } else if (base instanceof LocalArrayNode) {
        // memoryRegister = ((LocalArrayNode) base).getMemoryRegister();
        // } else if (!((base instanceof ParameterNode) || (base instanceof ReadNode) ||
        // (base instanceof FloatingReadNode) || (base instanceof PiNode))) {
        // TornadoInternalError.unimplemented("address origin unimplemented: %s",
        // base.getClass().getName());
        // }
        //
        // SPIRVAddressNode result = new SPIRVAddressNode(base, offset, memoryRegister);
        // return base.graph().unique(result);
        return null;
    }
}
