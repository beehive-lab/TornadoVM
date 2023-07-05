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
package uk.ac.manchester.tornado.drivers.spirv.tests;

import java.math.BigInteger;

import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVInstScope;
import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVModule;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpBitcast;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpCapability;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpConstant;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpDecorate;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpEntryPoint;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpExtInstImport;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFunction;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFunctionEnd;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFunctionParameter;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpIAdd;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpMemoryModel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpReturn;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpSource;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpTypeFunction;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpTypePointer;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpTypeVoid;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpVariable;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVAddressingModel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVCapability;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentInt;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentLong;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVDecoration;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVExecutionModel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVFunctionControl;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMemoryModel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVSourceLanguage;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVStorageClass;

public class TestLKBufferAccess {

    public static void testAssignWithLookUpBuffer(SPIRVModule module) {

        SPIRVInstScope functionScope;
        SPIRVInstScope blockScope;

        // Add All Capabilities
        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses())); // Uses physical addressing, non-logical addressing modes.
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage())); // Uses partially linked modules and libraries. (e.g., OpenCL)
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel())); // Uses the Kernel Execution Model.
        module.add(new SPIRVOpCapability(SPIRVCapability.Int64())); // Uses OpTypeInt to declare 64-bit integer types
        module.add(new SPIRVOpCapability(SPIRVCapability.Int8())); //

        // Add import OpenCL STD
        SPIRVId idImport = module.getNextId();
        module.add(new SPIRVOpExtInstImport(idImport, new SPIRVLiteralString("OpenCL.std")));

        // Set the memory model to Physical64 with OpenCL
        module.add(new SPIRVOpMemoryModel(SPIRVAddressingModel.Physical64(), SPIRVMemoryModel.OpenCL()));

        // SPIRVId idSPIRVBuiltin = module.getNextId();

        // Add module entry function
        SPIRVId mainFunctionID = module.getNextId();
        module.add(new SPIRVOpEntryPoint(SPIRVExecutionModel.Kernel(), mainFunctionID, new SPIRVLiteralString("copyTestZero"), new SPIRVMultipleOperands<>()));

        // OpSource
        module.add(new SPIRVOpSource(SPIRVSourceLanguage.OpenCL_C(), new SPIRVLiteralInteger(100000), new SPIRVOptionalOperand<>(), new SPIRVOptionalOperand<>()));

        SPIRVId heapBaseAddr = module.getNextId();
        SPIRVId frameBaseAddr = module.getNextId();
        SPIRVId ul0 = module.getNextId();
        SPIRVId ul1 = module.getNextId();
        SPIRVId frame = module.getNextId();
        module.add(new SPIRVOpDecorate(heapBaseAddr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(frameBaseAddr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(ul0, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(ul1, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(frame, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));

        // Type declaration
        SPIRVId uchar = module.getNextId();
        SPIRVId ulong = module.getNextId();
        SPIRVId uint = module.getNextId();
        module.add(new SPIRVOpTypeInt(uchar, new SPIRVLiteralInteger(8), new SPIRVLiteralInteger(0)));
        module.add(new SPIRVOpTypeInt(ulong, new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(0)));
        module.add(new SPIRVOpTypeInt(uint, new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)));

        // CONSTANTS
        // Index for the stack-frame
        SPIRVId ulongConstant3 = module.getNextId();
        SPIRVId ulongConstant24 = module.getNextId();
        SPIRVId uintConstant50 = module.getNextId();
        module.add(new SPIRVOpConstant(ulong, ulongConstant3, new SPIRVContextDependentLong(BigInteger.valueOf(3))));
        module.add(new SPIRVOpConstant(ulong, ulongConstant24, new SPIRVContextDependentLong(BigInteger.valueOf(24))));
        module.add(new SPIRVOpConstant(uint, uintConstant50, new SPIRVContextDependentInt(BigInteger.valueOf(50))));

        // OpVoid
        SPIRVId voidType = module.getNextId();
        module.add(new SPIRVOpTypeVoid(voidType));

        // Type pointers
        SPIRVId ptrCrossWorkGroupUChar = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUChar, SPIRVStorageClass.CrossWorkgroup(), uchar));

        SPIRVId functionPre = module.getNextId();
        module.add(new SPIRVOpTypeFunction(functionPre, voidType, new SPIRVMultipleOperands<>(ptrCrossWorkGroupUChar, ulong)));

        SPIRVId ptrFunctionPTRCrossWorkGroupUChar = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPTRCrossWorkGroupUChar, SPIRVStorageClass.Function(), ptrCrossWorkGroupUChar));

        SPIRVId ptrFunctionUlong = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionUlong, SPIRVStorageClass.Function(), ulong));

        SPIRVId ptrCrossWorkGroupUlong = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUlong, SPIRVStorageClass.Function(), ulong));

        SPIRVId ptrFunctionPTRCrossWorkGroupULong = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPTRCrossWorkGroupULong, SPIRVStorageClass.Function(), ptrCrossWorkGroupUlong));

        SPIRVId ptrCrossWorkGroupUInt = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUInt, SPIRVStorageClass.CrossWorkgroup(), uint));

        functionScope = module.add(new SPIRVOpFunction(voidType, mainFunctionID, SPIRVFunctionControl.DontInline(), functionPre));

        SPIRVId heap_base = module.getNextId();
        functionScope.add(new SPIRVOpFunctionParameter(ptrCrossWorkGroupUChar, heap_base));

        SPIRVId frame_base = module.getNextId();
        functionScope.add(new SPIRVOpFunctionParameter(ulong, frame_base));

        SPIRVId entry = module.getNextId();
        blockScope = functionScope.add(new SPIRVOpLabel(entry));

        // Variable declaration within the module
        blockScope.add(new SPIRVOpVariable(ptrFunctionPTRCrossWorkGroupUChar, heapBaseAddr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUlong, frameBaseAddr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUlong, ul0, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUlong, ul1, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionPTRCrossWorkGroupULong, frame, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));

        blockScope.add(new SPIRVOpStore(heapBaseAddr, heap_base, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        blockScope.add(new SPIRVOpStore(frameBaseAddr, frame_base, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id20 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ptrCrossWorkGroupUChar, id20, heapBaseAddr, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id21 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ulong, id21, frameBaseAddr, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId ptridx = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(ptrCrossWorkGroupUChar, ptridx, id20, id21, new SPIRVMultipleOperands<>()));

        SPIRVId id23 = module.getNextId();
        blockScope.add(new SPIRVOpBitcast(ptrCrossWorkGroupUlong, id23, ptridx));

        blockScope.add(new SPIRVOpStore(frame, id23, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id24 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ptrCrossWorkGroupUlong, id24, frame, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId ptridx1 = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(ptrCrossWorkGroupUlong, ptridx1, id24, ulongConstant3, new SPIRVMultipleOperands<>()));

        SPIRVId id27 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ulong, id27, ptridx1, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        blockScope.add(new SPIRVOpStore(ul0, id27, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id28 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ulong, id28, ul0, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId add = module.getNextId();
        blockScope.add(new SPIRVOpIAdd(ulong, add, id28, ulongConstant24));

        blockScope.add(new SPIRVOpStore(ul1, add, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id31 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ulong, id31, ul1, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id34 = module.getNextId();
        blockScope.add(new SPIRVOpConvertUToPtr(ptrCrossWorkGroupUInt, id34, id31));

        blockScope.add(new SPIRVOpStore(id34, uintConstant50, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));

        blockScope.add(new SPIRVOpReturn());
        functionScope.add(new SPIRVOpFunctionEnd());
    }

    public static void testAssignWithLookUpBufferOptimized(SPIRVModule module) {

        SPIRVInstScope functionScope;
        SPIRVInstScope blockScope;

        // Add All Capabilities
        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses())); // Uses physical addressing, non-logical addressing modes.
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage())); // Uses partially linked modules and libraries. (e.g., OpenCL)
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel())); // Uses the Kernel Execution Model.
        module.add(new SPIRVOpCapability(SPIRVCapability.Int64())); // Uses OpTypeInt to declare 64-bit integer types
        module.add(new SPIRVOpCapability(SPIRVCapability.Int8())); //

        // Add import OpenCL STD
        SPIRVId idImport = module.getNextId();
        module.add(new SPIRVOpExtInstImport(idImport, new SPIRVLiteralString("OpenCL.std")));

        // Set the memory model to Physical64 with OpenCL
        module.add(new SPIRVOpMemoryModel(SPIRVAddressingModel.Physical64(), SPIRVMemoryModel.OpenCL()));

        // SPIRVId idSPIRVBuiltin = module.getNextId();

        // Add module entry function
        SPIRVId mainFunctionID = module.getNextId();
        module.add(new SPIRVOpEntryPoint(SPIRVExecutionModel.Kernel(), mainFunctionID, new SPIRVLiteralString("copyTestZero"), new SPIRVMultipleOperands<>()));

        // OpSource
        module.add(new SPIRVOpSource(SPIRVSourceLanguage.OpenCL_C(), new SPIRVLiteralInteger(100000), new SPIRVOptionalOperand<>(), new SPIRVOptionalOperand<>()));

        SPIRVId heapBaseAddr = module.getNextId();
        SPIRVId frameBaseAddr = module.getNextId();
        SPIRVId ul0 = module.getNextId();
        SPIRVId ul1 = module.getNextId();
        SPIRVId frame = module.getNextId();
        module.add(new SPIRVOpDecorate(heapBaseAddr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(frameBaseAddr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(ul0, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(ul1, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));
        module.add(new SPIRVOpDecorate(frame, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8))));

        // Type declaration
        SPIRVId uchar = module.getNextId();
        SPIRVId ulong = module.getNextId();
        SPIRVId uint = module.getNextId();
        module.add(new SPIRVOpTypeInt(uchar, new SPIRVLiteralInteger(8), new SPIRVLiteralInteger(0)));
        module.add(new SPIRVOpTypeInt(ulong, new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(0)));
        module.add(new SPIRVOpTypeInt(uint, new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)));

        // CONSTANTS
        // Index for the stack-frame
        SPIRVId ulongConstant3 = module.getNextId();
        SPIRVId ulongConstant24 = module.getNextId();
        SPIRVId uintConstant50 = module.getNextId();
        module.add(new SPIRVOpConstant(ulong, ulongConstant3, new SPIRVContextDependentLong(BigInteger.valueOf(3))));
        module.add(new SPIRVOpConstant(ulong, ulongConstant24, new SPIRVContextDependentLong(BigInteger.valueOf(24))));
        module.add(new SPIRVOpConstant(uint, uintConstant50, new SPIRVContextDependentInt(BigInteger.valueOf(50))));

        // OpVoid
        SPIRVId voidType = module.getNextId();
        module.add(new SPIRVOpTypeVoid(voidType));

        // Type pointers
        SPIRVId ptrCrossWorkGroupUChar = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUChar, SPIRVStorageClass.CrossWorkgroup(), uchar));

        SPIRVId functionPre = module.getNextId();
        module.add(new SPIRVOpTypeFunction(functionPre, voidType, new SPIRVMultipleOperands<>(ptrCrossWorkGroupUChar, ulong)));

        SPIRVId ptrFunctionPTRCrossWorkGroupUChar = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPTRCrossWorkGroupUChar, SPIRVStorageClass.Function(), ptrCrossWorkGroupUChar));

        SPIRVId ptrFunctionUlong = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionUlong, SPIRVStorageClass.Function(), ulong));

        SPIRVId ptrCrossWorkGroupUlong = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUlong, SPIRVStorageClass.Function(), ulong));

        SPIRVId ptrFunctionPTRCrossWorkGroupULong = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPTRCrossWorkGroupULong, SPIRVStorageClass.Function(), ptrCrossWorkGroupUlong));

        SPIRVId ptrCrossWorkGroupUInt = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUInt, SPIRVStorageClass.CrossWorkgroup(), uint));

        functionScope = module.add(new SPIRVOpFunction(voidType, mainFunctionID, SPIRVFunctionControl.DontInline(), functionPre));

        SPIRVId heap_base = module.getNextId();
        functionScope.add(new SPIRVOpFunctionParameter(ptrCrossWorkGroupUChar, heap_base));

        SPIRVId frame_base = module.getNextId();
        functionScope.add(new SPIRVOpFunctionParameter(ulong, frame_base));

        SPIRVId entry = module.getNextId();
        blockScope = functionScope.add(new SPIRVOpLabel(entry));

        // Variable declaration within the module
        blockScope.add(new SPIRVOpVariable(ptrFunctionPTRCrossWorkGroupUChar, heapBaseAddr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUlong, frameBaseAddr, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUlong, ul0, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionUlong, ul1, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(ptrFunctionPTRCrossWorkGroupULong, frame, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));

        blockScope.add(new SPIRVOpStore(heapBaseAddr, heap_base, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        blockScope.add(new SPIRVOpStore(frameBaseAddr, frame_base, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id20 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ptrCrossWorkGroupUChar, id20, heapBaseAddr, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id21 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ulong, id21, frameBaseAddr, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId ptridx = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(ptrCrossWorkGroupUChar, ptridx, id20, id21, new SPIRVMultipleOperands<>()));

        SPIRVId id23 = module.getNextId();
        blockScope.add(new SPIRVOpBitcast(ptrCrossWorkGroupUlong, id23, ptridx));

        blockScope.add(new SPIRVOpStore(frame, id23, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id24 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ptrCrossWorkGroupUlong, id24, frame, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId ptridx1 = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(ptrCrossWorkGroupUlong, ptridx1, id24, ulongConstant3, new SPIRVMultipleOperands<>()));

        SPIRVId id27 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ulong, id27, ptridx1, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));
        blockScope.add(new SPIRVOpStore(ul0, id27, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id28 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(ulong, id28, ul0, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId add = module.getNextId();
        blockScope.add(new SPIRVOpIAdd(ulong, add, id28, ulongConstant24));

        SPIRVId id34 = module.getNextId();
        blockScope.add(new SPIRVOpConvertUToPtr(ptrCrossWorkGroupUInt, id34, add));

        blockScope.add(new SPIRVOpStore(id34, uintConstant50, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));

        blockScope.add(new SPIRVOpReturn());
        functionScope.add(new SPIRVOpFunctionEnd());
    }
}
