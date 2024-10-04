/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.asm;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import jdk.graal.compiler.asm.AbstractAddress;
import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVInstScope;
import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVModule;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVInstruction;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpBitwiseAnd;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpBitwiseOr;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpBitwiseXor;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpConstant;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpDecorate;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpEntryPoint;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpExecutionMode;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFAdd;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFDiv;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFMul;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFOrdEqual;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFOrdLessThan;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFSub;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFunction;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFunctionEnd;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpFunctionParameter;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpIAdd;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpIEqual;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpIMul;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpISub;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpName;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpSDiv;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpSLessThan;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpSRem;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpShiftLeftLogical;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpShiftRightArithmetic;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpShiftRightLogical;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpTypeArray;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpTypeFunction;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpTypePointer;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentDouble;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentFloat;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentHalfFloat;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentInt;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentLong;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVDecoration;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVExecutionMode;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVExecutionModel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVFunctionControl;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLinkageType;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVStorageClass;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVPrimitiveTypes;
import uk.ac.manchester.tornado.drivers.spirv.SPIRVThreadBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIROp;

public final class SPIRVAssembler extends Assembler {

    /**
     * Control and handling for the SPIR-V builtin functions
     */
    public final Map<SPIRVThreadBuiltIn, SPIRVId> builtinTable;
    private final Map<ConstantKeyPair, SPIRVId> constants;
    private final Map<Value, SPIRVId> lirTable;
    private final Map<String, SPIRVId> lirTableName;
    private final Map<String, SPIRVId> SPIRVSymbolTable;
    /**
     * Control for the SPIR-V module (beehive-spirv-toolkit)
     */
    public SPIRVModule module;
    /**
     * Control and Handling of SPIR-V primitives for the module being generated
     */
    public SPIRVPrimitiveTypes primitives;
    private SPIRVInstScope functionScope;
    private SPIRVId mainFunctionID;
    private SPIRVId functionSignature;
    private SPIRVInstScope blockZeroScope;
    private SPIRVId returnLabel;
    private boolean returnWithValue;
    private Stack<SPIRVInstScope> currentBlockScopeStack;
    /**
     * Table that stores the Block ID with its Label Reference ID
     */
    private Map<String, SPIRVId> labelTable;
    private Map<String, SPIRVInstScope> blockTable;
    private Map<Integer, SPIRVId> parametersId;
    private Map<SPIRVKind, HashMap<SPIRVId, SPIRVId>> arrayDeclarationTable;
    private Map<SPIRVId, SPIRVId> functionPtrToArray;
    private Map<SPIRVId, SPIRVId> functionPtrToArrayLocal;
    private SPIRVId kernelContextId;
    private SPIRVId ptrCrossWorkULong;
    private SPIRVId openclImport;
    private ByteBuffer spirvByteBuffer;
    private int methodIndex;

    private Map<SPIRVId, Map<Integer, LinkedList<FunctionTable>>> opFunctionTable;
    private Map<AllocatableValue, SPIRVId> phiMap;
    private Map<AllocatableValue, SPIRVId> phiNamesAcrossBlocks;
    private Map<AllocatableValue, AllocatableValue> phiTrace;
    private Map<AllocatableValue, SPIRVId> pendingIDs;
    private List<SPIRVId> parameterIdList;

    public SPIRVAssembler(TargetDescription target) {
        super(target, null);
        labelTable = new HashMap<>();
        blockTable = new HashMap<>();
        constants = new HashMap<>();
        parametersId = new HashMap<>();
        lirTable = new HashMap<>();
        lirTableName = new HashMap<>();
        builtinTable = new HashMap<>();
        currentBlockScopeStack = new Stack<>();
        arrayDeclarationTable = new HashMap<>();
        functionPtrToArray = new HashMap<>();
        functionPtrToArrayLocal = new HashMap<>();
        SPIRVSymbolTable = new HashMap<>();
        opFunctionTable = new HashMap<>();
        phiNamesAcrossBlocks = new HashMap<>();
        pendingIDs = new HashMap<>();
        parameterIdList = new ArrayList<>();
    }

    public SPIRVInstScope getFunctionScope() {
        return functionScope;
    }

    public SPIRVId getReturnLabel() {
        return returnLabel;
    }

    public void setReturnLabel(SPIRVId returnLabel) {
        this.returnLabel = returnLabel;
    }

    public SPIRVInstScope getBlockZeroScope() {
        return blockZeroScope;
    }

    public void setBlockZeroScope(SPIRVInstScope blockScope) {
        this.blockZeroScope = blockScope;
    }

    public Map<String, SPIRVInstScope> getBlockTable() {
        return blockTable;
    }

    public SPIRVId getKernelContextId() {
        return this.kernelContextId;
    }

    public void setKernelContextId(SPIRVId kernelContextId) {
        this.kernelContextId = kernelContextId;
    }

    public void setReturnWithValue(boolean returnWithValue) {
        this.returnWithValue = returnWithValue;
    }

    public boolean returnWithValue() {
        return this.returnWithValue;
    }

    public void setPtrCrossWorkGroupULong(SPIRVId ptrToCrossWorkGroupPrimitive) {
        this.ptrCrossWorkULong = ptrToCrossWorkGroupPrimitive;
    }

    public SPIRVId getPTrCrossWorkULong() {
        return this.ptrCrossWorkULong;
    }

    public Map<String, SPIRVId> getSPIRVSymbolTable() {
        return this.SPIRVSymbolTable;
    }

    public void putSymbol(String name, SPIRVId id) {
        SPIRVSymbolTable.put(name, id);
    }

    public void setPhiMap(Map<AllocatableValue, SPIRVId> phiMap) {
        this.phiMap = phiMap;
    }

    public ByteBuffer getSPIRVByteBuffer() {
        return this.spirvByteBuffer;
    }

    public void setSPIRVByteBuffer(ByteBuffer spirvByteBuffer) {
        this.spirvByteBuffer = spirvByteBuffer;
    }

    public void insertOpenCLImportId(SPIRVId oclImport) {
        this.openclImport = oclImport;
    }

    public SPIRVId getOpenclImport() {
        return this.openclImport;
    }

    public void intializeScopeStack() {
        this.currentBlockScopeStack = new Stack<>();
    }

    public SPIRVInstScope currentBlockScope() {
        return currentBlockScopeStack.peek();
    }

    public int scopeSize() {
        return currentBlockScopeStack.size();
    }

    public void pushScope(SPIRVInstScope scope) {
        currentBlockScopeStack.push(scope);
    }

    public SPIRVInstScope popScope() {
        return currentBlockScopeStack.pop();
    }

    public void emitAttribute(SPIRVCompilationResultBuilder crb) {
        throw new RuntimeException("[Not supported for SPIR-V] FPGA ATTRIBUTES - Check with the OpenCL Backend");
    }

    public SPIRVId createArrayDeclaration(SPIRVId elementTypeId, SPIRVId elementsId) {
        SPIRVId resultArrayId = module.getNextId();
        module.add(new SPIRVOpTypeArray(resultArrayId, elementTypeId, elementsId));
        return resultArrayId;
    }

    public SPIRVId declareArray(SPIRVKind elementType, SPIRVId elementTypeId, SPIRVId elementsId) {
        if (!arrayDeclarationTable.containsKey(elementType)) {
            SPIRVId resultArray = createArrayDeclaration(elementTypeId, elementsId);
            HashMap<SPIRVId, SPIRVId> value = new HashMap<>();
            value.put(elementsId, resultArray);
            arrayDeclarationTable.put(elementType, value);
            return resultArray;
        } else {
            HashMap<SPIRVId, SPIRVId> value = arrayDeclarationTable.get(elementType);
            if (value.containsKey(elementsId)) {
                return value.get(elementsId);
            } else {
                SPIRVId resultArray = createArrayDeclaration(elementTypeId, elementsId);
                value.put(elementsId, resultArray);
                arrayDeclarationTable.put(elementType, value);
                return resultArray;
            }
        }
    }

    public SPIRVId getFunctionPtrToPrivateArray(SPIRVId resultArrayId) {
        if (!functionPtrToArray.containsKey(resultArrayId)) {
            SPIRVId functionPTR = module.getNextId();
            module.add(new SPIRVOpTypePointer(functionPTR, SPIRVStorageClass.Function(), resultArrayId));
            functionPtrToArray.put(resultArrayId, functionPTR);
        }
        return functionPtrToArray.get(resultArrayId);
    }

    public SPIRVId getFunctionPtrToLocalArray(SPIRVId resultArrayId) {
        if (!functionPtrToArrayLocal.containsKey(resultArrayId)) {
            SPIRVId functionPTR = module.getNextId();
            module.add(new SPIRVOpTypePointer(functionPTR, SPIRVStorageClass.Workgroup(), resultArrayId));
            functionPtrToArrayLocal.put(resultArrayId, functionPTR);
        }
        return functionPtrToArrayLocal.get(resultArrayId);
    }

    /**
     * This method composes a unique name for all labels used. This is important
     * since all methods within the same compilation unit will be in the same file,
     * and SPIR-V requires different names.
     *
     * If we want to return the same names per module, just return the labelName.
     *
     * @param labelName
     *     String
     * @return a new label name.
     */
    public String composeUniqueLabelName(String labelName) {
        return labelName + "_kernel" + methodIndex;
    }

    public SPIRVId registerBlockLabel(String blockName) {
        blockName = composeUniqueLabelName(blockName);
        if (!labelTable.containsKey(blockName)) {
            SPIRVId label = module.getNextId();
            module.add(new SPIRVOpName(label, new SPIRVLiteralString(blockName)));
            labelTable.put(blockName, label);
        }
        return labelTable.get(blockName);
    }

    public void emitBlockLabelIfNotPresent(HIRBlock b, SPIRVInstScope functionScope) {
        String blockName = composeUniqueLabelName(b.toString());
        if (!labelTable.containsKey(blockName)) {
            SPIRVId label = module.getNextId();
            module.add(new SPIRVOpName(label, new SPIRVLiteralString(blockName)));
            labelTable.put(blockName, label);
        }
        SPIRVId label = labelTable.get(blockName);
        SPIRVInstScope block = functionScope.add(new SPIRVOpLabel(label));
        blockTable.put(blockName, block);
    }

    public SPIRVInstScope emitBlockLabel(String labelName, SPIRVInstScope functionScope) {
        labelName = composeUniqueLabelName(labelName);
        SPIRVId label = module.getNextId();
        module.add(new SPIRVOpName(label, new SPIRVLiteralString(labelName)));
        SPIRVInstScope block = functionScope.add(new SPIRVOpLabel(label));
        labelTable.put(labelName, label);
        blockTable.put(labelName, block);
        currentBlockScopeStack.push(block);
        return block;
    }

    private SPIRVId createNewOpTypeFunction(SPIRVId returnType, SPIRVId... operands) {
        functionSignature = module.getNextId();
        module.add(new SPIRVOpTypeFunction(functionSignature, returnType, new SPIRVMultipleOperands<>(operands)));
        return functionSignature;
    }

    private SPIRVId createNewFunctionAndUpdateTables(SPIRVId returnType, SPIRVId... operands) {
        int numParams = operands.length;
        SPIRVId functionId = createNewOpTypeFunction(returnType, operands);
        functionSignature = functionId;
        FunctionTable functionTable = new FunctionTable(returnType, functionId, operands);
        LinkedList<FunctionTable> list = new LinkedList<>();
        list.add(functionTable);
        HashMap<Integer, LinkedList<FunctionTable>> m = new HashMap<>();
        m.put(numParams, list);
        opFunctionTable.put(returnType, m);
        return functionId;
    }

    /**
     * To emit a {@link SPIRVOpTypeFunction}, the id has to be unique and the
     * function return type and the parameters have to be one per function
     * signature. This means that, if two functions have the same return type and
     * the same type of parameters and the parameter types are the same. we should
     * return the same ID previously registered for the first function declaration.
     *
     * This method checks whether we have a function type with the same return ID
     * and same parameters already registered. To do so, we manage a table
     * {@link SPIRVAssembler#opFunctionTable}, that stores the information as
     * follows:
     *
     * <code>
     * Map<%returnType, Map<NumParameters, LinkedList<SPIRVOpFunctionTable>>>
     * </code>
     *
     * If we have the same number of parameters with the same return type, when we
     * do a sequential search over the linked-list to check the type of each
     * parameter (stored in the {@link FunctionTable ) class).
     *
     * @param returnType
     *     ID with the return value.
     * @param operands
     *     List of IDs for the operads.
     * @return A {@link SPIRVId} for the {@link SPIRVOpFunction}
     */
    public SPIRVId emitOpTypeFunction(SPIRVId returnType, SPIRVId... operands) {
        if (!opFunctionTable.containsKey(returnType)) {
            createNewFunctionAndUpdateTables(returnType, operands);
        } else {
            // Search the type
            Map<Integer, LinkedList<FunctionTable>> internalMap = opFunctionTable.get(returnType);

            if (internalMap.containsKey(operands.length)) {
                // Sequential Check for all operands
                LinkedList<FunctionTable> opFunctionTableList = internalMap.get(operands.length);
                boolean isInCache = false;
                SPIRVId functionType = null;
                for (FunctionTable functionTable : opFunctionTableList) {
                    if (functionTable.areParamsEqual(operands)) {
                        isInCache = true;
                        functionType = functionTable.functionID;
                        break;
                    }
                }

                if (!isInCache) {
                    // Add a new Entry
                    functionType = createNewOpTypeFunction(returnType, operands);
                    opFunctionTableList.add(new FunctionTable(returnType, functionType, operands));

                    // Update function tables
                    internalMap.put(operands.length, opFunctionTableList);
                    opFunctionTable.put(returnType, internalMap);
                }
                functionSignature = functionType;
                return functionType;
            } else {
                // Create a new entry in the internal map
                functionSignature = createNewFunctionAndUpdateTables(returnType, operands);
            }
        }

        return functionSignature;
    }

    public void emitEntryPointMainKernel(StructuredGraph graph, String kernelName, boolean fp64Capability, boolean fp16Capability) {
        mainFunctionID = module.getNextId();

        SPIRVMultipleOperands operands;
        List<SPIRVId> builtInList = new ArrayList<>();
        if (graph.getNodes().filter(SPIRVThreadBuiltIn.GLOBAL_THREAD_ID.getNodeClass()).isNotEmpty()) {
            builtInList.add(builtinTable.get(SPIRVThreadBuiltIn.GLOBAL_THREAD_ID));
        }

        if (graph.getNodes().filter(SPIRVThreadBuiltIn.LOCAL_THREAD_ID.getNodeClass()).isNotEmpty() //
                || graph.getNodes().filter(SPIRVThreadBuiltIn.LOCAL_THREAD_ID.getOptionalNodeClass()).isNotEmpty()) {
            builtInList.add(builtinTable.get(SPIRVThreadBuiltIn.LOCAL_THREAD_ID));
        }

        if (graph.getNodes().filter(SPIRVThreadBuiltIn.WORKGROUP_SIZE.getNodeClass()).isNotEmpty() //
                || graph.getNodes().filter(SPIRVThreadBuiltIn.WORKGROUP_SIZE.getOptionalNodeClass()).isNotEmpty()) {
            builtInList.add(builtinTable.get(SPIRVThreadBuiltIn.WORKGROUP_SIZE));
        }

        if (graph.getNodes().filter(SPIRVThreadBuiltIn.GLOBAL_SIZE.getNodeClass()).isNotEmpty()) {
            builtInList.add(builtinTable.get(SPIRVThreadBuiltIn.GLOBAL_SIZE));
        }

        if (graph.getNodes().filter(SPIRVThreadBuiltIn.GROUP_ID.getNodeClass()).isNotEmpty()) {
            builtInList.add(builtinTable.get(SPIRVThreadBuiltIn.GROUP_ID));
        }

        if (builtInList.size() == 0) {
            operands = new SPIRVMultipleOperands();
        } else {
            SPIRVId[] array = new SPIRVId[builtInList.size()];
            builtInList.toArray(array);
            operands = new SPIRVMultipleOperands(array);
        }

        if (fp64Capability && fp16Capability) {
            module.add(new SPIRVOpExecutionMode(mainFunctionID, SPIRVExecutionMode.ContractionOff()));
        }

        module.add(new SPIRVOpEntryPoint(SPIRVExecutionModel.Kernel(), mainFunctionID, new SPIRVLiteralString(kernelName), operands));

    }

    public SPIRVId getFunctionSignature() {
        return functionSignature;
    }

    public SPIRVId getMainKernelId() {
        return mainFunctionID;
    }

    public SPIRVInstScope emitOpFunction(SPIRVId returnType, SPIRVId functionID, SPIRVId functionPredefinition, SPIRVFunctionControl functionControl) {
        if (functionID == null || functionPredefinition == null) {
            throw new RuntimeException("MainFunction or FunctionPre SPIR-V IDs are null. It can't generate correct SPIR-V code");
        }
        functionScope = module.add(new SPIRVOpFunction(returnType, functionID, functionControl, functionPredefinition));
        return functionScope;
    }

    public void emitParameterFunction(SPIRVId typeID, SPIRVId parameterId, SPIRVInstScope functionScope) {
        functionScope.add(new SPIRVOpFunctionParameter(typeID, parameterId));
    }

    public void closeFunction(SPIRVInstScope functionScope) {
        functionScope.add(new SPIRVOpFunctionEnd());
    }

    public void insertParameterId(int index, SPIRVId id) {
        parametersId.put(index, id);
    }

    public SPIRVId getParameterId(int parameterIndex) {
        return parametersId.get(parameterIndex);
    }

    public void registerLIRInstructionValue(Value valueLIRInstruction, SPIRVId spirvId) {
        lirTable.put(valueLIRInstruction, spirvId);
    }

    public void registerLIRInstructionValue(String valueLIRInstruction, SPIRVId spirvId) {
        lirTableName.put(valueLIRInstruction, spirvId);
    }

    public SPIRVId lookUpLIRInstructions(Value valueLIRInstruction) {
        return lirTable.get(valueLIRInstruction);
    }

    public SPIRVId lookUpLIRInstructionsName(String valueLIRInstruction) {
        return lirTableName.get(valueLIRInstruction);
    }

    public void clearLIRTable() {
        lirTable.clear();
        lirTableName.clear();
    }

    public SPIRVId emitConstantValue(SPIRVKind type, String valueConstant) {
        SPIRVId newConstantId = module.getNextId();
        SPIRVId typeID = primitives.getTypePrimitive(type);
        switch (type) {
            case OP_TYPE_INT_8, OP_TYPE_INT_16, OP_TYPE_INT_32 -> module.add(new SPIRVOpConstant(typeID, newConstantId, new SPIRVContextDependentInt(BigInteger.valueOf(Integer.parseInt(
                    valueConstant)))));
            case OP_TYPE_INT_64 -> module.add(new SPIRVOpConstant(typeID, newConstantId, new SPIRVContextDependentLong(BigInteger.valueOf(Integer.parseInt(valueConstant)))));
            case OP_TYPE_FLOAT_16 -> module.add(new SPIRVOpConstant(typeID, newConstantId, new SPIRVContextDependentHalfFloat(Float.floatToFloat16(Float.parseFloat(valueConstant)))));
            case OP_TYPE_FLOAT_32 -> module.add(new SPIRVOpConstant(typeID, newConstantId, new SPIRVContextDependentFloat(Float.parseFloat(valueConstant))));
            case OP_TYPE_FLOAT_64 -> module.add(new SPIRVOpConstant(typeID, newConstantId, new SPIRVContextDependentDouble(Double.parseDouble(valueConstant))));
            default -> throw new TornadoRuntimeException("Data type not supported yet: " + type);
        }
        return newConstantId;
    }

    public SPIRVId lookUpConstant(String valueConstant, SPIRVKind type) {
        ConstantKeyPair ckp = new ConstantKeyPair(valueConstant, type);
        if (constants.containsKey(ckp)) {
            return constants.get(ckp);
        } else {
            SPIRVId newConstantId = emitConstantValue(type, valueConstant);
            constants.put(ckp, newConstantId);
            return newConstantId;
        }
    }

    public Map<ConstantKeyPair, SPIRVId> getConstants() {
        return this.constants;
    }

    public void emit(String str) {
        emitSubString(str);
    }

    public void emitSubString(String str) {
        for (byte b : str.getBytes()) {
            emitByte(b);
        }
    }

    @Override
    public void align(int modulus) {

    }

    @Override
    public void halt() {

    }

    @Override
    public void jmp(Label l) {

    }

    @Override
    protected void patchJumpTarget(int branch, int jumpTarget) {

    }

    @Override
    public AbstractAddress makeAddress(int transferSize, Register base, int displacement) {
        return null;
    }

    @Override
    public AbstractAddress getPlaceholder(int instructionStartPosition) {
        return null;
    }

    @Override
    public void ensureUniquePC() {

    }

    public void emitValue(SPIRVCompilationResultBuilder crb, Value value) {
        if (crb.getAssembler().lookUpLIRInstructions(value) == null) {
            SPIRVId id = crb.getAssembler().lookUpLIRInstructionsName(value.toString());
            crb.getAssembler().registerLIRInstructionValue(value, id);
        }
    }

    public void emitValueOrOp(SPIRVCompilationResultBuilder crb, Value value) {
        if (value instanceof SPIRVLIROp spirvValue) {
            spirvValue.emit(crb, this);
        } else {
            emitValue(crb, value);
        }
    }

    public SPIRVId[] loadHeapPointerAndFrameIndex() {

        SPIRVId loadHeap = module.getNextId();
        SPIRVId frameIndexId = module.getNextId();

        SPIRVId heapId = SPIRVSymbolTable.get("heapBaseAddrId");
        SPIRVId frameId = SPIRVSymbolTable.get("frameBaseAddrId");

        SPIRVId ptrToUChar = primitives.getPtrToCrossWorkGroupPrimitive(SPIRVKind.OP_TYPE_INT_8);

        int alignment = 8;
        currentBlockScope().add(new SPIRVOpLoad( //
                ptrToUChar, //
                loadHeap, //
                heapId, //
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(alignment))) //
        ));

        SPIRVId ulong = primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
        currentBlockScope().add(new SPIRVOpLoad( //
                ulong, //
                frameIndexId, //
                frameId, //
                new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8))) //
        ));

        return new SPIRVId[] { loadHeap, frameIndexId };
    }

    public SPIRVId getMethodRegistrationId(String methodName) {
        return SPIRVSymbolTable.get(methodName);
    }

    public SPIRVId registerNewMethod(String methodName) {
        SPIRVId functionToCall = module.getNextId();
        module.add(new SPIRVOpName(functionToCall, new SPIRVLiteralString(methodName)));
        module.add(new SPIRVOpDecorate(functionToCall, SPIRVDecoration.LinkageAttributes(new SPIRVLiteralString(methodName), SPIRVLinkageType.Export())));
        SPIRVSymbolTable.put(methodName, functionToCall);
        return functionToCall;
    }

    public SPIRVId getLabel(String blockName) {
        blockName = composeUniqueLabelName(blockName);
        return labelTable.get(blockName);
    }

    public int getMethodIndex() {
        return methodIndex;
    }

    public void setMethodIndex(int methodIndex) {
        this.methodIndex = methodIndex;
    }

    public boolean isResultInPhiMap(Variable result) {
        return this.phiMap.containsKey(result);
    }

    public boolean isPhiMapEmpty() {
        return (this.phiMap == null);
    }

    public SPIRVId getPhiId(Variable result) {
        return this.phiMap.get(result);
    }

    // This is just for debugging
    public void printContentPhiTables() {
        System.out.println("PhiTable: " + phiMap);
        System.out.println("PhiTrace: " + phiTrace);
        System.out.println("phiNamesAcrossBlocks: " + phiNamesAcrossBlocks);
    }

    public void updatePendingIDs(AllocatableValue trace, SPIRVId newID) {
        this.pendingIDs.put(trace, newID);
    }

    public SPIRVId getPendingId(AllocatableValue trace) {
        return this.pendingIDs.get(trace);
    }

    public boolean isPendingIDAvailable(AllocatableValue trace) {
        return this.pendingIDs.containsKey(trace);
    }

    public AllocatableValue getPhiTraceValue(Variable result) {
        return this.phiTrace.get(result);
    }

    public void updatePhiMap(AllocatableValue key, SPIRVId newID) {
        this.phiMap.put(key, newID);
    }

    public void setPhiTrace(Map<AllocatableValue, AllocatableValue> phiTrace) {
        this.phiTrace = phiTrace;
    }

    public void setPhiValueId(AllocatableValue phiTraceValue, SPIRVId operationId) {
        this.phiMap.put(phiTraceValue, operationId);
    }

    public boolean isPhiTraceNull() {
        return this.phiTrace == null;
    }

    public void clearPhiTables() {
        if (phiTrace != null) {
            phiTrace.clear();
            phiTrace = null;
        }
        if (phiMap != null) {
            phiMap.clear();
            phiMap = null;
        }
        if (pendingIDs != null) {
            pendingIDs.clear();
            pendingIDs = null;
        }
    }

    public void registerPhiNameInstruction(AllocatableValue lhs, SPIRVId phiResultId) {
        phiNamesAcrossBlocks.put(lhs, phiResultId);
    }

    public boolean isPhiAcrossBlocksPresent(AllocatableValue lhs) {
        return phiNamesAcrossBlocks.containsKey(lhs);
    }

    public SPIRVId getPhiIdAcrossBlock(AllocatableValue lhs) {
        return phiNamesAcrossBlocks.get(lhs);
    }

    public void clearForwardPhiTable() {
        phiNamesAcrossBlocks.clear();
        phiNamesAcrossBlocks = new HashMap<>();
    }

    public SPIRVId emitDecorateOpenCLBuiltin(SPIRVModule module, SPIRVThreadBuiltIn builtIn) {
        SPIRVId idSPIRVBuiltin = module.getNextId();
        module.add(new SPIRVOpName(idSPIRVBuiltin, new SPIRVLiteralString(builtIn.getName())));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.BuiltIn(builtIn.getBuiltIn())));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.Constant()));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.LinkageAttributes(new SPIRVLiteralString(builtIn.getName()), SPIRVLinkageType.Import())));
        return idSPIRVBuiltin;
    }

    public Iterable<? extends Map.Entry<SPIRVThreadBuiltIn, SPIRVId>> getBuiltinTableEntrySet() {
        return builtinTable.entrySet();
    }

    public SPIRVId lookupParameterFromIndex(int parameterIndex) {
        return parameterIdList.get(parameterIndex);
    }

    public void addFunctionParameterId(SPIRVId id) {
        this.parameterIdList.add(id);
    }

    public static class ConstantKeyPair {
        private String name;
        private SPIRVKind kind;

        public ConstantKeyPair(String name, SPIRVKind kind) {
            this.name = name;
            this.kind = kind;
        }

        public String getName() {
            return name;
        }

        public SPIRVKind getKind() {
            return kind;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((kind == null) ? 0 : kind.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ConstantKeyPair) {
                ConstantKeyPair ckp = (ConstantKeyPair) obj;
                return (this.name.equals(ckp.name) && this.kind.equals(ckp.kind));
            }
            return false;
        }
    }

    private static class FunctionTable {

        SPIRVId[] params;
        private SPIRVId returnId;
        private SPIRVId functionID;

        public FunctionTable(SPIRVId returnId, SPIRVId functionID, SPIRVId... params) {
            this.returnId = returnId;
            this.functionID = functionID;
            this.params = params;
        }

        public boolean areParamsEqual(SPIRVId... params) {
            if (params == null) {
                return false;
            }

            if (params.length != this.params.length) {
                return false;
            }

            for (int i = 0; i < this.params.length; i++) {
                SPIRVId param = this.params[i];
                if (!param.equals(params[i++])) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Base class for SPIR-V opcodes.
     */
    public static class SPIRVOp {
        protected final String opcode;

        protected SPIRVOp(String opcode) {
            this.opcode = opcode;
        }

        protected final void emitOpcode(SPIRVAssembler asm) {
            asm.emit(opcode);
        }

        public boolean equals(SPIRVOp other) {
            return opcode.equals(other.opcode);
        }

        @Override
        public String toString() {
            return opcode;
        }
    }

    /**
     * Unary operations
     */
    public static class SPIRVUnaryOp extends SPIRVOp {

        private final boolean prefix;

        protected SPIRVUnaryOp(String opcode, boolean prefix) {
            super(opcode);
            this.prefix = prefix;
        }

        public void emit(SPIRVCompilationResultBuilder crb, Value x) {
            final SPIRVAssembler asm = crb.getAssembler();
            if (prefix) {
                emitOpcode(asm);
                asm.emitValueOrOp(crb, x);
            } else {
                asm.emitValueOrOp(crb, x);
                emitOpcode(asm);
            }
        }
    }

    /**
     * Binary operations
     */
    public abstract static class SPIRVBinaryOp extends SPIRVOp {

        // Integer arithmetic
        public static final SPIRVBinaryOp ADD_INTEGER = new SPIRVBinaryOpIAdd("+", "SPIRVOpIAdd");
        public static final SPIRVBinaryOp SUB_INTEGER = new SPIRVBinaryOpISub("-", "SPIRVOpISub");
        public static final SPIRVBinaryOp MULT_INTEGER = new SPIRVBinaryOpIMul("*", "SPIRVOpIMul");
        public static final SPIRVBinaryOp DIV_INTEGER = new SPIRVBinaryOpIDiv("/", "SPIRVOpSDiv");
        public static final SPIRVBinaryOp INTEGER_REM = new SPIRVBinaryOpSRem("MOD", "SPIRVOpSRem");
        public static final SPIRVBinaryOp INTEGER_LESS_THAN = new SPIRVBinaryOpSLessThan("<", "SPIRVOpSLessThan");
        public static final SPIRVBinaryOp INTEGER_EQUALS = new SPIRVBinaryOpIEqual("==", "SPIRVBinaryOpIEqual");
        public static final SPIRVBinaryOp INTEGER_BELOW = new SPIRVBinaryOpIBelow("<", "SPIRVOpSLessThan");

        // Float
        public static final SPIRVBinaryOp ADD_FLOAT = new SPIRVBinaryOpFAdd("+", "SPIRVOpFAdd");
        public static final SPIRVBinaryOp SUB_FLOAT = new SPIRVBinaryOpFSub("-", "SPIRVOpFSub");
        public static final SPIRVBinaryOp MULT_FLOAT = new SPIRVBinaryOpFMul("*", "SPIRVOpFMul");
        public static final SPIRVBinaryOp DIV_FLOAT = new SPIRVBinaryOpFDiv("/", "SPIRVOpFDiv");
        public static final SPIRVBinaryOp FLOAT_LESS_THAN = new SPIRVBinaryFORLessThan("<", "SPIRVBinaryFORLessThan");
        public static final SPIRVBinaryOp FLOAT_EQUALS = new SPIRVBinaryFOREquals("==", "SPIRVOpFOrdEqual");

        // Bitwise
        public static final SPIRVBinaryOp BITWISE_LEFT_SHIFT = new SPIRVBinaryOpLeftShift("<<", "SPIRVOpShiftLeftLogical");
        public static final SPIRVBinaryOp BITWISE_RIGHT_SHIFT = new SPIRVBinaryOpRightShift(">>", "SPIRVBinaryOpRightShift");
        public static final SPIRVBinaryOp BITWISE_UNSIGNED_RIGHT_SHIFT = new SPIRVBinaryOpUnsignedRightShift(">>", "SPIRVOpShiftRightArithmetic");
        public static final SPIRVBinaryOp BITWISE_AND = new SPIRVBinaryOpBitwiseAnd("&", "SPIRVBinaryOpBitwiseAnd");
        public static final SPIRVBinaryOp BITWISE_OR = new SPIRVBinaryOpBitwiseOr("&", "SPIRVBinaryOpBitwiseOr");
        public static final SPIRVBinaryOp BITWISE_XOR = new SPIRVBinaryOpBitwiseXor("&", "SPIRVBinaryOpBitwiseXor");

        protected String spirvInstruction;
        protected boolean checkSameTypes;
        protected boolean resultWidthCanChange;

        protected SPIRVBinaryOp(String opcode, String spirvInstruction) {
            super(opcode);
            this.spirvInstruction = spirvInstruction;
        }

        protected SPIRVBinaryOp(String opcode, String spirvInstruction, boolean checkSameTypes, boolean resultWidthCanChange) {
            super(opcode);
            this.spirvInstruction = spirvInstruction;
            this.checkSameTypes = checkSameTypes;
            this.resultWidthCanChange = resultWidthCanChange;
        }

        public boolean checkSameTypes() {
            return checkSameTypes;
        }

        public boolean resultWidthCanChange() {
            return resultWidthCanChange;
        }

        public String getOpcode() {
            return this.opcode;
        }

        /**
         * Instruction used for debugging
         *
         * @return String
         */
        public String getInstruction() {
            return spirvInstruction;
        }

        public abstract SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2);

    }

    public static class SPIRVBinaryOpIAdd extends SPIRVBinaryOp {

        protected SPIRVBinaryOpIAdd(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction, true, true);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpIAdd(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpFAdd extends SPIRVBinaryOp {

        protected SPIRVBinaryOpFAdd(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpFAdd(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpISub extends SPIRVBinaryOp {

        protected SPIRVBinaryOpISub(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpISub(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpFSub extends SPIRVBinaryOp {

        protected SPIRVBinaryOpFSub(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpFSub(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpIMul extends SPIRVBinaryOp {

        protected SPIRVBinaryOpIMul(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpIMul(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpFMul extends SPIRVBinaryOp {

        protected SPIRVBinaryOpFMul(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpFMul(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpIDiv extends SPIRVBinaryOp {

        protected SPIRVBinaryOpIDiv(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpSDiv(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpFDiv extends SPIRVBinaryOp {

        protected SPIRVBinaryOpFDiv(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpFDiv(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryFORLessThan extends SPIRVBinaryOp {

        protected SPIRVBinaryFORLessThan(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpFOrdLessThan(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryFOREquals extends SPIRVBinaryOp {

        protected SPIRVBinaryFOREquals(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpFOrdEqual(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpLeftShift extends SPIRVBinaryOp {

        protected SPIRVBinaryOpLeftShift(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpShiftLeftLogical(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpRightShift extends SPIRVBinaryOp {

        protected SPIRVBinaryOpRightShift(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpShiftRightLogical(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpUnsignedRightShift extends SPIRVBinaryOp {

        protected SPIRVBinaryOpUnsignedRightShift(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpShiftRightArithmetic(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpBitwiseAnd extends SPIRVBinaryOp {

        protected SPIRVBinaryOpBitwiseAnd(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpBitwiseAnd(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpBitwiseOr extends SPIRVBinaryOp {

        protected SPIRVBinaryOpBitwiseOr(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpBitwiseOr(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpBitwiseXor extends SPIRVBinaryOp {

        protected SPIRVBinaryOpBitwiseXor(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpBitwiseXor(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpSLessThan extends SPIRVBinaryOp {

        protected SPIRVBinaryOpSLessThan(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction, true, false);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpSLessThan(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpIEqual extends SPIRVBinaryOp {

        protected SPIRVBinaryOpIEqual(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpIEqual(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpIBelow extends SPIRVBinaryOp {

        protected SPIRVBinaryOpIBelow(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpSLessThan(idResultType, idResult, operand1, operand2);
        }
    }

    public static class SPIRVBinaryOpSRem extends SPIRVBinaryOp {

        protected SPIRVBinaryOpSRem(String opcode, String spirvInstruction) {
            super(opcode, spirvInstruction);
        }

        @Override
        public SPIRVInstruction generateInstruction(SPIRVId idResultType, SPIRVId idResult, SPIRVId operand1, SPIRVId operand2) {
            return new SPIRVOpSRem(idResultType, idResult, operand1, operand2);
        }
    }

}
