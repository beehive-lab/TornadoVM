package uk.ac.manchester.tornado.drivers.cuda;

import uk.ac.manchester.tornado.drivers.cuda.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.PTXCompilationResult;

import java.util.concurrent.ConcurrentHashMap;

import static java.util.jar.Pack200.Unpacker.FALSE;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.runtime.common.Tornado.getProperty;

public class CUDACodeCache {

    private static final boolean PRINT_SOURCE = Boolean.parseBoolean(getProperty("tornado.opencl.source.print", FALSE));
    private final CUDADeviceContext deviceContext;
    private final ConcurrentHashMap<String, PTXInstalledCode> cache;

    public CUDACodeCache(CUDADeviceContext deviceContext) {
        this.deviceContext = deviceContext;
        cache = new ConcurrentHashMap<>();
    }

    public PTXInstalledCode installSource(PTXCompilationResult result) {
        String cacheKey = result.getName();

        if (!cache.containsKey(cacheKey)) {
            if (PRINT_SOURCE) {
                String source = new String(result.getTargetCode());
                System.out.println(source);
            }

            CUDAModule module = new CUDAModule(result.getTargetCode(), result.getName(), result.getTaskMeta());

            if (module.getIsPTXJITSuccess()) {
                PTXInstalledCode code = new PTXInstalledCode(result.getName(), module, deviceContext);
                cache.put(cacheKey, code);
                return code;
            }
            else {
                shouldNotReachHere("PTX JIT compilation failed!");
            }
        }

        return cache.get(cacheKey);
    }

    public boolean isCached(String name) {
        return cache.containsKey(name);
    }
}
