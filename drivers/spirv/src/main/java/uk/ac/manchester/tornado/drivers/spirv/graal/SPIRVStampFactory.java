package uk.ac.manchester.tornado.drivers.spirv.graal;

import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

public class SPIRVStampFactory {

    // FIXME: <REFACTOR> USe a HashMap instead of an array
    private static final SPIRVStamp[] stamps;

    static {
        stamps = new SPIRVStamp[SPIRVKind.values().length];
    }

    public static SPIRVStamp getStampFor(SPIRVKind kind) {
        int index = 0;
        for (SPIRVKind spirvKind : SPIRVKind.values()) {
            if (spirvKind == kind) {
                break;
            }
            index++;
        }
        if (stamps[index] == null) {
            stamps[index] = new SPIRVStamp(kind);
        }
        return stamps[index];
    }
}
