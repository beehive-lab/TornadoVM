package tornado.drivers.opencl.graal.lir;

import com.oracle.graal.api.meta.AllocatableValue;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.lir.Opcode;

@Opcode("RETURN")
public class OCLReturnSlot extends AllocatableValue {

	public OCLReturnSlot(LIRKind lirKind) {
		super(lirKind);
	}

}
