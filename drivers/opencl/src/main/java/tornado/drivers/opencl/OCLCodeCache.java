package tornado.drivers.opencl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.enums.OCLBuildStatus;
import tornado.drivers.opencl.graal.OCLInstalledCode;

import static tornado.common.Tornado.*;
import static tornado.drivers.opencl.enums.OCLBuildStatus.CL_BUILD_SUCCESS;

public class OCLCodeCache {

    private final boolean OPENCL_CACHE_ENABLE = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.enable", "False"));
    private final boolean OPENCL_LOAD_BINS = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.load", "False"));
    private final boolean OPENCL_DUMP_BINS = Boolean.parseBoolean(getProperty("tornado.opencl.codecache.dump", "False"));
    private final String OPENCL_CACHE_DIR = getProperty("tornado.opencl.codecache.dir", "opencl-cache");

    private final Map<String, OCLInstalledCode> cache;
    private final OCLDeviceContext deviceContext;

    public OCLCodeCache(OCLDeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        cache = new HashMap<>();

        if (OPENCL_CACHE_ENABLE || OPENCL_LOAD_BINS) {
            info("loading binaries into code cache");
            load();
        }
    }

    private Path resolveCacheDir() {
        final String deviceDir = String.format("device-%d-%d", deviceContext.getPlatformContext().getPlatformIndex(), deviceContext.getDevice().getIndex());
        final Path outDir = Paths.get(OPENCL_CACHE_DIR + "/" + deviceDir);
        if (!Files.exists(outDir)) {
            try {
                Files.createDirectories(outDir);
            } catch (IOException e) {
                error("unable to create cache dir: %s", outDir.toString());
                error(e.getMessage());
            }
        }

        TornadoInternalError.guarantee(Files.isDirectory(outDir), "cache directory is not a directory: %s", outDir.toAbsolutePath().toString());
        return outDir;
    }

    public OCLInstalledCode installSource(String entryPoint, byte[] source) {

        OCLInstalledCode code = null;

        info("Installing code for %s into code cache", entryPoint);
        final OCLProgram program = deviceContext.createProgramWithSource(source,
                new long[]{source.length});

        // TODO add support for passing compiler optimisation flags here
        final long t0 = System.nanoTime();
        program.build(OPENCL_CFLAGS);
        final long t1 = System.nanoTime();

        final OCLBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        debug("\tOpenCL compilation status = %s", status.toString());

        final String log = program.getBuildLog(deviceContext.getDeviceId()).trim();
        if (!log.isEmpty()) {
            debug(log);
        }

        final OCLKernel kernel = (status == CL_BUILD_SUCCESS) ? program.getKernel(entryPoint) : null;

        code = new OCLInstalledCode(entryPoint, source, deviceContext, program,
                kernel);

        if (status == CL_BUILD_SUCCESS) {
            debug("\tOpenCL Kernel id = 0x%x", kernel.getId());
            if (PRINT_COMPILE_TIMES) {
                System.out.printf("compile: kernel %s opencl %.9f\n", entryPoint, (t1 - t0) * 1e-9f);
            }
            cache.put(entryPoint, code);

            // BUG Apple does not seem to like implementing the OpenCL spec properly, this causes a sigfault.
            if ((OPENCL_CACHE_ENABLE || OPENCL_DUMP_BINS) && !deviceContext.getPlatformContext().getPlatform().getVendor().equalsIgnoreCase("Apple")) {
                final Path outDir = resolveCacheDir();
                program.dumpBinaries(outDir.toAbsolutePath().toString() + "/" + entryPoint);
            }
        } else {
            warn("\tunable to compile %s", entryPoint);
            code.invalidate();
        }

        return code;
    }

    public OCLInstalledCode installBinary(String entryPoint, byte[] binary) {
        return installBinary(entryPoint, binary, false);
    }

    private OCLInstalledCode installBinary(String entryPoint, byte[] binary, boolean alreadyCached) {
        OCLInstalledCode code = null;

        info("Installing binary for %s into code cache", entryPoint);
        final OCLProgram program = deviceContext.createProgramWithBinary(binary,
                new long[]{binary.length});

        // TODO add support for passing compiler optimisation flags here
        final long t0 = System.nanoTime();
        program.build(OPENCL_CFLAGS);
        final long t1 = System.nanoTime();

        final OCLBuildStatus status = program.getStatus(deviceContext.getDeviceId());
        debug("\tOpenCL compilation status = %s", status.toString());

        final String log = program.getBuildLog(deviceContext.getDeviceId()).trim();
        if (!log.isEmpty()) {
            debug(log);
        }

        final OCLKernel kernel = (status == CL_BUILD_SUCCESS) ? program.getKernel(entryPoint) : null;

        code = new OCLInstalledCode(entryPoint, null, deviceContext, program,
                kernel);

        if (status == CL_BUILD_SUCCESS) {
            debug("\tOpenCL Kernel id = 0x%x", kernel.getId());
            if (PRINT_COMPILE_TIMES) {
                System.out.printf("compile: kernel %s opencl %.9f\n", entryPoint, (t1 - t0) * 1e-9f);
            }
            cache.put(entryPoint, code);

            if ((OPENCL_CACHE_ENABLE || OPENCL_DUMP_BINS) && !alreadyCached) {
                final Path outDir = resolveCacheDir();
                writeToFile(outDir.toAbsolutePath().toString() + "/" + entryPoint, binary);
            }

        } else {
            warn("\tunable to install binary for %s", entryPoint);
            code.invalidate();
        }

        return code;
    }

    private void load() {
        try {
            final Path cacheDir = resolveCacheDir();
            Files.list(cacheDir)
                    .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .forEach(this::loadBinary);
        } catch (IOException e) {
            error("io exception when loading cache files: %s", e.getMessage());
        }
    }

    private void loadBinary(Path path) {
        final File file = path.toFile();
        try {
            final byte[] binary = Files.readAllBytes(path);
            installBinary(file.getName(), binary, true);
        } catch (IOException e) {
            error("unable to load binary: %s (%s)", file, e.getMessage());
        }
    }

    private void writeToFile(String file, byte[] binary) {

        info("dumping binary %s", file);
        try (FileOutputStream fis = new FileOutputStream(file);) {
            fis.write(binary);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reset() {
        for (OCLInstalledCode code : cache.values()) {
            code.invalidate();
        }

        cache.clear();
    }

    public boolean isCached(String entryPoint) {
        return cache.containsKey(entryPoint);
    }

    public OCLInstalledCode getCode(String entryPoint) {
        return cache.get(entryPoint);
    }
}
