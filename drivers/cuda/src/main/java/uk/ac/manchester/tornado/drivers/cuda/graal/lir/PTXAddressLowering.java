package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.FloatingReadNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.FixedArrayNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.LocalArrayNode;

import static uk.ac.manchester.tornado.drivers.cuda.graal.PTXArchitecture.PTXMemoryBase;

public class PTXAddressLowering extends AddressLoweringPhase.AddressLowering {

    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {
        PTXMemoryBase memoryRegister = PTXArchitecture.globalSpace;
        if (base instanceof FixedArrayNode) {
            memoryRegister = ((FixedArrayNode) base).getMemoryRegister();
        } else if (base instanceof LocalArrayNode) {
            memoryRegister = ((LocalArrayNode) base).getMemoryRegister();
        } else if (!((base instanceof ParameterNode) || (base instanceof ReadNode) || (base instanceof FloatingReadNode) || (base instanceof PiNode))) {
            TornadoInternalError.unimplemented("address origin unimplemented: %s", base.getClass().getName());
        }

        PTXAddressNode result = new PTXAddressNode(base, offset, memoryRegister);
        return base.graph().unique(result);
    }
}
