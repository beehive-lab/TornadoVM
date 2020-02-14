package uk.ac.manchester.tornado.drivers.cuda.graal.lir;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;

public class PTXAddressLowering extends AddressLoweringPhase.AddressLowering {

    @Override public AddressNode lower(ValueNode base, ValueNode offset) {
        return null;
    }
}
