package tornado.drivers.opencl.runtime;

import static tornado.common.exceptions.TornadoInternalError.guarantee;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import tornado.common.DeviceMapping;
import tornado.common.Tornado;
import tornado.common.enums.Access;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.OpenCLInstalledCode;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.meta.domain.DomainTree;
import tornado.runtime.api.PrebuiltTask;

public class OCLPrebuiltTask extends PrebuiltTask {

	 private OpenCLInstalledCode code;
	    private OCLBackend backend;
	
	protected OCLPrebuiltTask(String entryPoint,String filename, Object[] args,
			Access[] access, OCLDeviceMapping device, DomainTree domain) {
		super(entryPoint,filename, args, access, device,domain);
		
		backend = device.getBackend();
	}
	
	
	public void dumpCode() {
        for (byte b : code.getCode())
            System.out.printf("%c", b);

    }

	 public void compile() {
	        final Path path = Paths.get(filename);
	        guarantee(path.toFile().exists(), "file does not exist: %s", filename);
	        try {
	            final byte[] source = Files.readAllBytes(path);
	            code = backend.getCodeCache().addMethod(null,entryPoint,
	                    source);
	        } catch (IOException e) {
	            TornadoInternalError.shouldNotReachHere();
	        }

	    }
	 
	 public OpenCLInstalledCode getCode(){
		 return code;
	 }
	
}
