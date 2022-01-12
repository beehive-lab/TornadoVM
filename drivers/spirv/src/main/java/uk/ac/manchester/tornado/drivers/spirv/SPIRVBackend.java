/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.SuitesProvider;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVHeader;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVInstScope;
import uk.ac.manchester.spirvbeehivetoolkit.lib.SPIRVModule;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpBitcast;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpCapability;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpConstant;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpDecorate;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpExtInstImport;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpMemoryModel;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpReturn;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpSource;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpTypePointer;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.SPIRVOpVariable;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVAddressingModel;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVCapability;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVContextDependentDouble;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVContextDependentFloat;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVContextDependentInt;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVContextDependentLong;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVDecoration;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVFunctionControl;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVFunctionParameterAttribute;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLinkageType;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralContextDependentNumber;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMemoryModel;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVSourceLanguage;
import uk.ac.manchester.spirvbeehivetoolkit.lib.instructions.operands.SPIRVStorageClass;
import uk.ac.manchester.tornado.api.exceptions.TornadoDeviceFP64NotSupported;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.opencl.OCLCodeCache;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.FPGAWorkGroupSizeNode;
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
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVIRGenerationResult;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator.ArrayVariable;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVNodeMatchRules;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVReferenceMapBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.mm.SPIRVCallStack;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.graal.backend.TornadoBackend;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVBackend extends TornadoBackend<SPIRVProviders> implements FrameMap.ReferenceMapBuilderFactory {

    private static class SPIRV_HEADER_VALUES {
        public static final int SPIRV_VERSION_FOR_OPENCL = 300000;
        public static final int SPIRV_MAJOR_VERSION = 1;
        public static final int SPIRV_MINOR_VERSION = 2;
        public static final int SPIRV_GENERATOR_ID = 32;
        public static final int SPIRV_INITIAL_BOUND = 0;
        public static final int SPIRV_SCHEMA = 0;
    }

    private SPIRVDeviceContext context;
    private boolean isInitialized;
    private final OptionValues options;
    private final SPIRVTargetDescription targetDescription;
    private final SPIRVArchitecture spirvArchitecture;
    private final SPIRVDeviceContext deviceContext;
    private final SPIRVCodeProvider codeCache;
    private final ScheduleMetaData scheduleMetaData;

    private SPIRVId pointerToGlobalMemoryHeap;
    private SPIRVId pointerToULongFunction;
    private SPIRVInstScope blockScope;
    private SPIRVId pointerToFrameAccess;
    private SPIRVId ptrFunctionPTRCrossWorkGroupUChar;
    private boolean fp64CapabilityEnabled;
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
        this.scheduleMetaData = new ScheduleMetaData("spirvBackend");
        this.supportsFP64 = targetDescription.isSupportsFP64();
        this.isInitialized = false;
        this.methodIndex = new AtomicInteger(0);
    }

    // FIXME <REFACTOR> <S>
    @Override
    public String decodeDeopt(long value) {
        DeoptimizationReason reason = getProviders().getMetaAccess().decodeDeoptReason(JavaConstant.forLong(value));
        DeoptimizationAction action = getProviders().getMetaAccess().decodeDeoptAction(JavaConstant.forLong(value));
        return String.format("deopt: reason=%s, action=%s", reason.toString(), action.toString());
    }

    @Override
    public boolean isInitialised() {
        return isInitialized;
    }

    // FIXME: <REFACTOR> Common between OCL and SPIRV

    /**
     * It allocates the smallest of the requested heap size or the max global memory
     * size.
     */
    public void allocateHeapMemoryOnDevice() {
        long memorySize = Math.min(DEFAULT_HEAP_ALLOCATION, context.getDevice().getDeviceMaxAllocationSize());
        if (memorySize < DEFAULT_HEAP_ALLOCATION) {
            Tornado.info("Unable to allocate %s of heap space - resized to %s", humanReadableByteCount(DEFAULT_HEAP_ALLOCATION, false), humanReadableByteCount(memorySize, false));
        }
        Tornado.info("%s: allocating %s of heap space", context.getDevice().getDeviceName(), humanReadableByteCount(memorySize, false));
        context.getMemoryManager().allocateDeviceMemoryRegions(memorySize);
    }

    @Override
    public void init() {
        if (!isInitialized) {
            Tornado.info("Initialization of the SPIRV Backend - Calling Memory Allocator");
            allocateHeapMemoryOnDevice();

            // Initialize deviceHeapPointer via the lookupBufferAddress
            TaskMetaData meta = new TaskMetaData(scheduleMetaData, OCLCodeCache.LOOKUP_BUFFER_KERNEL_NAME);
            runAndReadLookUpKernel(meta);

            isInitialized = true;
        }
    }

    @Override
    public int getMethodIndex() {
        return methodIndex.get();
    }

    public void incrementMethodIndex() {
        methodIndex.incrementAndGet();
    }

    public SPIRVSuitesProvider getTornadoSuites() {
        return ((SPIRVProviders) getProviders()).getSuitesProvider();
    }

    // FIXME <REFACTOR> Common method
    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        return new RegisterAllocationConfig(registerConfig, allocationRestrictedTo);
    }

    @Override
    public SuitesProvider getSuites() {
        unimplemented("Get suites method in SPIRVBackend not implemented yet.");
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

    public SPIRVDeviceContext getDeviceContext() {
        return context;
    }

    public void reset() {
        getDeviceContext().reset();
    }

    @Override
    public String toString() {
        return String.format("Backend: arch=%s, device=%s", spirvArchitecture.getName(), deviceContext.getDevice().getDeviceName());
    }

    @Override
    public SPIRVCodeProvider getCodeCache() {
        return codeCache;
    }

    private void runAndReadLookUpKernel(TaskMetaData meta) {
        long address = context.getMemoryManager().launchAndReadLookupBufferAddress(meta);
        deviceContext.getMemoryManager().init(this, address);
    }

    private FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new SPIRVFrameMap(getCodeCache(), registerConfig, this);
    }

    // FIXME <Refactor> common method
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new SPIRVFrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfig);
    }

    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig,
            StructuredGraph graph, Object stub) {
        return new SPIRVIRGenerationResult(compilationId, lir, frameMapBuilder, registerAllocationConfig, new CallingConvention(0, null, (AllocatableValue[]) null));
    }

    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes, final int methodIndex) {
        return new SPIRVLIRGenerator(getProviders(), lirGenRes, methodIndex);
    }

    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new SPIRVNodeLIRBuilder(graph, lirGen, new SPIRVNodeMatchRules(lirGen));
    }

    public SPIRVCompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGen, FrameMap frameMap, SPIRVCompilationResult compilationResult, CompilationResultBuilderFactory factory,
            boolean isKernel, boolean isParallel) {

        SPIRVAssembler asm;
        if (compilationResult.getAssembler() == null) {
            asm = createAssembler();
        } else {
            asm = compilationResult.getAssembler();
        }
        SPIRVFrameContext frameContext = new SPIRVFrameContext();
        DataBuilder dataBuilder = new SPIRVDataBuilder();
        SPIRVCompilationResultBuilder crb = new SPIRVCompilationResultBuilder(getProviders(), frameMap, asm, dataBuilder, frameContext, options, getDebugContext(), compilationResult);
        // SPIRVCompilationResultBuilder crb = new
        // SPIRVCompilationResultBuilder(codeCache, getForeignCalls(), frameMap, asm,
        // dataBuilder, frameContext, options, compilationResult);
        crb.setKernel(isKernel);
        crb.setParallel(isParallel);
        crb.setDeviceContext(deviceContext);
        return crb;
    }

    private SPIRVAssembler createAssembler() {
        return new SPIRVAssembler(targetDescription);
    }

    public void emitCode(SPIRVCompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method) {

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
        // constants.
        // 1.1 Emit main function parameters and variables
        // 1.2 Emit the logic for the stack frame-access within TornadoVM
        emitPrologue(crb, asm, method, lir, asm.module);

        // // 2. Code emission. Visitor traversal for the whole LIR for SPIR-V
        crb.emit(lir);

        // 3. Close kernel
        emitEpilogue(asm);

        // 4. Clean-up
        cleanUp(asm);
    }

    private void cleanUp(SPIRVAssembler asm) {
        // this.blockScope = null;
        asm.setReturnWithValue(false);
        asm.setReturnLabel(null);
        incrementMethodIndex();
        asm.clearLIRTable();
    }

    private static void writeBufferToFile(ByteBuffer buffer, String filepath) {
        buffer.flip();
        File out = new File(filepath);
        try {
            FileChannel channel = new FileOutputStream(out, false).getChannel();
            channel.write(buffer);
            channel.close();
        } catch (IOException e) {
            System.err.println("IO exception: " + e.getMessage());
        }
    }

    private void emitFP64Capability(SPIRVModule module) {
        module.add(new SPIRVOpCapability(SPIRVCapability.Float64())); // To use doubles
        fp64CapabilityEnabled = true;
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
        // @formatter:off
        module.add(new SPIRVOpSource(
                SPIRVSourceLanguage.OpenCL_C(),
                new SPIRVLiteralInteger(version),
                new SPIRVOptionalOperand<>(),
                new SPIRVOptionalOperand<>()));
        // @formatter:on
    }

    private SPIRVId emitDecorateOpenCLBuiltin(SPIRVModule module, SPIRVThreadBuiltIn builtIn) {
        SPIRVId idSPIRVBuiltin = module.getNextId();
        module.add(new SPIRVOpName(idSPIRVBuiltin, new SPIRVLiteralString(builtIn.name)));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.BuiltIn(builtIn.builtIn)));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.Constant()));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.LinkageAttributes(new SPIRVLiteralString(builtIn.name), SPIRVLinkageType.Import())));
        return idSPIRVBuiltin;
    }

    private static class TypeConstant {
        public SPIRVId typeID;
        public SPIRVLiteralContextDependentNumber n;
        public String valueString;
        public SPIRVKind kind;

        public TypeConstant(SPIRVId typeID, SPIRVLiteralContextDependentNumber n, String valueString, SPIRVKind kind) {
            this.typeID = typeID;
            this.n = n;
            this.valueString = valueString;
            this.kind = kind;
        }
    }

    private SPIRVLiteralContextDependentNumber buildLiteralContextNumber(SPIRVKind kind, Constant value) {
        if (kind == SPIRVKind.OP_TYPE_INT_32) {
            return new SPIRVContextDependentInt(BigInteger.valueOf(Integer.parseInt(value.toValueString())));
        } else if (kind == SPIRVKind.OP_TYPE_INT_64) {
            return new SPIRVContextDependentLong(BigInteger.valueOf(Long.parseLong(value.toValueString())));
        } else if (kind == SPIRVKind.OP_TYPE_FLOAT_32) {
            return new SPIRVContextDependentFloat(Float.parseFloat(value.toValueString()));
        } else if (kind == SPIRVKind.OP_TYPE_FLOAT_64) {
            return new SPIRVContextDependentDouble(Double.parseDouble(value.toValueString()));
        } else {
            throw new RuntimeException("SPIRV - SPIRVLiteralContextDependentNumber Type not supported");
        }
    }

    /**
     * This method emits the access to the stack-frame. The corresponding OpenCL
     * code is as follows:
     *
     * <code>
     * __global ulong *_frame = (__global ulong *) &_heap_base[_frame_base];
     * </code>
     *
     * @param module
     * @param heapBaseAddrId
     * @param frameBaseAddrId
     * @param frameId
     * @param heap_base
     * @param frame_base
     */
    private void emitLookUpBufferAccess(SPIRVModule module, SPIRVId heapBaseAddrId, SPIRVId frameBaseAddrId, SPIRVId frameId, SPIRVId heap_base, SPIRVId frame_base, SPIRVAssembler asm) {
        blockScope.add(new SPIRVOpStore(heapBaseAddrId, heap_base, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));
        blockScope.add(new SPIRVOpStore(frameBaseAddrId, frame_base, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id20 = module.getNextId();
        blockScope.add(new SPIRVOpLoad(pointerToGlobalMemoryHeap, id20, heapBaseAddrId, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId id21 = module.getNextId();
        blockScope.add(
                new SPIRVOpLoad(asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64), id21, frameBaseAddrId, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

        SPIRVId ptridx = module.getNextId();
        blockScope.add(new SPIRVOpInBoundsPtrAccessChain(pointerToGlobalMemoryHeap, ptridx, id20, id21, new SPIRVMultipleOperands<>()));

        SPIRVId id23 = module.getNextId();
        SPIRVId ptrToGlobalLong = asm.primitives.getPtrToCrossWorkGroupPrimitive(SPIRVKind.OP_TYPE_INT_64);
        // blockScope.add(new SPIRVOpBitcast(pointerToULongFunction, id23, ptridx));
        blockScope.add(new SPIRVOpBitcast(ptrToGlobalLong, id23, ptridx));

        blockScope.add(new SPIRVOpStore(frameId, id23, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));
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

    private IDTable emitVariableDefs(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, LIR lir) {
        Map<SPIRVKind, Set<Variable>> kindToVariable = new HashMap<>();
        List<Tuple2<SPIRVId, SPIRVKind>> ids = new ArrayList<>();
        final int expectedVariables = lir.numVariables();
        final AtomicInteger variableCount = new AtomicInteger();

        ArrayList<AllocatableValue> resultArrays = new ArrayList<>();
        for (AbstractBlockBase<?> b : lir.linearScanOrder()) {
            for (LIRInstruction lirInstruction : lir.getLIRforBlock(b)) {

                lirInstruction.forEachOutput((instruction, value, mode, flags) -> {
                    if (value instanceof ArrayVariable) {
                        // All function variables, including arrays, must be defined a consecutive block
                        // of instructions from the block 0. We detect array declaration and define
                        // these as array for the SPIR-V Function StorageClass.
                        ArrayVariable variable = (ArrayVariable) value;
                        resultArrays.add(variable);
                    } else if (value instanceof Variable) {
                        Variable variable = (Variable) value;
                        if (variable.getName() != null) {
                            addVariableDef(kindToVariable, variable);
                            variableCount.incrementAndGet();
                        }
                    }
                    return value;
                });
            }
        }

        if (Tornado.FULL_DEBUG) {
            Logger.traceCodeGen(Logger.BACKEND.SPIRV, "found %d variable, expected (%d)", variableCount.get(), expectedVariables);
        }

        int index = 0;
        for (SPIRVKind spirvKind : kindToVariable.keySet()) {
            if (Tornado.FULL_DEBUG) {
                Logger.traceCodeGen(Logger.BACKEND.SPIRV, "VARIABLES -------------- ");
                Logger.traceCodeGen(Logger.BACKEND.SPIRV, "\tTYPE: " + spirvKind);
            }
            for (Variable var : kindToVariable.get(spirvKind)) {
                if (Tornado.FULL_DEBUG) {
                    Logger.traceCodeGen(Logger.BACKEND.SPIRV, "\tNAME: " + var);
                }
                SPIRVId variable = asm.module.getNextId();
                asm.insertParameterId(index, variable);
                index++;
                asm.module.add(new SPIRVOpName(variable, new SPIRVLiteralString(var.toString())));
                asm.module.add(new SPIRVOpDecorate(variable, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(spirvKind.getByteCount()))));
                asm.registerLIRInstructionValue(var.toString(), variable);
                ids.add(new Tuple2<>(variable, spirvKind));
            }
        }
        return new IDTable(ids, kindToVariable, resultArrays);
    }

    private void registerParallelIntrinsics(ControlFlowGraph cfg, SPIRVAssembler asm, SPIRVModule module) {

        Map<String, SPIRVId> SPIRVSymbolTable = asm.getSPIRVSymbolTable();

        // Register Thread ID
        if (cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.GLOBAL_THREAD_ID.getNodeClass()).isNotEmpty()) {
            SPIRVId id = emitDecorateOpenCLBuiltin(module, SPIRVThreadBuiltIn.GLOBAL_THREAD_ID);
            SPIRVSymbolTable.put(SPIRVThreadBuiltIn.GLOBAL_THREAD_ID.name, id);
            asm.builtinTable.put(SPIRVThreadBuiltIn.GLOBAL_THREAD_ID, id);
        }

        // Register Global Size
        if (cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.GLOBAL_SIZE.getNodeClass()).isNotEmpty()) {
            SPIRVId id = emitDecorateOpenCLBuiltin(module, SPIRVThreadBuiltIn.GLOBAL_SIZE);
            SPIRVSymbolTable.put(SPIRVThreadBuiltIn.GLOBAL_SIZE.name, id);
            asm.builtinTable.put(SPIRVThreadBuiltIn.GLOBAL_SIZE, id);
        }

        // Look for other builtins
        if (cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.LOCAL_THREAD_ID.getNodeClass()).isNotEmpty()
                || cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.LOCAL_THREAD_ID.getOptionalNodeClass()).isNotEmpty()) {
            SPIRVId id = emitDecorateOpenCLBuiltin(module, SPIRVThreadBuiltIn.LOCAL_THREAD_ID);
            SPIRVSymbolTable.put(SPIRVThreadBuiltIn.LOCAL_THREAD_ID.name, id);
            asm.builtinTable.put(SPIRVThreadBuiltIn.LOCAL_THREAD_ID, id);
        }

        if (cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.GROUP_ID.getNodeClass()).isNotEmpty()) {
            SPIRVId id = emitDecorateOpenCLBuiltin(module, SPIRVThreadBuiltIn.GROUP_ID);
            SPIRVSymbolTable.put(SPIRVThreadBuiltIn.GROUP_ID.name, id);
            asm.builtinTable.put(SPIRVThreadBuiltIn.GROUP_ID, id);
        }

        if (cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.WORKGROUP_SIZE.getNodeClass()).isNotEmpty()
                || cfg.graph.getNodes().filter(SPIRVThreadBuiltIn.WORKGROUP_SIZE.getOptionalNodeClass()).isNotEmpty()) {
            SPIRVId id = emitDecorateOpenCLBuiltin(module, SPIRVThreadBuiltIn.WORKGROUP_SIZE);
            SPIRVSymbolTable.put(SPIRVThreadBuiltIn.WORKGROUP_SIZE.name, id);
            asm.builtinTable.put(SPIRVThreadBuiltIn.WORKGROUP_SIZE, id);
        }
    }

    private static class LocalParameter {
        public String actualName;
        private SPIRVId typeId;
    }

    private void emitPrologueForNonMainKernel(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, ResolvedJavaMethod method, LIR lir) {
        String methodName = SPIRVUtils.makeMethodName(method);

        // Find declaration ID for the new method
        SPIRVId methodId = asm.getMethodRegistrationId(methodName);

        if (methodId == null) {
            throw new RuntimeException("Method not registered");
        }

        // Register the function
        // final JavaKind returnKind = method.getSignature().getReturnKind();
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
                if (javaName.startsWith(SPIRVKind.VECTOR_COLLECTION_PATH)) {
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
        for (Tuple2<SPIRVId, SPIRVKind> id : idTable.list) {
            SPIRVKind kind = id.second;
            // we need a pointer to kind
            SPIRVId resultType = asm.primitives.getPtrOpTypePointerWithStorage(kind, SPIRVStorageClass.Function());
            blockScope.add(new SPIRVOpVariable(resultType, id.first, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        }
        // We declare the arrays for private memory (if any)
        for (AllocatableValue value : idTable.resultArrays) {
            ArrayGen.emit(asm, value, value.getValueKind());
        }
    }

    private void emitPrologueForMainKernelEntry(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, ResolvedJavaMethod method, LIR lir, SPIRVModule module) {
        final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();

        if (cfg.getStartBlock().getEndNode().predecessor().asNode() instanceof FPGAWorkGroupSizeNode) {
            FPGAWorkGroupSizeNode fpgaNode = (FPGAWorkGroupSizeNode) (cfg.getStartBlock().getEndNode().predecessor().asNode());
            String attribute = fpgaNode.createThreadAttribute();

            throw new RuntimeException("FPGA Thread Attributes not supported yet.");
            // asm.emitSymbol(attribute);
            // asm.emitLine("");
        }

        emitSPIRVCapabilities(module);
        emitImportOpenCL(asm, module);
        emitOpenCLAddressingMode(module);
        emitOpSourceForOpenCL(module, SPIRV_HEADER_VALUES.SPIRV_VERSION_FOR_OPENCL);

        boolean isParallel = crb.isParallel();
        // Generate this only if the kernel is parallel (it uses the get_global_id)
        if (isParallel) {
            registerParallelIntrinsics(cfg, asm, module);
        }

        // Decorate for heap_base
        SPIRVId heapBaseAddrId = module.getNextId();
        module.add(new SPIRVOpDecorate(heapBaseAddrId, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type
        asm.putSymbol("heapBaseAddrId", heapBaseAddrId);

        // Decorate for frameBaseAddrId
        SPIRVId frameBaseAddrId = module.getNextId();
        module.add(new SPIRVOpDecorate(frameBaseAddrId, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type
        asm.putSymbol("frameBaseAddrId", frameBaseAddrId);

        // Decorate for frameId
        SPIRVId frameId = module.getNextId();
        module.add(new SPIRVOpDecorate(frameId, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type
        asm.putSymbol("frameId", frameId);

        // ----------------------------------
        // Emit all variables (types and initial values)
        // ----------------------------------
        IDTable idTable = emitVariableDefs(crb, asm, lir);
        if (idTable.kindToVariable.containsKey(SPIRVKind.OP_TYPE_FLOAT_64)) {
            emitFP64Capability(module);
        }

        // ----------------------------------
        // Emit Entry Kernel
        // ----------------------------------
        if (fp64CapabilityEnabled && !supportsFP64) {
            throw new TornadoDeviceFP64NotSupported("Error - The current SPIR-V device does not support FP64");
        }
        asm.emitEntryPointMainKernel(cfg.graph, method.getName(), isParallel, fp64CapabilityEnabled);

        // ----------------------------------
        // OpNames for the heap and frame
        // ----------------------------------
        module.add(new SPIRVOpName(heapBaseAddrId, new SPIRVLiteralString("heapBaseAddr")));
        module.add(new SPIRVOpName(frameBaseAddrId, new SPIRVLiteralString("frameBaseAddr")));
        module.add(new SPIRVOpName(frameId, new SPIRVLiteralString("frame")));

        // We add the type for char
        asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_8);

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

            SPIRVKind kind = SPIRVKind.fromJavaKind(stackKind);
            SPIRVId typeId;
            if (kind.isPrimitive()) {
                typeId = asm.primitives.getTypePrimitive(kind);
            } else {
                throw new RuntimeException("Type not supported");
            }

            SPIRVLiteralContextDependentNumber literalNumber = buildLiteralContextNumber(kind, value);
            stack.add(new TypeConstant(typeId, literalNumber, value.toValueString(), kind));
        }

        // Add constant 3 --> Frame Access
        int reservedSlots = SPIRVCallStack.RESERVED_SLOTS;
        asm.lookUpConstant(Integer.toString(reservedSlots), SPIRVKind.OP_TYPE_INT_32);

        // And the reminder of the constants
        while (!stack.isEmpty()) {
            TypeConstant t = stack.pop();
            SPIRVId idConstant = module.getNextId();
            module.add(new SPIRVOpConstant(t.typeID, idConstant, t.n));
            asm.getConstants().put(new SPIRVAssembler.ConstantKeyPair(t.valueString, t.kind), idConstant);
        }

        // emit Type Void
        asm.primitives.emitTypeVoid();

        // Define Pointer to HEAP
        pointerToGlobalMemoryHeap = module.getNextId();
        SPIRVId uchar = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_8);
        module.add(new SPIRVOpTypePointer(pointerToGlobalMemoryHeap, SPIRVStorageClass.CrossWorkgroup(), uchar));

        // Declare the function
        SPIRVId ulong = asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64);
        asm.emitOpTypeFunction(asm.primitives.getTypeVoid(), pointerToGlobalMemoryHeap, ulong);

        ptrFunctionPTRCrossWorkGroupUChar = module.getNextId();
        module.add(new SPIRVOpTypePointer(ptrFunctionPTRCrossWorkGroupUChar, SPIRVStorageClass.Function(), pointerToGlobalMemoryHeap));

        asm.setPtrCrossWorkGroupULong(asm.primitives.getPtrToCrossWorkGroupPrimitive(SPIRVKind.OP_TYPE_INT_64));

        pointerToULongFunction = asm.primitives.getPtrToTypeFunctionPrimitive(SPIRVKind.OP_TYPE_INT_64);
        pointerToFrameAccess = module.getNextId();
        module.add(new SPIRVOpTypePointer(pointerToFrameAccess, SPIRVStorageClass.Function(), asm.getPTrCrossWorkULong()));

        if (isParallel) {
            // If the kernel is parallel, we need to declare a vector 3 (ThreadID-0,
            // ThreadID-1, ThreadID-2) that will be used in the OCL builtins for thread id
            // and global sizes.

            SPIRVId ptrV3ulong = asm.primitives.getPtrOpTypePointerWithStorage(SPIRVKind.OP_TYPE_VECTOR3_INT_64, SPIRVStorageClass.Input());

            for (Map.Entry<SPIRVThreadBuiltIn, SPIRVId> entry : asm.builtinTable.entrySet()) {
                asm.module.add(new SPIRVOpVariable(ptrV3ulong, entry.getValue(), SPIRVStorageClass.Input(), new SPIRVOptionalOperand<>()));
            }
        }

        // --------------------------------------
        // Main kernel Begins
        // --------------------------------------
        SPIRVInstScope functionScope = asm.emitOpFunction(asm.primitives.getTypeVoid(), asm.getMainKernelId(), asm.getFunctionSignature(), SPIRVFunctionControl.DontInline());

        // --------------------------------------
        // Main kernel parameters
        // --------------------------------------
        SPIRVId heap_base = module.getNextId();
        SPIRVId frame_base = module.getNextId();
        asm.emitParameterFunction(pointerToGlobalMemoryHeap, heap_base, functionScope);
        asm.emitParameterFunction(asm.primitives.getTypePrimitive(SPIRVKind.OP_TYPE_INT_64), frame_base, functionScope);

        // --------------------------------------
        // Label Entry
        // --------------------------------------
        blockScope = asm.emitBlockLabel("B0", functionScope);
        asm.setBlockZeroScope(blockScope);

        // --------------------------------------
        // All variable declaration + Lookup buffer access
        // --------------------------------------

        asm.module.add(new SPIRVOpDecorate(heapBaseAddrId, SPIRVDecoration.FuncParamAttr(SPIRVFunctionParameterAttribute.NoCapture())));
        asm.module.add(new SPIRVOpDecorate(heapBaseAddrId, SPIRVDecoration.FuncParamAttr(SPIRVFunctionParameterAttribute.NoWrite())));

        blockScope.add(new SPIRVOpVariable(ptrFunctionPTRCrossWorkGroupUChar, heapBaseAddrId, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        blockScope.add(new SPIRVOpVariable(pointerToULongFunction, frameBaseAddrId, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        for (Tuple2<SPIRVId, SPIRVKind> id : idTable.list) {
            SPIRVKind kind = id.second;
            // we need a pointer to kind
            SPIRVId resultType = asm.primitives.getPtrOpTypePointerWithStorage(kind, SPIRVStorageClass.Function());
            blockScope.add(new SPIRVOpVariable(resultType, id.first, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        }
        // We declare the arrays for private memory (if any)
        for (AllocatableValue value : idTable.resultArrays) {
            ArrayGen.emit(asm, value, value.getValueKind());
        }

        blockScope.add(new SPIRVOpVariable(pointerToFrameAccess, frameId, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        asm.setStackFrameId(frameId);

        // --------------------------------------
        // Emit Lookup buffer access
        // --------------------------------------
        emitLookUpBufferAccess(module, heapBaseAddrId, frameBaseAddrId, frameId, heap_base, frame_base, asm);
    }

    private void emitPrologue(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, ResolvedJavaMethod method, LIR lir, SPIRVModule module) {
        asm.intializeScopeStack();
        String methodName = crb.compilationResult.getName();
        Logger.traceCodeGen(Logger.BACKEND.SPIRV, "[SPIR-V CodeGen] Generating SPIRV-Header for method: %s", methodName);
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
}