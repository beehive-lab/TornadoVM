package uk.ac.manchester.tornado.drivers.cuda.graal.nodes.vector;

import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryIntrinsic;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;

import static uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler.PTXBinaryIntrinsic.*;

public class VectorUtil {
    private static final PTXBinaryIntrinsic[] loadTable = new PTXBinaryIntrinsic[] { VLOAD2, VLOAD3, VLOAD4, VLOAD8, VLOAD16 };

    public static PTXBinaryIntrinsic resolveLoadIntrinsic(PTXKind kind) {
        return lookupValueByLength(loadTable, kind);
    }

    private static <T> T lookupValueByLength(T[] array, PTXKind vectorKind) {
        final int index = vectorKind.lookupLengthIndex();
        if (index != -1) {
            return array[index];
        } else {
            throw TornadoInternalError.shouldNotReachHere("Unsupported vector type: " + vectorKind.toString());
        }
    }
}
