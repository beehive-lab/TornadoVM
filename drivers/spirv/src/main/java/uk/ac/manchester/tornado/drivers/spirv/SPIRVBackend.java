package uk.ac.manchester.tornado.drivers.spirv;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.runtime.common.RuntimeUtilities.humanReadableByteCount;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

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
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.spirvproto.lib.InvalidSPIRVModuleException;
import uk.ac.manchester.spirvproto.lib.SPIRVHeader;
import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.SPIRVModule;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpCapability;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpEntryPoint;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunctionEnd;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpFunctionParameter;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpIAdd;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLabel;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpLoad;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpMemoryModel;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpReturn;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpStore;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeFunction;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeInt;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypePointer;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeVector;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpTypeVoid;
import uk.ac.manchester.spirvproto.lib.instructions.SPIRVOpVariable;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVAddressingModel;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVCapability;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVExecutionModel;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVFunctionControl;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVId;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralInteger;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVLiteralString;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryAccess;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMemoryModel;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVMultipleOperands;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVOptionalOperand;
import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVStorageClass;
import uk.ac.manchester.tornado.drivers.opencl.OCLCodeCache;
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
import uk.ac.manchester.tornado.runtime.common.Tornado;
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
        SPIRVModule module = new SPIRVModule(new SPIRVHeader(1, 2, 29, 0, 0));

        // emitPrologue(crb, asm, method, lir, module);
        // crb.emit(lir);
        // emitEpilogue(asm);
        dummySPIRVModuleTest(module);

        emitSPIRVCodeIntoASMModule(asm, module);
    }

    private void dummySPIRVModuleTest(SPIRVModule module) {
        SPIRVInstScope functionScope;
        SPIRVInstScope blockScope;

        emitSPIRVHeader(module);

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

        module.add(new SPIRVOpEntryPoint(SPIRVExecutionModel.Kernel(), functionDef, new SPIRVLiteralString("copyTestZero"), new SPIRVMultipleOperands<>()));

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

    private void emitSPIRVHeader(SPIRVModule module) {
        module.add(new SPIRVOpCapability(SPIRVCapability.Addresses()));
        module.add(new SPIRVOpCapability(SPIRVCapability.Linkage()));
        module.add(new SPIRVOpCapability(SPIRVCapability.Kernel()));
        module.add(new SPIRVOpMemoryModel(SPIRVAddressingModel.Physical64(), SPIRVMemoryModel.OpenCL()));
    }

    private void emitPrologue(SPIRVCompilationResultBuilder crb, SPIRVAssembler asm, ResolvedJavaMethod method, LIR lir, SPIRVModule module) {
        String methodName = crb.compilationResult.getName();
        if (crb.isKernel()) {
            final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();

            // Emit SPIR-V Header
            emitSPIRVHeader(module);

        } else {

        }
    }

    private void emitEpilogue(SPIRVAssembler asm) {
    }
}
