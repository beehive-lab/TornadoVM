package tornado.drivers.opencl.graal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import jdk.vm.ci.code.*;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import tornado.common.Tornado;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLKernel;
import tornado.drivers.opencl.OCLProgram;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.enums.OCLBuildStatus;
import tornado.drivers.opencl.graal.backend.OCLBackend;

import static tornado.common.Tornado.*;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import static tornado.drivers.opencl.enums.OCLBuildStatus.CL_BUILD_SUCCESS;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;
import static tornado.common.exceptions.TornadoInternalError.unimplemented;

public class OCLCodeCache implements CodeCacheProvider {

    private final String OPENCL_BIN_DIR = getProperty("tornado.opencl.bindir", "opencl-bin");
    private OCLBackend backend;

    private final List<OCLInstalledCode> cache;
    private final TargetDescription target;

    public OCLCodeCache(TargetDescription target) {
        this.target = target;
        cache = new ArrayList<>();
    }

    public OCLInstalledCode addMethod(ResolvedJavaMethod method, String entryPoint, byte[] source) {
        return installOCLProgram(entryPoint, source);
    }

    public OCLInstalledCode addMethod(ResolvedJavaMethod method, byte[] source) {
        return addMethod(method, method.getName(), source);
    }

    @Override
    public SpeculationLog createSpeculationLog() {
        // TODO Auto-generated method stub
        return null;
    }

    public void setBackend(OCLBackend value) {
        backend = value;

    }

    @Override
    public long getMaxCallTargetOffset(long l) {
        unimplemented();
        return -1;
    }

    @Override
    public int getMinimumOutgoingSize() {
        return 0;
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return new OCLRegisterConfig();
    }

    @Override
    public OCLTargetDescription getTarget() {
        return (OCLTargetDescription) target;
    }

    @Override
    public InstalledCode installCode(ResolvedJavaMethod rjm, CompiledCode cc, InstalledCode ic, SpeculationLog sl, boolean bln) {
        unimplemented("waiting for CompiledCode to be implemented first");
//  return addMethod(method, method.getName(), result.);
        return null;
    }

    public OCLInstalledCode installOCLProgram(String entryPoint, byte[] source) {
        if (backend == null) {
            fatal("OpenCL code cache not initialised");
        }

        OCLInstalledCode code = null;

        info("Installing code for %s into code cache", entryPoint);
        final OCLDeviceContext deviceContext = backend.getDeviceContext();

        final OCLProgram program = deviceContext.createProgram(source,
                new long[]{source.length});

        // TODO add support for passing compiler optimisation flags here
        final long t0 = System.nanoTime();
        program.build(Tornado.OPENCL_CFLAGS);
        final long t1 = System.nanoTime();

        final OCLBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        debug("\tOpenCL compilation status = %s", status.toString());

        final String log = program.getBuildLog(deviceContext.getDeviceId()).trim();
        if (!log.isEmpty()) {
            debug(log);
        }

        final OCLKernel kernel = (status == OCLBuildStatus.CL_BUILD_SUCCESS) ? program.getKernel(entryPoint) : null;

        code = new OCLInstalledCode(entryPoint, source, deviceContext, program,
                kernel);

        if (status == CL_BUILD_SUCCESS) {
            debug("\tOpenCL Kernel id = 0x%x", kernel.getId());
            if (PRINT_COMPILE_TIMES) {
                System.out.printf("compile: kernel %s opencl %.9f\n", entryPoint, (t1 - t0) * 1e-9f);
            }
            cache.add(code);

            // BUG Apple does not seem to like implementing the OpenCL spec properly, this causes a sigfault.
            if (DUMP_BINARIES && !backend.getDeviceContext().getPlatformContext().getPlatform().getVendor().equalsIgnoreCase("Apple")) {
                final Path outDir = Paths.get(OPENCL_BIN_DIR);
                if (!Files.exists(outDir)) {
                    try {
                        Files.createDirectories(outDir);
                    } catch (IOException e) {
                        error("unable to create OPENCL_BIN_DIR: %s", OPENCL_BIN_DIR);
                        error(e.getMessage());
                    }
                }
                if (Files.isDirectory(outDir)) {
                    program.dumpBinaries(OPENCL_BIN_DIR + "/" + entryPoint + "-platform-" + backend.getDeviceContext().getPlatformContext().getPlatformIndex());
                }
            }

        } else {
            warn("\tunable to compile %s", entryPoint);
            code.invalidate();
        }

        return code;
    }

    @Override
    public void invalidateInstalledCode(InstalledCode ic) {
        unimplemented();
    }

    public void reset() {
        for (OCLInstalledCode code : cache) {
            code.invalidate();
        }

        cache.clear();
    }

    @Override
    public boolean shouldDebugNonSafepoints() {
        unimplemented();
        return false;
    }

}
