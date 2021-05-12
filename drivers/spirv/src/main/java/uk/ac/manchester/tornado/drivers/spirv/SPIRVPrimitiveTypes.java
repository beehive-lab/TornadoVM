package uk.ac.manchester.tornado.drivers.spirv;

import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeBool;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeFloat;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeVoid;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.tornado.drivers.spirv.common.SPIRVLogger;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

import java.util.HashMap;
import java.util.Map;

public class SPIRVPrimitiveTypes {

    private Map<SPIRVKind, SPIRVId> primitives;

    private final uk.ac.manchester.spirvproto.lib.SPIRVModule module;

    public SPIRVPrimitiveTypes(uk.ac.manchester.spirvproto.lib.SPIRVModule module) {
        this.module = module;
        this.primitives = new HashMap<>();
    }

    public SPIRVId getTypePrimitive(SPIRVKind primitive) {
        SPIRVLogger.traceCodeGen("Adding primitive: " + primitive);
        if (!primitives.containsKey(primitive)) {
            SPIRVId typeID = module.getNextId();
            int sizeInBytes = primitive.getSizeInBytes() * 8;
            switch (primitive) {
                case OP_TYPE_VOID:
                    module.add(new SPIRVOpTypeVoid(typeID));
                    break;
                case OP_TYPE_BOOL:
                    module.add(new SPIRVOpTypeBool(typeID));
                    break;
                case OP_TYPE_INT_8:
                case OP_TYPE_INT_64:
                case OP_TYPE_INT_32:
                    module.add(new SPIRVOpTypeInt(typeID, new SPIRVLiteralInteger(sizeInBytes), new SPIRVLiteralInteger(0)));
                    break;
                case OP_TYPE_FLOAT_32:
                case OP_TYPE_FLOAT_64:
                    module.add(new SPIRVOpTypeFloat(typeID, new SPIRVLiteralInteger(sizeInBytes)));
                    break;
                default:
                    throw new RuntimeException("DataType Not supported yet");
            }
            primitives.put(primitive, typeID);
        }
        return primitives.get(primitive);
    }

    public SPIRVId getTypeVoid() {
        return getTypePrimitive(SPIRVKind.OP_TYPE_VOID);
    }

    public void emitTypeVoid() {
        getTypeVoid();
    }

}
