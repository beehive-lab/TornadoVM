package uk.ac.manchester.tornado.drivers.spirv.common;

import uk.ac.manchester.tornado.runtime.common.TornadoOptions;

public class SPIRVLogger {

    public static class Colors {
        private static final String RESET = "\u001B[0m";
        private static final String BLACK = "\u001B[30m";
        private static final String RED = "\u001B[31m";
        private static final String GREEN = "\u001B[32m";
        private static final String YELLOW = "\u001B[33m";
        private static final String BLUE = "\u001B[34m";
        private static final String PURPLE = "\u001B[35m";
        private static final String CYAN = "\u001B[36m";
        private static final String WHITE = "\u001B[37m";
    }

    public static void trace(final String message, final Object... args) {
        // https://apps.timwhitlock.info/emoji/tables/unicode
        System.out.printf("\uD83D\uDC49 " + message + "\n", args);
    }

    public static void traceCodeGen(final String message, final Object... args) {
        if (TornadoOptions.TRACE_CODE_GEN) {
            System.out.printf(Colors.CYAN + "[SPIRV-CodeGen] " + message + Colors.RESET + "\n", args);
        }
    }

    public static void traceBuildLIR(String message, final Object... args) {
        if (TornadoOptions.TRACE_BUILD_LIR) {
            System.out.printf(Colors.GREEN + "[SPIRV-BuildLIR] " + message + Colors.RESET + "\n", args);
        }
    }
}
