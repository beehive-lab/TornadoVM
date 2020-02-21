package uk.ac.manchester.tornado.drivers.cuda.graal;

import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;

public class PTXStampFactory {
    private static final PTXStamp[] stamps = new PTXStamp[PTXKind.values().length];

    public static PTXStamp getStampFor(PTXKind kind) {
        int index = 0;
        for (PTXKind oclKind : PTXKind.values()) {
            if (oclKind == kind) {
                break;
            }
            index++;
        }

        if (stamps[index] == null) {
            stamps[index] = new PTXStamp(kind);
        }

        return stamps[index];
    }
}
