/*
 * Simple smoke test for Metal kernel reflection. This test only runs on macOS
 * and requires `xcrun` (metal/metallib) to be available. It compiles a tiny
 * MSL kernel via MetalCodeCache.compileAndLink and asserts that reflection
 * reports argument metadata.
 */
package uk.ac.manchester.tornado.drivers.metal;

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import uk.ac.manchester.tornado.drivers.metal.graal.MetalInstalledCode;

public class TestMetalReflectionSmoke {

    private static boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac");
    }

    private static boolean hasXcrun() {
        try {
            Process p = new ProcessBuilder("xcrun", "--version").start();
            int rc = p.waitFor();
            return rc == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    public void smokeReflection() throws Exception {
        assumeTrue("macOS required for Metal tests", isMac());
        assumeTrue("xcrun required for Metal toolchain", hasXcrun());

            // trivial Metal kernel: takes 2 buffers and writes result to out[0]
            String msl = "using namespace metal; kernel void add(device const float* a [[buffer(0)]], device const float* b [[buffer(1)]], device float* out [[buffer(2)]]) { uint id = static_cast<uint>(get_thread_position_in_grid().x); out[id] = a[id] + b[id]; }";

        // Obtain platform/context/device context using public APIs
        MetalPlatform platform = (MetalPlatform) Metal.getPlatform(0);
        MetalContext context = platform.createContext();
        context.createCommandQueue(0);
        MetalDeviceContext ctx = context.createDeviceContext(0);

        MetalCodeCache cache = new MetalCodeCache(ctx);
        MetalInstalledCode installed = cache.compileAndLink("add", msl);
        assertTrue("installed code should not be null", installed != null);
        MetalKernel k = installed.getKernel();
        assertTrue("kernel must be present", k != null);

        int n = k.getArgCount();
        // Expect at least 3 buffer arguments (a,b,out)
        assertTrue("expected at least 3 args, got " + n, n >= 3);
        MetalKernel.KernelArgInfo info0 = k.getArgInfoObject(0);
        assertTrue("arg0 info should be non-null", info0 != null);
        // Name may be empty depending on reflection granularity; just ensure parsing succeeded
        assertTrue("index must match", info0.index == 0);
    }
}
