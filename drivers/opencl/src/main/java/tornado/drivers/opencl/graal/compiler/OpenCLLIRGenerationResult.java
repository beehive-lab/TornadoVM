package tornado.drivers.opencl.graal.compiler;

import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResultBase;


public class OpenCLLIRGenerationResult extends LIRGenerationResultBase {

	public OpenCLLIRGenerationResult(
			String compilationUnitName,
			LIR lir,
			FrameMapBuilder frameMapBuilder) {
		super(compilationUnitName, lir, frameMapBuilder);
	}

}
