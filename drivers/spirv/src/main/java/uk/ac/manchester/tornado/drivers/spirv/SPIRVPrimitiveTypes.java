/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
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

    final private Map<SPIRVKind, SPIRVId> undefTable;

    final private Map<SPIRVKind, HashMap<String, SPIRVId>> ptrWithStorageClassToPrimitive;

    private final uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVModule module;

    public SPIRVPrimitiveTypes(uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVModule module) {
        this.module = module;
        this.primitives = new HashMap<>();
        this.undefTable = new HashMap<>();
        this.ptrWithStorageClassToPrimitive = new HashMap<>();
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

    public SPIRVId getPtrToCrossWorkGroupPrimitive(SPIRVKind primitive) {
        return getPtrOpTypePointerWithStorage(primitive, SPIRVStorageClass.CrossWorkgroup());
    }

    public SPIRVId getPtrToTypeFunctionPrimitive(SPIRVKind primitive) {
        return getPtrOpTypePointerWithStorage(primitive, SPIRVStorageClass.Function());
    }

    public SPIRVId getPtrOpTypePointerWithStorage(SPIRVKind primitive, SPIRVStorageClass storageClass) {
        SPIRVId primitiveId = getTypePrimitive(primitive);
        if (!ptrWithStorageClassToPrimitive.containsKey(primitive)) {
            HashMap<String, SPIRVId> spirvStorageClassSPIRVIdMap = new HashMap<>();
            SPIRVId resultType = module.getNextId();
            module.add(new SPIRVOpTypePointer(resultType, storageClass, primitiveId));
            spirvStorageClassSPIRVIdMap.put(storageClass.name, resultType);
            ptrWithStorageClassToPrimitive.put(primitive, spirvStorageClassSPIRVIdMap);
            return spirvStorageClassSPIRVIdMap.get(storageClass.name);
        } else {
            HashMap<String, SPIRVId> spirvStorageClassSPIRVIdMap = ptrWithStorageClassToPrimitive.get(primitive);
            if (!spirvStorageClassSPIRVIdMap.containsKey(storageClass.name)) {
                SPIRVId resultType = module.getNextId();
                module.add(new SPIRVOpTypePointer(resultType, storageClass, primitiveId));
                spirvStorageClassSPIRVIdMap.put(storageClass.name, resultType);
                ptrWithStorageClassToPrimitive.put(primitive, spirvStorageClassSPIRVIdMap);
            }
            return spirvStorageClassSPIRVIdMap.get(storageClass.name);
        }
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
