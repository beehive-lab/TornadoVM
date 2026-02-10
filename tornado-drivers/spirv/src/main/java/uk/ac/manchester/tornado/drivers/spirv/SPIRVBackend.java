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
package uk.ac.manchester.tornado.drivers.spirv;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.DataBuilder;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;
import jdk.graal.compiler.lir.framemap.ReferenceMapBuilder;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.SuitesProvider;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVHeader;
import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVInstScope;
import uk.ac.manchester.beehivespirvtoolkit.lib.SPIRVModule;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpCapability;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpConstant;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpDecorate;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpExtInstImport;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpMemoryModel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpName;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpReturn;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpSource;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.SPIRVOpVariable;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVAddressingModel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVCapability;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentDouble;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentFloat;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentInt;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVContextDependentLong;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVDecoration;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVFunctionControl;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralContextDependentNumber;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVMemoryModel;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVSourceLanguage;
import uk.ac.manchester.beehivespirvtoolkit.lib.instructions.operands.SPIRVStorageClass;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.exceptions.TornadoBailoutRuntimeException;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.common.utils.BackendDeopt;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.FPGAWorkGroupSizeNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.WriteHalfFloatNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVCodeProvider;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVFrameContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVFrameMap;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVFrameMapBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVProviders;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVSuitesProvider;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVUtils;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVDataBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerationResult;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator.ArrayVariable;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVNodeMatchRules;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVReferenceMapBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVKernelStackFrame;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.backend.XPUBackend;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class SPIRVBackend extends XPUBackend<SPIRVProviders> implements FrameMap.ReferenceMapBuilderFactory {

    private final OptionValues options;
    private final SPIRVTargetDescription targetDescription;
    private final SPIRVArchitecture spirvArchitecture;
    private final SPIRVDeviceContext deviceContext;
    private final SPIRVCodeProvider codeCache;
    private SPIRVDeviceContext context;
    private SPIRVId pointerToGlobalMemoryHeap;
    private SPIRVId pointerToULongFunction;
    private SPIRVInstScope blockScope;
    private boolean fp64CapabilityEnabled;

    private boolean fp16CapabilityEnabled;
    private boolean supportsFP64;
    private AtomicInteger methodIndex;

    public SPIRVBackend(OptionValues options, SPIRVProviders providers, SPIRVTargetDescription targetDescription, SPIRVCodeProvider codeProvider, SPIRVDeviceContext deviceContext) {
        super(providers);
        this.context = deviceContext;
        this.options = options;
        this.targetDescription = targetDescription;
        this.codeCache = codeProvider;
        this.deviceContext = deviceContext;
        this.spirvArchitecture = targetDescription.getArch();
        this.supportsFP64 = targetDescription.isSupportsFP64();
        this.methodIndex = new AtomicInteger(0);
    }

    @Override
    public String decodeDeopt(long value) {
        return BackendDeopt.decodeDeopt(value, getProviders());
    }

    @Override
    public boolean isInitialised() {
        return true;
    }

    // FIXME: <REFACTOR> Common between OCL and SPIRV

    /**
     * It allocated the extra buffers that are used by this backend.
     */
    @Override
    public void allocateTornadoVMBuffersOnDevice() {
        TornadoInternalError.shouldNotReachHere("Should not allocate extra buffers on the device.");
    }

    @Override
    public void init() {
    }

    @Override
    public int getMethodIndex() {
        return methodIndex.get();
    }

    public void incrementMethodIndex() {
        methodIndex.incrementAndGet();
    }

    @Override
    public SPIRVSuitesProvider getTornadoSuites() {
        return ((SPIRVProviders) getProviders()).getSuitesProvider();
    }

    @Override
    public SuitesProvider getSuites() {
        unimplemented("Get suites method in SPIRVBackend not implemented yet.");
        return null;
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo, Object stub) {
        return null;
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        unimplemented("Create compiled code method in SPIRVBackend not implemented yet.");
        return null;
    }

    // FIXME: <Revisit> This method returns an implemented inside the inner class.
    // Check if we can return null instead.
    @Override
    public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
        return new SPIRVReferenceMapBuilder();
    }

    @Override
    public SPIRVDeviceContext getDeviceContext() {
        return this.context;
    }

    public void reset(long executionPlanId) {
        getDeviceContext().reset(executionPlanId);
    }

    @Override
    public String toString() {
        return String.format("Backend: arch=%s, device=%s", spirvArchitecture.getName(), deviceContext.getDevice().getDeviceName());
    }

    @Override
    public SPIRVCodeProvider getCodeCache() {
        return codeCache;
    }

    private FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new SPIRVFrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new SPIRVFrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfig);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig) {
        return new SPIRVLIRGenerationResult(compilationId, lir, frameMapBuilder, registerAllocationConfig, new CallingConvention(0, null, new AllocatableValue[0]));
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        SPIRVLIRGenerationResult spirvlirGenerationResult = (SPIRVLIRGenerationResult) lirGenRes;
        return new SPIRVLIRGenerator(getProviders(), lirGenRes, spirvlirGenerationResult.getMethodIndex());
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new SPIRVNodeLIRBuilder(graph, lirGen, new SPIRVNodeMatchRules(lirGen));
    }

    public SPIRVCompilationResultBuilder newCompilationResultBuilder(FrameMap frameMap, SPIRVCompilationResult compilationResult, boolean isKernel, boolean isParallel, LIR lir) {

        SPIRVAssembler asm;
        if (compilationResult.getAssembler() == null) {
            asm = createAssembler();
        } else {
            asm = compilationResult.getAssembler();
        }
        SPIRVFrameContext frameContext = new SPIRVFrameContext();
        DataBuilder dataBuilder = new SPIRVDataBuilder();
        SPIRVCompilationResultBuilder crb = new SPIRVCompilationResultBuilder(getProviders(), frameMap, asm, dataBuilder, frameContext, options, getDebugContext(), compilationResult, lir);
        crb.setKernel(isKernel);
        crb.setParallel(isParallel);
        crb.setDeviceContext(deviceContext);
        return crb;
    }

    private SPIRVAssembler createAssembler() {
        return new SPIRVAssembler(targetDescription);
    }

    @Override
    public void emitCode(CompilationResultBuilder resultBuilder, LIR lir, ResolvedJavaMethod method, TornadoProfiler profiler) {

        // Enable Profiler for code generation
        SPIRVCompilationResultBuilder builder = (SPIRVCompilationResultBuilder) resultBuilder;
        TaskDataContext taskMetaData = builder.getTaskMetaData();
        profiler.start(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId());

        SPIRVCompilationResultBuilder crb = (SPIRVCompilationResultBuilder) resultBuilder;
        final SPIRVAssembler asm = (SPIRVAssembler) crb.asm;

        asm.setMethodIndex(methodIndex.get());

        if (crb.isKernel()) {
            // SPIR-V Header
            asm.module = new SPIRVModule( //
                    new SPIRVHeader( //
                            SPIRV_HEADER_VALUES.SPIRV_MAJOR_VERSION, //
                            SPIRV_HEADER_VALUES.SPIRV_MINOR_VERSION, //
                            SPIRV_HEADER_VALUES.SPIRV_GENERATOR_ID, //
                            SPIRV_HEADER_VALUES.SPIRV_INITIAL_BOUND, // The bound will be filled once the code-gen is finished
                            SPIRV_HEADER_VALUES.SPIRV_SCHEMA)); //

            // Instance the object for SPIR-V primitives handler
            asm.primitives = new SPIRVPrimitiveTypes(asm.module);
        }

        // 1. Emit SPIR-V preamble, variable declaration, decorators, types and
        // constants. Emit main function parameters and variables
        emitPrologue(crb, asm, method, lir, asm.module);

        // 2. Code emission. Visitor traversal for the whole LIR for SPIR-V
        crb.emit(lir);

        // 3. Close kernel
        emitEpilogue(asm);

        // 4. Clean-up
        cleanUp(asm);

        profiler.stop(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId());
        profiler.sum(ProfilerType.TOTAL_CODE_GENERATION_TIME, profiler.getTaskTimer(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId()));
    }

    private void cleanPhiTables(SPIRVAssembler asm) {
        asm.clearLIRTable();
        asm.clearPhiTables();
        asm.clearForwardPhiTable();
    }

    private void cleanUp(SPIRVAssembler asm) {
        asm.setReturnWithValue(false);
        asm.setReturnLabel(null);
        incrementMethodIndex();
        cleanPhiTables(asm);
    }

    private void emitFP64Capability(SPIRVModule module) {
        module.add(new SPIRVOpCapability(SPIRVCapability.Float64())); // To use doubles
        fp64CapabilityEnabled = true;
    }

    private void emitFP16Capability(SPIRVModule module) {
        module.add(new SPIRVOpCapability(SPIRVCapability.Float16Buffer())); // To use FP16
        fp16CapabilityEnabled = true;
    }

    private void emitSPIRVCapabilities(SPIRVModule module) {
        // Emit Capabilities
        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses())); // Uses physical addressing, non-logical addressing modes.
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage())); // Uses partially linked modules and libraries. (e.g., OpenCL)
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel())); // Uses the Kernel Execution Model.
    }

    private void emitImportOpenCL(SPIRVAssembler asm, SPIRVModule module) {
        // Add import OpenCL STD
        SPIRVId idImport = module.getNextId();
        module.add(new SPIRVOpExtInstImport(idImport, new SPIRVLiteralString("OpenCL.std")));
        asm.insertOpenCLImportId(idImport);
    }

    private void emitOpenCLAddressingMode(SPIRVModule module) {
        // Set the memory model to Physical64 with OpenCL
        module.add(new SPIRVOpMemoryModel(SPIRVAddressingModel.Physical64(), SPIRVMemoryModel.OpenCL()));
    }

    private void emitOpSourceForOpenCL(SPIRVModule module, int version) {
        module.add(new SPIRVOpSource( //
                SPIRVSourceLanguage.OpenCL_C(), //
                new SPIRVLiteralInteger(version), //
                new SPIRVOptionalOperand<>(), //
                new SPIRVOptionalOperand<>()));//
    }

    private SPIRVLiteralContextDependentNumber buildLiteralContextNumber(SPIRVKind kind, Constant value) {
        if (kind == SPIRVKind.OP_TYPE_FLOAT_16) {
            return new SPIRVContextDependentFloat(Float.parseFloat(value.toValueString()));
        } else if (kind == SPIRVKind.OP_TYPE_INT_32) {
            return new SPIRVContextDependentInt(BigInteger.valueOf(Integer.parseInt(value.toValueString())));
        } else if (kind == SPIRVKind.OP_TYPE_INT_64) {
            return new SPIRVContextDependentLong(BigInteger.valueOf(Long.parseLong(value.toValueString())));
        } else if (kind == SPIRVKind.OP_TYPE_FLOAT_32) {
            return new SPIRVContextDependentFloat(Float.parseFloat(value.toValueString()));
        } else if (kind == SPIRVKind.OP_TYPE_FLOAT_64) {
            return new SPIRVContextDependentDouble(Double.parseDouble(value.toValueString()));
        } else {
            throw new TornadoRuntimeException("SPIRV - SPIRVLiteralContextDependentNumber Type not supported");
        }
    }

    private void addVariableDef(Map<SPIRVKind, Set<Variable>> kindToVariable, Variable value) {
        if (value != null) {

            if (!(value.getPlatformKind() instanceof SPIRVKind)) {
                shouldNotReachHere();
            }

            SPIRVKind spirvKind = (SPIRVKind) value.getPlatformKind();
            if (spirvKind == SPIRVKind.ILLEGAL) {
                shouldNotReachHere();
            }

            if (!kindToVariable.containsKey(spirvKind)) {
                kindToVariable.put(spirvKind, new HashSet<>());
            }

            final Set<Variable> varList = kindToVariable.get(spirvKind);
            varList.add(value);
        }
    }

    private IDTable emitVariableDefs(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, LIR lir) {
        Map<SPIRVKind, Set<Variable>> kindToVariable = new HashMap<>();
        List<Tuple2<SPIRVId, SPIRVKind>> ids = new ArrayList<>();
        final int expectedVariables = lir.numVariables();
        final AtomicInteger variableCount = new AtomicInteger();

        ArrayList<AllocatableValue> resultArrays = new ArrayList<>();
        for (int block : lir.linearScanOrder()) {
            for (LIRInstruction lirInstruction : lir.getLIRforBlock(lir.getBlockById(block))) {

                lirInstruction.forEachOutput((instruction, value, mode, flags) -> {
                    if (value instanceof ArrayVariable variable) {
                        // All function variables, including arrays, must be defined a consecutive block
                        // of instructions from the block 0. We detect array declaration and define
                        // these as array for the SPIR-V Function StorageClass.
                        resultArrays.add(variable);
                    } else if (value instanceof Variable variable) {
                        if (variable.toString() != null) {
                            addVariableDef(kindToVariable, variable);
                            variableCount.incrementAndGet();
                        }
                    }
                    return value;
                });
            }
        }

        if (TornadoOptions.FULL_DEBUG) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "found %d variable, expected (%d)", variableCount.get(), expectedVariables);
        }

        int index = 0;
        for (SPIRVKind spirvKind : kindToVariable.keySet()) {
            if (TornadoOptions.FULL_DEBUG) {
                Logger.traceCodeGen(Logger.BACKEND.SPIRV, "VARIABLES -------------- ");
                Logger.traceCodeGen(Logger.BACKEND.SPIRV, "\tTYPE: " + spirvKind);
            }
            for (Variable var : kindToVariable.get(spirvKind)) {
                if (TornadoOptions.FULL_DEBUG) {
                    Logger.traceCodeGen(Logger.BACKEND.SPIRV, "\tNAME: " + var);
                }
                SPIRVId variable = asm.module.getNextId();
                asm.insertParameterId(index, variable);
                index++;
                if (!TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
                    asm.module.add(new SPIRVOpName(variable, new SPIRVLiteralString(var.toString())));
                    asm.module.add(new SPIRVOpDecorate(variable, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(spirvKind.getByteCount()))));
                    asm.registerLIRInstructionValue(var.toString(), variable);
                }
                ids.add(new Tuple2<>(variable, spirvKind));
            }
        }
        return new IDTable(ids, kindToVariable, resultArrays);
    }

    private void registerParallelIntrinsics(ControlFlowGraph cfg, SPIRVAssembler asm, SPIRVModule module) {

        Map<String, SPIRVId> SPIRVSymbolTable = asm.getSPIRVSymbolTable();

        // Register Thread ID
        if (cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.GLOBAL_THREAD_ID.getNodeClass()).isNotEmpty()) {
            SPIRVId id = asm.emitDecorateOpenCLBuiltin(module, SPIRVThreadBuiltIn.GLOBAL_THREAD_ID);
            SPIRVSymbolTable.put(SPIRVThreadBuiltIn.GLOBAL_THREAD_ID.name, id);
            asm.builtinTable.put(SPIRVThreadBuiltIn.GLOBAL_THREAD_ID, id);
        }

        // Register Global Size
        if (cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.GLOBAL_SIZE.getNodeClass()).isNotEmpty()) {
            SPIRVId id = asm.emitDecorateOpenCLBuiltin(module, SPIRVThreadBuiltIn.GLOBAL_SIZE);
            SPIRVSymbolTable.put(SPIRVThreadBuiltIn.GLOBAL_SIZE.name, id);
            asm.builtinTable.put(SPIRVThreadBuiltIn.GLOBAL_SIZE, id);
        }

        // Look for other builtins
        if (cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.LOCAL_THREAD_ID.getNodeClass()).isNotEmpty() || cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.LOCAL_THREAD_ID.getOptionalNodeClass())
                .isNotEmpty()) {
            SPIRVId id = asm.emitDecorateOpenCLBuiltin(module, SPIRVThreadBuiltIn.LOCAL_THREAD_ID);
            SPIRVSymbolTable.put(SPIRVThreadBuiltIn.LOCAL_THREAD_ID.name, id);
            asm.builtinTable.put(SPIRVThreadBuiltIn.LOCAL_THREAD_ID, id);
        }

        if (cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.GROUP_ID.getNodeClass()).isNotEmpty()) {
            SPIRVId id = asm.emitDecorateOpenCLBuiltin(module, SPIRVThreadBuiltIn.GROUP_ID);
            SPIRVSymbolTable.put(SPIRVThreadBuiltIn.GROUP_ID.name, id);
            asm.builtinTable.put(SPIRVThreadBuiltIn.GROUP_ID, id);
        }

        if (cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.WORKGROUP_SIZE.getNodeClass()).isNotEmpty() || cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.WORKGROUP_SIZE.getOptionalNodeClass())
                .isNotEmpty()) {
            SPIRVId id = asm.emitDecorateOpenCLBuiltin(module, SPIRVThreadBuiltIn.WORKGROUP_SIZE);
            SPIRVSymbolTable.put(SPIRVThreadBuiltIn.WORKGROUP_SIZE.name, id);
            asm.builtinTable.put(SPIRVThreadBuiltIn.WORKGROUP_SIZE, id);
        }
    }

    private void emitPrologueForMainKernel(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, ResolvedJavaMethod method, LIR lir, SPIRVId methodId, IDTable idTable) {

        final SPIRVId returnId = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_VOID);

        final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);
        ArrayList<LocalParameter> localParameters = new ArrayList<>();

        // Kernel context
        LocalParameter kernelContextParameter = new LocalParameter();
        kernelContextParameter.actualName = "__kernelContext";
        kernelContextParameter.typeId = asm.primitives.getPtrToCrossWorkGroupPrimitive(SPIRVKind.OP_TYPE_INT_64);
        kernelContextParameter.kind = SPIRVKind.OP_TYPE_INT_64;
        localParameters.add(kernelContextParameter);

        for (Local local : locals) {
            SPIRVKind spirvKind = SPIRVKind.OP_TYPE_INT_8;
            if (local.getType().toJavaName().equals(KernelContext.class.getName())) {
                spirvKind = SPIRVKind.OP_TYPE_INT_64;
            }

            // All Parameters Access Global Memory (PtrToCrossWorkGroup)
            SPIRVId kindId = asm.primitives.getPtrToCrossWorkGroupPrimitive(spirvKind);

            LocalParameter localParameter = new LocalParameter();
            localParameters.add(localParameter);

            localParameter.actualName = local.getName();
            localParameter.typeId = kindId;
            localParameter.kind = spirvKind;
        }
        SPIRVId[] typesOfLocalVars = new SPIRVId[localParameters.size()];
        Arrays.setAll(typesOfLocalVars, i -> localParameters.get(i).typeId);

        SPIRVId methodSignatureId = asm.emitOpTypeFunction(returnId, typesOfLocalVars);

        // --------------------------------------
        // Method Begins
        // --------------------------------------
        SPIRVInstScope functionScope = asm.emitOpFunction(returnId, methodId, methodSignatureId, SPIRVFunctionControl.DontInline());

        // --------------------------------------
        // Main kernel parameters
        // --------------------------------------
        List<Tuple2<SPIRVId, SPIRVKind>> ptrParameters = new ArrayList<>();
        List<SPIRVId> parameters = new ArrayList<>();
        for (int i = 0; i < localParameters.size(); i++) {
            LocalParameter localParameter = localParameters.get(i);
            SPIRVId id = asm.module.getNextId();
            String name = localParameter.actualName + "F" + asm.getMethodIndex();
            asm.module.add(new SPIRVOpName(id, new SPIRVLiteralString(name)));
            asm.module.add(new SPIRVOpDecorate(id, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(localParameter.kind.getSizeInBytes()))));
            asm.emitParameterFunction(localParameter.typeId, id, functionScope);

            // Global Ptr To Cross WorkGroup Parameters
            name += "_ptr";
            SPIRVId idPtr = asm.module.getNextId();
            asm.module.add(new SPIRVOpName(idPtr, new SPIRVLiteralString(name)));
            asm.module.add(new SPIRVOpDecorate(idPtr, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(SPIRVKind.OP_TYPE_INT_64.getSizeInBytes()))));
            ptrParameters.add(new Tuple2<>(idPtr, localParameter.kind));
            parameters.add(id);
            asm.registerLIRInstructionValue(name, idPtr);
            asm.addFunctionParameterId(idPtr);
            if (i == 0) {
                asm.setKernelContextId(idPtr);
            }
        }

        asm.setPtrCrossWorkGroupULong(asm.primitives.getPtrToCrossWorkGroupPrimitive(SPIRVKind.OP_TYPE_INT_64));

        // --------------------------------------
        // Label Entry
        // --------------------------------------
        blockScope = asm.emitBlockLabel("B0", functionScope);
        asm.setBlockZeroScope(blockScope);

        // --------------------------------------
        // All variable declaration
        // --------------------------------------
        if (!TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
            // Only emit variables for all names that are needed. For instance private
            // memory, local memory and constants in the case that the SPIR-V optimizer on.
            // If the SPIR-V optimizer is off, then we emit all variables registered through
            // Graal.
            for (Tuple2<SPIRVId, SPIRVKind> id : idTable.list) {
                SPIRVKind kind = id.second;
                // we need a pointer to kind
                SPIRVId resultType = asm.primitives.getPtrOpTypePointerWithStorage(kind, SPIRVStorageClass.Function());
                blockScope.add(new SPIRVOpVariable(resultType, id.first, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
            }
        }

        // We declare the arrays for local/private memory (if any)
        for (AllocatableValue value : idTable.resultArrays) {
            ArrayGen.emit(asm, value, value.getValueKind());
        }

        if (idTable.kindToVariable.containsKey(SPIRVKind.OP_TYPE_FLOAT_64)) {
            emitFP64Capability(asm.module);
        }
        if (idTable.kindToVariable.containsKey(SPIRVKind.OP_TYPE_FLOAT_16)) {
            emitFP16Capability(asm.module);
        }

        // Emit the Store between from the parameter value and the local variable
        // assigned
        for (Tuple2<SPIRVId, SPIRVKind> t2 : ptrParameters) {
            SPIRVId ptrToParam = t2.first;
            SPIRVId resultType = asm.primitives.getPtrFunctionToPtrCrossWorkGroup(t2.second);
            blockScope.add(new SPIRVOpVariable(resultType, ptrToParam, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        }
        for (int i = 0; i < ptrParameters.size(); i++) {
            blockScope.add(new SPIRVOpStore(ptrParameters.get(i).first, //
                    parameters.get(i), //
                    new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));
        }
    }

    private void emitPrologueForNonMainKernel(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, ResolvedJavaMethod method, LIR lir) {
        String methodName = SPIRVUtils.makeMethodName(method);

        // Find declaration ID for the new method
        SPIRVId methodId = asm.getMethodRegistrationId(methodName);

        if (methodId == null) {
            throw new RuntimeException("Method not registered");
        }

        // Register the function
        final ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(method.getDeclaringClass());
        SPIRVKind returnKind = SPIRVKind.fromResolvedJavaTypeToVectorKind(returnType);
        if (returnKind == SPIRVKind.ILLEGAL) {
            returnKind = SPIRVKind.fromJavaKind(method.getSignature().getReturnKind());
        }
        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "Return TYPE: " + returnKind);

        SPIRVId returnId = asm.primitives.getTypePrimitive(returnKind);

        final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);
        LocalParameter[] localParameters;

        int index = 0;
        if (TornadoOptions.SPIRV_DIRECT_CALL_WITH_LOAD_HEAP) {
            localParameters = new LocalParameter[locals.length + 2];
            SPIRVId ptrToUChar = asm.primitives.getPtrToCrossWorkGroupPrimitive(SPIRVKind.OP_TYPE_INT_8);
            SPIRVId ulong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
            localParameters[0] = new LocalParameter();
            localParameters[0].typeId = ptrToUChar;

            localParameters[1] = new LocalParameter();
            localParameters[1].typeId = ulong;
            index = 2;
        } else {
            localParameters = new LocalParameter[locals.length];
        }

        int j = 0;
        for (int i = index; i < locals.length; i++, j++) {
            Local l = locals[j];
            JavaKind type = l.getType().getJavaKind();
            SPIRVKind kind = SPIRVKind.fromJavaKindForMethodCalls(type);

            if (l.getType().getJavaKind() == JavaKind.Object) {
                // Check here if it is a vector Component Type
                String javaName = l.getType().toJavaName();
                if (javaName.startsWith(SPIRVKind.COLLECTION_PATH) || javaName.startsWith(SPIRVKind.VECTOR_COLLECTION_PATH)) {
                    String[] vectorTypeNames = javaName.split("\\.");
                    int nameIndex = vectorTypeNames.length - 1;
                    kind = SPIRVKind.getKindFromStringClassVector(vectorTypeNames[nameIndex]);
                }
            }

            SPIRVId kindId = asm.primitives.getTypePrimitive(kind);

            if (localParameters[i] == null) {
                localParameters[i] = new LocalParameter();
            }

            localParameters[i].actualName = locals[j].getName();
            localParameters[i].typeId = kindId;
        }
        SPIRVId[] typesOfLocalVars = new SPIRVId[localParameters.length];
        Arrays.setAll(typesOfLocalVars, i -> localParameters[i].typeId);

        SPIRVId methodSignatureId = asm.emitOpTypeFunction(returnId, typesOfLocalVars);

        // --------------------------------------
        // Method Begins
        // --------------------------------------
        SPIRVInstScope functionScope = asm.emitOpFunction(returnId, methodId, methodSignatureId, SPIRVFunctionControl.DontInline());

        // ----------------------------------
        // Emit all variables (types and initial values)
        // ----------------------------------
        IDTable idTable = emitVariableDefs(crb, asm, lir);

        // --------------------------------------
        // Main kernel parameters
        // --------------------------------------
        for (LocalParameter localParameter : localParameters) {
            SPIRVId id = asm.module.getNextId();
            String name = localParameter.actualName + "F" + asm.getMethodIndex();
            asm.module.add(new SPIRVOpName(id, new SPIRVLiteralString(name)));
            asm.emitParameterFunction(localParameter.typeId, id, functionScope);
            asm.registerLIRInstructionValue(name, id);
        }

        // --------------------------------------
        // Label Entry
        // --------------------------------------
        blockScope = asm.emitBlockLabel("B0", functionScope);
        asm.setBlockZeroScope(blockScope);

        // --------------------------------------
        // All variable declaration
        // --------------------------------------
        if (!TornadoOptions.OPTIMIZE_LOAD_STORE_SPIRV) {
            // Only emit variables for all names that are needed. For instance private
            // memory, local memory and constants in the case that the SPIR-V optimizer on.
            // If the SPIR-V optimizer is off, then we emit all variables registered through
            // Graal.
            for (Tuple2<SPIRVId, SPIRVKind> id : idTable.list) {
                SPIRVKind kind = id.second;
                // we need a pointer to kind
                SPIRVId resultType = asm.primitives.getPtrOpTypePointerWithStorage(kind, SPIRVStorageClass.Function());
                blockScope.add(new SPIRVOpVariable(resultType, id.first, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
            }
        }

        // We declare the arrays for local/private memory (if any)
        for (AllocatableValue value : idTable.resultArrays) {
            ArrayGen.emit(asm, value, value.getValueKind());
        }
    }

    /**
     * This method emits the global variables (Storage Class set to Input) for
     * OpenCL thread access, such as get_global_id, global_size, etc.
     * <p>
     * This is due to if the kernel is parallel, we need to declare a vector 3
     * elements (ThreadID-0, ThreadID-1, ThreadID-2) that will be used in the OCL
     * builtins for thread id and global sizes.
     * <p>
     * Example:
     *
     * <code>
     * %spirv_BuiltinGlobalSize = OpVariable %ptr_Input_v3long Input
     * </code>
     *
     * @param asm {@link SPIRVAssembler}
     */
    public void emitBuiltinVariables(SPIRVAssembler asm) {
        SPIRVId ptrV3ulong = asm.primitives.getPtrOpTypePointerWithStorage(SPIRVKind.OP_TYPE_VECTOR3_INT_64, SPIRVStorageClass.Input());
        for (Map.Entry<SPIRVThreadBuiltIn, SPIRVId> entry : asm.getBuiltinTableEntrySet()) {
            asm.module.add(new SPIRVOpVariable(ptrV3ulong, entry.getValue(), SPIRVStorageClass.Input(), new SPIRVOptionalOperand<>()));
        }
    }

    private void emitPrologueForMainKernelEntry(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, ResolvedJavaMethod method, LIR lir, SPIRVModule module) {
        final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();

        if (cfg.getStartBlock().getEndNode().predecessor() instanceof FPGAWorkGroupSizeNode) {
            throw new TornadoBailoutRuntimeException("FPGA Thread Attributes not supported yet.");
        }

        emitSPIRVCapabilities(module);
        emitImportOpenCL(asm, module);
        emitOpenCLAddressingMode(module);
        emitOpSourceForOpenCL(module, SPIRV_HEADER_VALUES.SPIRV_VERSION_FOR_OPENCL);

        registerParallelIntrinsics(cfg, asm, module);

        // ----------------------------------
        // Emit all variables (types and initial values)
        // ----------------------------------
        IDTable idTable = emitVariableDefs(crb, asm, lir);
        if (idTable.kindToVariable.containsKey(SPIRVKind.OP_TYPE_FLOAT_64)) {
            emitFP64Capability(module);
        }
        if (idTable.kindToVariable.containsKey(SPIRVKind.OP_TYPE_FLOAT_16)) {
            emitFP16Capability(module);
        }

        // ----------------------------------
        // Emit Entry Kernel
        // ----------------------------------
        if (fp64CapabilityEnabled && !supportsFP64) {
            throw new TornadoDeviceFP64NotSupported("Error - The current SPIR-V device does not support FP64");
        }
        asm.emitEntryPointMainKernel(cfg.graph, method.getName(), fp64CapabilityEnabled, fp16CapabilityEnabled);

        // Add all KINDS we generate the corresponding declaration
        for (SPIRVKind kind : idTable.kindToVariable.keySet()) {
            asm.primitives.getTypePrimitive(kind);
        }

        // ----------------------------------
        // Emit Constants
        // ----------------------------------
        // Stack used for declaring all constants
        Stack<TypeConstant> stack = new Stack<>();
        StructuredGraph graph = cfg.graph;
        for (ConstantNode constantNode : graph.getNodes().filter(ConstantNode.class)) {
            // Insert all constants
            JavaKind stackKind = constantNode.getStackKind();
            Constant value = constantNode.getValue();

            SPIRVKind kind;
            if (constantNode.usages().filter(WriteHalfFloatNode.class).isNotEmpty()) {
                kind = SPIRVKind.OP_TYPE_FLOAT_16;
            } else {
                kind = SPIRVKind.fromJavaKind(stackKind);
            }

            SPIRVId typeId;
            if (kind.isPrimitive()) {
                typeId = asm.primitives.getTypePrimitive(kind);
            } else {
                throw new TornadoRuntimeException("Type not supported");
            }

            SPIRVLiteralContextDependentNumber literalNumber = buildLiteralContextNumber(kind, value);
            stack.add(new TypeConstant(typeId, literalNumber, value.toValueString(), kind));
        }

        // Add constant 3 --> Frame Access
        int reservedSlots = SPIRVKernelStackFrame.RESERVED_SLOTS;
        asm.lookUpConstant(Integer.toString(reservedSlots), SPIRVKind.OP_TYPE_INT_32);

        // And the reminder of the constants
        while (!stack.isEmpty()) {
            TypeConstant t = stack.pop();
            SPIRVId idConstant = module.getNextId();
            module.add(new SPIRVOpConstant(t.typeID, idConstant, t.literalContextNumber));
            asm.getConstants().put(new SPIRVAssembler.ConstantKeyPair(t.valueString, t.kind), idConstant);
        }

        emitBuiltinVariables(asm);

        emitPrologueForMainKernel(crb, asm, method, lir, asm.getMainKernelId(), idTable);
    }

    private void emitPrologue(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, ResolvedJavaMethod method, LIR lir, SPIRVModule module) {
        asm.intializeScopeStack();
        String methodName = crb.compilationResult.getName();
        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "[SPIR-V CodeGen] Generating SPIR-V Preamble for method: %s", methodName);
        if (crb.isKernel()) {
            emitPrologueForMainKernelEntry(crb, asm, method, lir, module);
        } else {
            emitPrologueForNonMainKernel(crb, asm, method, lir);
        }
    }

    private void emitEpilogue(SPIRVAssembler asm) {
        if (TornadoOptions.SPIRV_RETURN_LABEL && !asm.returnWithValue() && asm.getReturnLabel() != null) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpReturn");
            SPIRVInstScope block = asm.getFunctionScope().add(new SPIRVOpLabel(asm.getReturnLabel()));
            block.add(new SPIRVOpReturn());
        }
        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "emit SPIRVOpFunctionEnd");
        asm.closeFunction(asm.getFunctionScope());
    }

    private static class SPIRV_HEADER_VALUES {
        public static final int SPIRV_VERSION_FOR_OPENCL = 300000;
        public static final int SPIRV_MAJOR_VERSION = 1;
        public static final int SPIRV_MINOR_VERSION = 2;
        public static final int SPIRV_GENERATOR_ID = 32;
        public static final int SPIRV_INITIAL_BOUND = 0;
        public static final int SPIRV_SCHEMA = 0;
    }

    private static class TypeConstant {
        public SPIRVId typeID;
        public SPIRVLiteralContextDependentNumber literalContextNumber;
        public String valueString;
        public SPIRVKind kind;

        public TypeConstant(SPIRVId typeID, SPIRVLiteralContextDependentNumber literal, String valueString, SPIRVKind kind) {
            this.typeID = typeID;
            this.literalContextNumber = literal;
            this.valueString = valueString;
            this.kind = kind;
        }
    }

    public static class Tuple2<T, K> {

        public T first;
        public K second;

        public Tuple2(T first, K second) {
            this.first = first;
            this.second = second;
        }
    }

    private static class IDTable {
        public List<Tuple2<SPIRVId, SPIRVKind>> list;
        public Map<SPIRVKind, Set<Variable>> kindToVariable;
        public ArrayList<AllocatableValue> resultArrays;

        public IDTable(List<Tuple2<SPIRVId, SPIRVKind>> list, Map<SPIRVKind, Set<Variable>> kindToVariable, ArrayList<AllocatableValue> resultArrays) {
            this.list = list;
            this.kindToVariable = kindToVariable;
            this.resultArrays = resultArrays;
        }
    }

    private static class ArrayGen {

        private static SPIRVId addSPIRVIdInPreamble(SPIRVAssembler asm, AllocatableValue resultArray) {
            SPIRVId idArrayVariable = asm.module.getNextId();
            asm.module.add(new SPIRVOpName(idArrayVariable, new SPIRVLiteralString(resultArray.toString())));
            SPIRVKind kind = (SPIRVKind) resultArray.getPlatformKind();
            asm.module.add(new SPIRVOpDecorate(idArrayVariable, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(kind.getSizeInBytes()))));
            asm.registerLIRInstructionValue(resultArray.toString(), idArrayVariable);
            return idArrayVariable;
        }

        public static void emit(SPIRVAssembler asm, AllocatableValue resultArray, ValueKind<?> lirKind) {
            SPIRVId idResult = addSPIRVIdInPreamble(asm, resultArray);
            Value length = ((ArrayVariable) resultArray).getLength();
            SPIRVId primitiveTypeId = asm.primitives.getTypePrimitive((SPIRVKind) lirKind.getPlatformKind());
            SPIRVId elementsId;
            if (length instanceof ConstantValue) {
                elementsId = asm.lookUpConstant(((ConstantValue) length).getConstant().toValueString(), SPIRVKind.OP_TYPE_INT_32);
            } else {
                throw new RuntimeException("Constant expected");
            }

            // Array declaration avoiding duplications
            SPIRVId resultArrayId = asm.declareArray((SPIRVKind) lirKind.getPlatformKind(), primitiveTypeId, elementsId);
            SPIRVId functionPTR = asm.getFunctionPtrToPrivateArray(resultArrayId);

            // Registration of the variable in the block 0 of the code
            asm.getBlockZeroScope().add(new SPIRVOpVariable(functionPTR, idResult, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        }
    }

    private static class LocalParameter {
        public String actualName;
        private SPIRVId typeId;
        private SPIRVKind kind;
    }

}
