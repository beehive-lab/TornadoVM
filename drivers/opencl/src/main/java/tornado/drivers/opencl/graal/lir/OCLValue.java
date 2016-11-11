package tornado.drivers.opencl.graal.lir;

import tornado.drivers.opencl.graal.asm.OCLAssembler;

public interface OCLValue {

	public String toValueString(OCLAssembler asm);
}
