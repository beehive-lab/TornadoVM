package tornado.drivers.opencl.graal;

import java.util.ArrayList;
import java.util.List;

import tornado.common.Tornado;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLKernel;
import tornado.drivers.opencl.OCLProgram;
import tornado.drivers.opencl.enums.OCLBuildStatus;
import tornado.drivers.opencl.graal.backend.OCLBackend;

import com.oracle.graal.api.code.CodeCacheProvider;
import com.oracle.graal.api.code.CompilationResult;
import com.oracle.graal.api.code.DataSection.Data;
import com.oracle.graal.api.code.InstalledCode;
import com.oracle.graal.api.code.RegisterConfig;
import com.oracle.graal.api.code.SpeculationLog;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.meta.Constant;
import com.oracle.graal.api.meta.JavaConstant;
import com.oracle.graal.api.meta.ResolvedJavaMethod;

public class OpenCLCodeCache implements CodeCacheProvider {

	private OCLBackend								backend;

	private final List<OpenCLInstalledCode>	cache;
	private final TargetDescription								target;

	public OpenCLCodeCache(TargetDescription target) {
		this.target = target;
		cache = new ArrayList<OpenCLInstalledCode>();
	}
	
	
	public OpenCLInstalledCode addMethod(ResolvedJavaMethod method, String entryPoint, byte[] source){
		if (backend == null) Tornado.fatal("OpenCL code cache not initialised");
		
		OpenCLInstalledCode code = null;
		
		Tornado.info("Installing code for %s into code cache", entryPoint);
		final OCLDeviceContext deviceContext = backend.getDeviceContext();

		final OCLProgram program = deviceContext.createProgram(source,
				new long[] { source.length });

		// TODO add support for passing compiler optimisation flags here
		program.build(Tornado.OPENCL_CFLAGS);

		final OCLBuildStatus status = program.getStatus(deviceContext.getDeviceId());
		Tornado.debug("\tOpenCL compilation status = %s", status.toString());

		final String log = program.getBuildLog(deviceContext.getDeviceId()).trim();
		if (!log.isEmpty()) Tornado.debug(log);
		
		final OCLKernel kernel = (status == OCLBuildStatus.CL_BUILD_SUCCESS) ? program.getKernel(entryPoint) : null;
		

		code = new OpenCLInstalledCode(entryPoint, source, deviceContext, program,
				kernel);

		if (status == OCLBuildStatus.CL_BUILD_SUCCESS) {
			Tornado.debug("\tOpenCL Kernel id = 0x%x", kernel.getId());
			cache.add(code);
			if(Tornado.DUMP_BINARIES)
				program.dumpBinaries("./opencl-bin/" + entryPoint + "-platform-" + backend.getDeviceContext().getPlatformContext().getPlatformIndex());
		} else {
			Tornado.warn("\tunable to compile %s",entryPoint);
			code.invalidate();
		}

		
		return code;
	}
	
	public OpenCLInstalledCode addMethod(ResolvedJavaMethod method, byte[] source){
		return addMethod(method, method.getName(), source);
	}

	@Override
	public OpenCLInstalledCode addMethod(ResolvedJavaMethod method, CompilationResult result,
			SpeculationLog speculationLog, InstalledCode ic) {
		return addMethod(method,method.getName(),result.getTargetCode());
	}
	
	public void reset(){
		for(OpenCLInstalledCode code : cache)
			code.invalidate();
		
		cache.clear();
	}

	@Override
	public Data createDataItem(Constant arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SpeculationLog createSpeculationLog() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String disassemble(CompilationResult arg0, InstalledCode arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getMinimumOutgoingSize() {
		return 0;
	}

	@Override
	public RegisterConfig getRegisterConfig() {
		return null;
	}

	@Override
	public TargetDescription getTarget() {
		return target;
	}

	@Override
	public boolean needsDataPatch(JavaConstant arg0) {
		return false;
	}

	@Override
	public OpenCLInstalledCode setDefaultMethod(ResolvedJavaMethod arg0, CompilationResult arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	public void setBackend(OCLBackend value) {
		backend = value;

	}

}
