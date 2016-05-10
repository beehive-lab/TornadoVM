package tornado.drivers.opencl.graal.lir;

import tornado.drivers.opencl.graal.asm.OpenCLAssembler;

public interface OCLValue {

	public String toValueString(OpenCLAssembler asm);
}
