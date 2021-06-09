package uk.ac.manchester.tornado.drivers.spirv;

import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeBool;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeFloat;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypePointer;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeVector;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeVoid;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVStorageClass;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

public class SPIRVPrimitiveTypes {

    final private Map<SPIRVKind, SPIRVId> primitives;

    final private Map<SPIRVKind, SPIRVId> ptrToPrimitives;

    final private Map<SPIRVKind, SPIRVId> crossGroupToPrimitives;

    private final uk.ac.manchester.spirvproto.lib.SPIRVModule module;

    public SPIRVPrimitiveTypes(uk.ac.manchester.spirvproto.lib.SPIRVModule module) {
        this.module = module;
        this.primitives = new HashMap<>();
        this.ptrToPrimitives = new HashMap<>();
        this.crossGroupToPrimitives = new HashMap<>();
    }

    public SPIRVId getTypePrimitive(SPIRVKind primitive) {
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
                case OP_TYPE_INT_16:
                case OP_TYPE_INT_64:
                case OP_TYPE_INT_32:
                    module.add(new SPIRVOpTypeInt(typeID, new SPIRVLiteralInteger(sizeInBytes), new SPIRVLiteralInteger(0)));
                    break;
                case OP_TYPE_FLOAT_32:
                case OP_TYPE_FLOAT_64:
                    module.add(new SPIRVOpTypeFloat(typeID, new SPIRVLiteralInteger(sizeInBytes)));
                    break;
                case OP_TYPE_VECTOR2_INT_32:
                    System.out.println("Registering a VECTOR: " + primitive);
                    SPIRVId intPrimitiveId = getTypePrimitive(primitive.getElementKind());
                    module.add(new SPIRVOpTypeVector(typeID, intPrimitiveId, new SPIRVLiteralInteger(primitive.getVectorLength())));
                    break;
                default:
                    throw new RuntimeException("DataType Not supported yet");
            }
            primitives.put(primitive, typeID);
        }
        return primitives.get(primitive);
    }

    public SPIRVId getPtrToTypePrimitive(SPIRVKind primitive, SPIRVStorageClass storageClass) {
        SPIRVId primitiveId = getTypePrimitive(primitive);
        if (!ptrToPrimitives.containsKey(primitive)) {
            SPIRVId resultType = module.getNextId();
            module.add(new SPIRVOpTypePointer(resultType, storageClass, primitiveId));
            ptrToPrimitives.put(primitive, resultType);
        }
        return ptrToPrimitives.get(primitive);
    }

    public SPIRVId getPtrToCrossGroupPrimitive(SPIRVKind primitive) {
        SPIRVId primitiveId = getTypePrimitive(primitive);
        if (!crossGroupToPrimitives.containsKey(primitive)) {
            SPIRVId resultType = module.getNextId();
            module.add(new SPIRVOpTypePointer(resultType, SPIRVStorageClass.CrossWorkgroup(), primitiveId));
            crossGroupToPrimitives.put(primitive, resultType);
        }
        return crossGroupToPrimitives.get(primitive);
    }

    public SPIRVId getPtrToTypePrimitive(SPIRVKind primitive) {
        return getPtrToTypePrimitive(primitive, SPIRVStorageClass.Function());
    }

    public SPIRVId getTypeVoid() {
        return getTypePrimitive(SPIRVKind.OP_TYPE_VOID);
    }

    public void emitTypeVoid() {
        getTypeVoid();
    }

}
