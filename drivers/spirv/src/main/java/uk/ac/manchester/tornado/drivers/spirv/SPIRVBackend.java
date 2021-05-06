package uk.ac.manchester.tornado.drivers.spirv;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Stack;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.lir.LIR;
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
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.spirvproto.lib.InvalidSPIRVModuleException;
import uk.ac.manchester.spirvproto.lib.SPIRVHeader;
import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.SPIRVModule;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpBitcast;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpCapability;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpConstant;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpConvertUToPtr;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpDecorate;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpEntryPoint;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpExtInstImport;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunctionEnd;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunctionParameter;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpIAdd;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpInBoundsPtrAccessChain;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpMemoryModel;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpName;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpReturn;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpSource;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeFunction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypePointer;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeVector;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeVoid;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpVariable;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVAddressingModel;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVBuiltIn;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVCapability;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVContextDependentDouble;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVContextDependentFloat;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVContextDependentInt;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVContextDependentLong;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVDecoration;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVExecutionModel;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVFunctionControl;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLinkageType;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralContextDependentNumber;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryModel;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVSourceLanguage;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVStorageClass;
import uk.ac.manchester.tornado.drivers.opencl.OCLCodeCache;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.ThreadConfigurationNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVCodeProvider;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVFrameContext;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVFrameMap;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVFrameMapBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVInstalledCode;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVProviders;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVSuitesProvider;
import uk.ac.manchester.tornado.drivers.spirv.graal.asm.SPIRVAssembler;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResult;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVDataBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVIRGenerationResult;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVLIRGenerator;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVNodeMatchRules;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.SPIRVReferenceMapBuilder;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.runtime.common.Tornado;
import uk.ac.manchester.tornado.runtime.common.TornadoLogger;
import uk.ac.manchester.tornado.runtime.graal.backend.TornadoBackend;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskMetaData;

public class SPIRVBackend extends TornadoBackend<SPIRVProviders> implements FrameMap.ReferenceMapBuilderFactory {

    SPIRVDeviceContext context;
    private boolean isInitialized;
    private final OptionValues options;
    private final SPIRVTargetDescription targetDescription;
    private final SPIRVArchitecture spirvArchitecture;
    private final SPIRVDeviceContext deviceContext;
    private final SPIRVCodeProvider codeCache;
    SPIRVInstalledCode lookupCode;
    final ScheduleMetaData scheduleMetaData;

    final HashMap<String, SPIRVId> SPIRVSymbolTable;
    private SPIRVPrimitiveTypes primitives;
    final HashMap<String, SPIRVId> constants;

    public static final int SPIRV_VERSION = 100000;
    public static final int SPIRV_MAJOR_VERSION = 1;
    public static final int SPIRV_MINOR_VERSION = 2;
    public static final int SPIRV_GENERATOR_ID = 29;
    public static final int SPIRV_SCHEMA = 0;

    private SPIRVId pointerToGlobalMemoryHeap;
    private SPIRVId pointerToULongFunction;
    private SPIRVInstScope blockScope;
    private SPIRVId pointerToFrameAccess;
    private SPIRVId ptrFunctionPTRCrossWorkGroupUChar;

    public SPIRVBackend(OptionValues options, SPIRVProviders providers, SPIRVTargetDescription targetDescription, SPIRVCodeProvider codeProvider, SPIRVDeviceContext deviceContext) {
        super(providers);
        this.context = deviceContext;
        this.options = options;
        this.targetDescription = targetDescription;
        this.codeCache = codeProvider;
        this.deviceContext = deviceContext;
        spirvArchitecture = targetDescription.getArch();
        scheduleMetaData = new ScheduleMetaData("spirvBackend");
        this.isInitialized = false;
        this.SPIRVSymbolTable = new HashMap<>();
        this.constants = new HashMap<>();
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

    // FIXME: <Revisit> This method returns an inplemented inside the inner class.
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

    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        return new SPIRVLIRGenerator(getProviders(), lirGenRes);
    }

    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new SPIRVNodeLIRBuilder(graph, lirGen, new SPIRVNodeMatchRules(lirGen));
    }

    public SPIRVCompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGen, FrameMap frameMap, SPIRVCompilationResult compilationResult, CompilationResultBuilderFactory factory,
            boolean isKernel, boolean isParallel) {

        SPIRVAssembler asm = createAssembler();
        SPIRVFrameContext frameContext = new SPIRVFrameContext();
        DataBuilder dataBuilder = new SPIRVDataBuilder();
        SPIRVCompilationResultBuilder crb = new SPIRVCompilationResultBuilder(codeCache, getForeignCalls(), frameMap, asm, dataBuilder, frameContext, options, compilationResult);
        crb.setKernel(isKernel);
        crb.setParallel(isParallel);
        crb.setDeviceContext(deviceContext);
        return crb;
    }

    private SPIRVAssembler createAssembler() {
        return new SPIRVAssembler(targetDescription);
    }

    private void emitSPIRVCodeIntoASMModule(SPIRVAssembler asm, SPIRVModule module) {
        ByteBuffer out = ByteBuffer.allocate(module.getByteCount());
        out.order(ByteOrder.LITTLE_ENDIAN);

        // Close SPIR-V module without validation
        module.close().write(out);
        for (int i = 0; i < module.getByteCount(); i++) {
            asm.emitByte(out.get(i));
        }
    }

    public void emitCode(SPIRVCompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method) {

        final SPIRVAssembler asm = (SPIRVAssembler) crb.asm;

        // SPIR-V Header
        asm.module = new SPIRVModule( //
                new SPIRVHeader( //
                        SPIRV_MAJOR_VERSION, //
                        SPIRV_MINOR_VERSION, //
                        SPIRV_GENERATOR_ID, //
                        0, // The bound will be filled once the code-gen is finished
                        SPIRV_SCHEMA)); //

        primitives = new SPIRVPrimitiveTypes(asm.module);

        // TestLKBufferAccess.testAssignWithLookUpBuffer(asm.module);
        // dummySPIRVModuleTest(asm.module);

        emitPrologue(crb, asm, method, lir, asm.module);
        // crb.emit(lir);
        emitEpilogue(asm);

        emitSPIRVCodeIntoASMModule(asm, asm.module);
    }

    private void dummySPIRVModuleTest(SPIRVModule module) {
        SPIRVInstScope functionScope;
        SPIRVInstScope blockScope;

        emitSPIRVHeader(module, true);

        SPIRVId opTypeInt = module.getNextId();
        module.add(new SPIRVOpTypeInt(opTypeInt, new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)));

        SPIRVId opTypeVoid = module.getNextId();
        module.add(new SPIRVOpTypeVoid(opTypeVoid));

        SPIRVId intPointer = module.getNextId();
        module.add(new SPIRVOpTypePointer(intPointer, SPIRVStorageClass.CrossWorkgroup(), opTypeInt));

        SPIRVId functionType = module.getNextId();
        module.add(new SPIRVOpTypeFunction(functionType, opTypeVoid, new SPIRVMultipleOperands<>(intPointer)));

        SPIRVId vector = module.getNextId();
        module.add(new SPIRVOpTypeVector(vector, opTypeInt, new SPIRVLiteralInteger(3)));
        SPIRVId pointer = module.getNextId();
        module.add(new SPIRVOpTypePointer(pointer, SPIRVStorageClass.Input(), vector));
        SPIRVId input = module.getNextId();
        module.add(new SPIRVOpVariable(pointer, input, SPIRVStorageClass.Input(), new SPIRVOptionalOperand<>()));

        SPIRVId functionDef = module.getNextId();
        functionScope = module.add(new SPIRVOpFunction(opTypeVoid, functionDef, SPIRVFunctionControl.DontInline(), functionType));
        SPIRVId defParam1 = module.getNextId();
        functionScope.add(new SPIRVOpFunctionParameter(pointer, defParam1));

        module.add(new SPIRVOpEntryPoint(SPIRVExecutionModel.Kernel(), functionDef, new SPIRVLiteralString("copyTestZero_int256"), new SPIRVMultipleOperands<>()));

        blockScope = functionScope.add(new SPIRVOpLabel(module.getNextId()));
        SPIRVId var1 = module.getNextId();
        blockScope.add(new SPIRVOpVariable(pointer, var1, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
        SPIRVId var4 = module.getNextId();
        blockScope.add(new SPIRVOpVariable(intPointer, var4, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));

        SPIRVId load = module.getNextId();
        blockScope.add(new SPIRVOpLoad(vector, load, input, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(16)))));

        SPIRVId add = module.getNextId();
        blockScope.add(new SPIRVOpIAdd(opTypeInt, add, var4, load));
        blockScope.add(new SPIRVOpStore(var1, add, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));

        blockScope.add(new SPIRVOpReturn());
        functionScope.add(new SPIRVOpFunctionEnd());
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

    public static void writeModuleToFile(SPIRVModule module, String filepath) throws InvalidSPIRVModuleException {
        ByteBuffer out = ByteBuffer.allocate(module.getByteCount());
        out.order(ByteOrder.LITTLE_ENDIAN);
        module.validate().write(out);
        writeBufferToFile(out, filepath);
    }

    private void emitSPIRVCapabilities(SPIRVModule module) {
        // Emit Capabilities
        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses())); // Uses physical addressing, non-logical addressing modes.
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage())); // Uses partially linked modules and libraries. (e.g., OpenCL)
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel())); // Uses the Kernel Execution Model.
        module.add(new SPIRVOpCapability(SPIRVCapability.Int64())); // Uses OpTypeInt to declare 64-bit integer types
        module.add(new SPIRVOpCapability(SPIRVCapability.Int8()));
    }

    private void emitImportOpenCL(SPIRVModule module) {
        // Add import OpenCL STD
        SPIRVId idImport = module.getNextId();
        module.add(new SPIRVOpExtInstImport(idImport, new SPIRVLiteralString("OpenCL.std")));
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

    private SPIRVId emitDecorateOpenCLBuiltin(SPIRVModule module) {
        SPIRVId idSPIRVBuiltin = module.getNextId();
        // Add Decorators for the GetGlobalID intrinsics
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.BuiltIn(SPIRVBuiltIn.GlobalInvocationId())));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.Constant()));
        module.add(new SPIRVOpDecorate(idSPIRVBuiltin, SPIRVDecoration.LinkageAttributes(new SPIRVLiteralString("spirv_BuiltInGlobalInvocationId"), SPIRVLinkageType.Import())));
        return idSPIRVBuiltin;
    }

    private void emitSPIRVHeader(SPIRVModule module, boolean isParallel) {

        emitSPIRVCapabilities(module);
        emitImportOpenCL(module);
        emitOpenCLAddressingMode(module);
        emitOpSourceForOpenCL(module, SPIRV_VERSION);

        // Generate this only if the kernel is parallel (it uses the get_global_id)
        if (isParallel) {
            SPIRVId idSPIRVBuiltin = emitDecorateOpenCLBuiltin(module);
            SPIRVSymbolTable.put("idSPIRVBuiltin", idSPIRVBuiltin);
        }

        // Decorate for heap_base
        SPIRVId heapBaseAddrId = module.getNextId();
        module.add(new SPIRVOpDecorate(heapBaseAddrId, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type
        SPIRVSymbolTable.put("heapBaseAddrId", heapBaseAddrId);

        // Decorate for frameBaseAddrId
        SPIRVId frameBaseAddrId = module.getNextId();
        module.add(new SPIRVOpDecorate(frameBaseAddrId, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type
        SPIRVSymbolTable.put("frameBaseAddrId", frameBaseAddrId);

        // Decorate for frameId
        SPIRVId frameId = module.getNextId();
        module.add(new SPIRVOpDecorate(frameId, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type
        SPIRVSymbolTable.put("frameId", frameId);

        module.add(new SPIRVOpName(heapBaseAddrId, new SPIRVLiteralString("heapBaseAddr")));
        module.add(new SPIRVOpName(frameBaseAddrId, new SPIRVLiteralString("frameBaseAddr")));
        module.add(new SPIRVOpName(frameId, new SPIRVLiteralString("frame")));

        // ------------------------------------------------------------------------------------------------------------
        // For each I/O, there is a decorate with alignment 8 (it is a pointer to the
        // data)
        // TODO

        // ------------------------------------------------------------------------------------------------------------
        // EMIT TYPES
        // Type Int
        SPIRVId ulong = module.getNextId();
        SPIRVId uint32 = module.getNextId();
        SPIRVId uint8 = module.getNextId();
        module.add(new SPIRVOpTypeInt(ulong, new SPIRVLiteralInteger(64), new SPIRVLiteralInteger(0)));
        module.add(new SPIRVOpTypeInt(uint32, new SPIRVLiteralInteger(32), new SPIRVLiteralInteger(0)));
        module.add(new SPIRVOpTypeInt(uint8, new SPIRVLiteralInteger(8), new SPIRVLiteralInteger(0)));

        // Index for the stack-frame
        SPIRVId ulongConstant3 = module.getNextId();
        module.add(new SPIRVOpConstant(ulong, ulongConstant3, new SPIRVContextDependentLong(BigInteger.valueOf(3))));

        // Header value to skip
        SPIRVId ulongConstant24 = module.getNextId();
        module.add(new SPIRVOpConstant(ulong, ulongConstant24, new SPIRVContextDependentLong(BigInteger.valueOf(24))));

        // This ID is dependent of one application
        SPIRVId ulongConstant50 = module.getNextId();
        module.add(new SPIRVOpConstant(ulong, ulongConstant50, new SPIRVContextDependentLong(BigInteger.valueOf(50))));

        // OpVoid
        SPIRVId voidType = module.getNextId();
        module.add(new SPIRVOpTypeVoid(voidType));
        // ------------------------------------------------------------------------------------------------------------
    }

    private static class TypeConstant {
        public SPIRVId typeID;
        public SPIRVLiteralContextDependentNumber n;
        public String valueString;

        public TypeConstant(SPIRVId typeID, SPIRVLiteralContextDependentNumber n, String valueString) {
            this.typeID = typeID;
            this.n = n;
            this.valueString = valueString;
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

    private void emitPrologue(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, ResolvedJavaMethod method, LIR lir, SPIRVModule module) {
        String methodName = crb.compilationResult.getName();
        TornadoLogger.trace("[SPIR-V CodeGen] Generating code for method: %s \n", methodName);
        boolean isParallel = crb.isParallel();
        if (crb.isKernel()) {
            final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();
            if (cfg.getStartBlock().getEndNode().predecessor().asNode() instanceof ThreadConfigurationNode) {
                asm.emitAttribute(crb); // value
            }
            // Emit SPIR-V Header
            // emitSPIRVHeader(module, isParallel);

            emitSPIRVCapabilities(module);
            emitImportOpenCL(module);
            emitOpenCLAddressingMode(module);
            emitOpSourceForOpenCL(module, 100000);

            asm.emitEntryPointMainKernel(method.getName());

            // Generate this only if the kernel is parallel (it uses the get_global_id)
            if (isParallel) {
                SPIRVId idSPIRVBuiltin = emitDecorateOpenCLBuiltin(module);
                SPIRVSymbolTable.put("idSPIRVBuiltin", idSPIRVBuiltin);
            }

            // Decorate for heap_base
            SPIRVId heapBaseAddrId = module.getNextId();
            module.add(new SPIRVOpDecorate(heapBaseAddrId, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type
            SPIRVSymbolTable.put("heapBaseAddrId", heapBaseAddrId);

            // Decorate for frameBaseAddrId
            SPIRVId frameBaseAddrId = module.getNextId();
            module.add(new SPIRVOpDecorate(frameBaseAddrId, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type
            SPIRVSymbolTable.put("frameBaseAddrId", frameBaseAddrId);

            // Decorate for frameId
            SPIRVId frameId = module.getNextId();
            module.add(new SPIRVOpDecorate(frameId, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type
            SPIRVSymbolTable.put("frameId", frameId);

            SPIRVId ul0 = module.getNextId();
            module.add(new SPIRVOpDecorate(ul0, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type
            SPIRVId ul1 = module.getNextId();
            module.add(new SPIRVOpDecorate(ul1, SPIRVDecoration.Alignment(new SPIRVLiteralInteger(8)))); // Long Type

            module.add(new SPIRVOpName(heapBaseAddrId, new SPIRVLiteralString("heapBaseAddr")));
            module.add(new SPIRVOpName(frameBaseAddrId, new SPIRVLiteralString("frameBaseAddr")));
            module.add(new SPIRVOpName(frameId, new SPIRVLiteralString("frame")));

            Stack<TypeConstant> stack = new Stack<>();

            // For each I/O, there is a decorate with alignment 8 (it is a pointer to the
            // data)
            // How many variables?
            final int expectedVariables = lir.numVariables();
            System.out.println("Expected Variable: " + expectedVariables);

            // We add the type for char
            primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_8);

            // Emit Constants
            StructuredGraph graph = cfg.graph;
            for (ConstantNode constantNode : graph.getNodes().filter(ConstantNode.class)) {
                // Insert all constants
                JavaKind stackKind = constantNode.getStackKind();
                Constant value = constantNode.getValue();

                SPIRVKind kind = SPIRVKind.fromJavaKind(stackKind);
                SPIRVId typeId;
                if (kind.isInteger()) {
                    typeId = primitives.getTypeInt(kind);
                } else {
                    throw new RuntimeException("Type not supported");
                }

                SPIRVLiteralContextDependentNumber literalNumber = buildLiteralContextNumber(kind, value);
                stack.add(new TypeConstant(typeId, literalNumber, value.toValueString()));

            }

            // Add constant 3 --> Frame Access
            SPIRVId idConstant3 = module.getNextId();
            module.add(new SPIRVOpConstant(primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_64), idConstant3, new SPIRVContextDependentLong(BigInteger.valueOf(3))));
            constants.put("3", idConstant3);

            // And the reminder of the constants
            while (!stack.isEmpty()) {
                TypeConstant t = stack.pop();
                SPIRVId idConstant = module.getNextId();
                module.add(new SPIRVOpConstant(t.typeID, idConstant, t.n));
                constants.put(t.valueString, idConstant);
            }

            // emit Type Void
            primitives.emitTypeVoid();

            // Define Pointer to HEAP
            pointerToGlobalMemoryHeap = module.getNextId();
            SPIRVId uchar = primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_8);
            module.add(new SPIRVOpTypePointer(pointerToGlobalMemoryHeap, SPIRVStorageClass.CrossWorkgroup(), uchar));

            // Declare the function
            SPIRVId ulong = primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_64);
            asm.emitOpMainFunction(primitives.getTypeVoid(), pointerToGlobalMemoryHeap, ulong);

            ptrFunctionPTRCrossWorkGroupUChar = module.getNextId();
            module.add(new SPIRVOpTypePointer(ptrFunctionPTRCrossWorkGroupUChar, SPIRVStorageClass.Function(), pointerToGlobalMemoryHeap));

            pointerToULongFunction = module.getNextId();
            module.add(new SPIRVOpTypePointer(pointerToULongFunction, SPIRVStorageClass.Function(), primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_64)));

            pointerToFrameAccess = module.getNextId();
            module.add(new SPIRVOpTypePointer(pointerToFrameAccess, SPIRVStorageClass.Function(), pointerToULongFunction));

            // FIXME: Here we will have a ptr to each type of the kernel - Since this kernel
            // stores in Int*, we need a Pointer to INT in CROSS-Work-GROUP
            SPIRVId ptrCrossWorkGroupUInt = module.getNextId();
            module.add(new SPIRVOpTypePointer(ptrCrossWorkGroupUInt, SPIRVStorageClass.CrossWorkgroup(), primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_32)));

            // Main kernel Begins
            SPIRVInstScope functionScope = asm.emitOpFunction(primitives.getTypeVoid(), asm.getMainKernelId(), asm.getFunctionPredefinition());
            //
            // Main kernel parameters
            SPIRVId heap_base = module.getNextId();
            asm.emitParameterFunction(pointerToGlobalMemoryHeap, heap_base, functionScope);

            SPIRVId frame_base = module.getNextId();
            asm.emitParameterFunction(primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_64), frame_base, functionScope);

            // Label Entry
            blockScope = asm.emitBlockLabel("Entry", functionScope);

            // All variable declaration + Lookup buffer access
            {
                blockScope.add(new SPIRVOpVariable(ptrFunctionPTRCrossWorkGroupUChar, heapBaseAddrId, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
                blockScope.add(new SPIRVOpVariable(pointerToULongFunction, frameBaseAddrId, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
                blockScope.add(new SPIRVOpVariable(pointerToULongFunction, ul0, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
                blockScope.add(new SPIRVOpVariable(pointerToULongFunction, ul1, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));
                blockScope.add(new SPIRVOpVariable(pointerToFrameAccess, frameId, SPIRVStorageClass.Function(), new SPIRVOptionalOperand<>()));

                blockScope.add(new SPIRVOpStore(heapBaseAddrId, heap_base, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));
                blockScope.add(new SPIRVOpStore(frameBaseAddrId, frame_base, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                SPIRVId id20 = module.getNextId();
                blockScope.add(new SPIRVOpLoad(pointerToGlobalMemoryHeap, id20, heapBaseAddrId, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                SPIRVId id21 = module.getNextId();
                blockScope.add(
                        new SPIRVOpLoad(primitives.getTypeInt(SPIRVKind.OP_TYPE_INT_64), id21, frameBaseAddrId, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                SPIRVId ptridx = module.getNextId();
                blockScope.add(new SPIRVOpInBoundsPtrAccessChain(pointerToGlobalMemoryHeap, ptridx, id20, id21, new SPIRVMultipleOperands<>()));

                SPIRVId id23 = module.getNextId();
                blockScope.add(new SPIRVOpBitcast(pointerToULongFunction, id23, ptridx));

                blockScope.add(new SPIRVOpStore(frameId, id23, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                SPIRVId id24 = module.getNextId();
                blockScope.add(new SPIRVOpLoad(pointerToULongFunction, id24, frameId, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                SPIRVId ptridx1 = module.getNextId();
                blockScope.add(new SPIRVOpInBoundsPtrAccessChain(pointerToULongFunction, ptridx1, id24, constants.get("3"), new SPIRVMultipleOperands<>()));

                SPIRVId id27 = module.getNextId();
                blockScope.add(new SPIRVOpLoad(ulong, id27, ptridx1, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                blockScope.add(new SPIRVOpStore(ul0, id27, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                SPIRVId id28 = module.getNextId();
                blockScope.add(new SPIRVOpLoad(ulong, id28, ul0, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                SPIRVId add = module.getNextId();
                blockScope.add(new SPIRVOpIAdd(ulong, add, id28, constants.get("24")));

                blockScope.add(new SPIRVOpStore(ul1, add, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                SPIRVId id31 = module.getNextId();
                blockScope.add(new SPIRVOpLoad(ulong, id31, ul1, new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(8)))));

                SPIRVId id34 = module.getNextId();
                blockScope.add(new SPIRVOpConvertUToPtr(ptrCrossWorkGroupUInt, id34, id31));

                blockScope.add(new SPIRVOpStore(id34, constants.get("50"), new SPIRVOptionalOperand<>(SPIRVMemoryAccess.Aligned(new SPIRVLiteralInteger(4)))));

                blockScope.add(new SPIRVOpReturn());
            }

        } else

        {

            // Inner function to be called within the main kernel

        }
    }

    private void emitEpilogue(SPIRVAssembler asm) {
        asm.closeFunction(asm.functionScope);
    }
}
