package uk.ac.manchester.tornado.drivers.spirv;

import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeVoid;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

public class SPIRVPrimitiveTypes {

    private Map<SPIRVKind, SPIRVId> primitives;

    private final uk.ac.manchester.spirvproto.lib.SPIRVModule module;

    public SPIRVPrimitiveTypes(uk.ac.manchester.spirvproto.lib.SPIRVModule module) {
        this.module = module;
        this.primitives = new HashMap<>();
    }

    public SPIRVId getTypeInt(SPIRVKind primitive) {
        if (!primitives.containsKey(primitive)) {
            SPIRVId typeID = module.getNextId();
            int sizeInBytes = primitive.getSizeInBytes() * 8;
            module.add(new SPIRVOpTypeInt(typeID, new SPIRVLiteralInteger(sizeInBytes), new SPIRVLiteralInteger(0)));
            primitives.put(primitive, typeID);
        }
        return primitives.get(primitive);
    }

    public SPIRVId getTypeVoid() {
        if (!primitives.containsKey(SPIRVKind.OP_TYPE_VOID)) {
            SPIRVId type = module.getNextId();
            module.add(new SPIRVOpTypeVoid(type));
            primitives.put(SPIRVKind.OP_TYPE_VOID, type);
            return type;
        }
        return primitives.get(SPIRVKind.OP_TYPE_VOID);
    }

    public void emitTypeVoid() {
        getTypeVoid();
    }

}
