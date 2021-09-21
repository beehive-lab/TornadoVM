package uk.ac.manchester.tornado.drivers.spirv;

import java.util.HashMap;
import java.util.Map;

import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeBool;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeFloat;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypePointer;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeVector;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypeVoid;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpUndef;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVStorageClass;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

public class SPIRVPrimitiveTypes {

    final private Map<SPIRVKind, SPIRVId> primitives;

    final private Map<SPIRVKind, SPIRVId> ptrToPrimitives;

    final private Map<SPIRVKind, SPIRVId> ptrToPrimitivesWorkGroup;

    final private Map<SPIRVKind, SPIRVId> crossGroupToPrimitives;

    final private Map<SPIRVKind, SPIRVId> undefTable;

    private final uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVModule module;

    public SPIRVPrimitiveTypes(uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVModule module) {
        this.module = module;
        this.primitives = new HashMap<>();
        this.ptrToPrimitives = new HashMap<>();
        this.crossGroupToPrimitives = new HashMap<>();
        this.undefTable = new HashMap<>();
        this.ptrToPrimitivesWorkGroup = new HashMap<>();
    }

    private SPIRVId getVectorType(SPIRVKind vectorType, SPIRVId typeID) {
        SPIRVId intPrimitiveId = getTypePrimitive(vectorType.getElementKind());
        module.add(new SPIRVOpTypeVector(typeID, intPrimitiveId, new SPIRVLiteralInteger(vectorType.getVectorLength())));
        primitives.put(vectorType, typeID);
        return primitives.get(vectorType);
    }

    public SPIRVId getTypePrimitive(SPIRVKind primitive) {
        if (!primitives.containsKey(primitive)) {
            SPIRVId typeID = module.getNextId();
            int sizeInBytes = primitive.getSizeInBytes() * 8;

            if (primitive.isVector()) {
                return getVectorType(primitive, typeID);
            }
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

    public SPIRVId getPtrOpTypePointerWithStorage(SPIRVKind primitive, SPIRVStorageClass storageClass) {
        SPIRVId primitiveId = getTypePrimitive(primitive);
        if (!ptrToPrimitivesWorkGroup.containsKey(primitive)) {
            SPIRVId resultType = module.getNextId();
            module.add(new SPIRVOpTypePointer(resultType, storageClass, primitiveId));
            ptrToPrimitivesWorkGroup.put(primitive, resultType);
        }
        return ptrToPrimitivesWorkGroup.get(primitive);
    }

    public SPIRVId getPtrOpTypePointerWithStorage(SPIRVKind primitive) {
        return getPtrOpTypePointerWithStorage(primitive, SPIRVStorageClass.Workgroup());
    }

    public SPIRVId getTypeVoid() {
        return getTypePrimitive(SPIRVKind.OP_TYPE_VOID);
    }

    public void emitTypeVoid() {
        getTypeVoid();
    }

    public SPIRVId getUndef(SPIRVKind vectorType) {
        if (undefTable.containsKey(vectorType)) {
            return undefTable.get(vectorType);
        } else {
            SPIRVId undefId = module.getNextId();
            SPIRVId typeId = getTypePrimitive(vectorType);
            module.add(new SPIRVOpUndef(typeId, undefId));
            undefTable.put(vectorType, undefId);
            return undefId;
        }
    }
}
