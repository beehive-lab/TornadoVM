package uk.ac.manchester.tornado.drivers.spirv.common;

import uk.ac.manchester.tornado.drivers.common.Colour;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVLogger {

    public static void traceCodeGen(final String message, final Object... args) {
        if (TornadoOptions.TRACE_CODE_GEN) {
            System.out.printf(Colour.CYAN + "[SPIRV-CodeGen] " + message + Colour.RESET + "\n", args);
        }
    }

    public static void traceBuildLIR(String message, final Object... args) {
        if (TornadoOptions.TRACE_BUILD_LIR) {
            System.out.printf(Colour.GREEN + "[SPIRV-BuildLIR] " + message + Colour.RESET + "\n", args);
        }
    }

    public static void traceRuntime(String message, final Object... args) {
        System.out.printf(Colour.YELLOW + "[SPIRV-Runtime] " + message + Colour.RESET + "\n", args);
    }
}
