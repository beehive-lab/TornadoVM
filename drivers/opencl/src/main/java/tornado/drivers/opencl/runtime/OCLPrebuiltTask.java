package tornado.drivers.opencl.runtime;

import tornado.common.enums.Access;
import tornado.drivers.opencl.graal.OCLInstalledCode;
import tornado.drivers.opencl.graal.backend.OCLBackend;
import tornado.meta.domain.DomainTree;
import tornado.runtime.api.PrebuiltTask;

public class OCLPrebuiltTask extends PrebuiltTask {

    private OCLInstalledCode code;
    private final OCLBackend backend;

    protected OCLPrebuiltTask(String id, String entryPoint, String filename, Object[] args,
            Access[] access, OCLDeviceMapping device, DomainTree domain) {
        super(id, entryPoint, filename, args, access, device, domain);

        backend = device.getBackend();
    }

    public void dumpCode() {
        for (byte b : code.getCode()) {
            System.out.printf("%c", b);
        }

    }

//    public void compile() {
//        final Path path = Paths.get(filename);
//        guarantee(path.toFile().exists(), "file does not exist: %s", filename);
//        try {
//            final byte[] source = Files.readAllBytes(path);
//            code = backend.getCodeCache().addMethod(null, entryPoint,
//                    source);
//        } catch (IOException e) {
//            shouldNotReachHere();
//        }
//
//    }
    public OCLInstalledCode getCode() {
        return code;
    }

}
