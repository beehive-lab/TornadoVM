package uk.ac.manchester.tornado.drivers.spirv.common;

public class SPIRVLogger {

    // https://apps.timwhitlock.info/emoji/tables/unicode

    public static void trace(final String message, final Object... args) {
        System.out.printf("\uD83D\uDC49 " + message + "\n", args);
    }

}
