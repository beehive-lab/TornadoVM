package uk.ac.manchester.tornado.drivers.cuda.graal.backend;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import uk.ac.manchester.tornado.drivers.cuda.CUDADevice;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContext;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDescription;
import uk.ac.manchester.tornado.drivers.cuda.graal.*;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.*;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.PTXKind;
import uk.ac.manchester.tornado.runtime.graal.backend.TornadoBackend;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;

import java.util.Map;
import java.util.Set;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

public class PTXBackend extends TornadoBackend<PTXProviders> implements FrameMap.ReferenceMapBuilderFactory {

    final CUDADeviceContext deviceContext;
    private boolean isInitialised;
    final CUDATargetDescription target;
    private PTXArchitecture arch;
    private PTXCodeProvider codeCache;
    private OptionValues options;

    public PTXBackend(PTXProviders providers, CUDADeviceContext deviceContext, CUDATargetDescription target,
                      PTXCodeProvider codeCache, OptionValues options) {
        super(providers);

        this.deviceContext = deviceContext;
        this.target = target;
        this.codeCache = codeCache;
        this.options = options;
        arch = target.getArch();
        isInitialised = false;
    }

    @Override
    public String decodeDeopt(long value) {
        return null;
    }

    @Override
    public SuitesProvider getSuites() {
        return null;
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        return new RegisterAllocationConfig(registerConfig, allocationRestrictedTo);
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        return null;
    }

    @Override
    public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
        return null;
    }

    public TornadoSuitesProvider getTornadoSuites() {

        return ((PTXProviders) getProviders()).getSuitesProvider();
    }

    public boolean isInitialised() {
        return isInitialised;
    }

    public void init() {
        if (isInitialised) return;

        allocateHeapMemoryOnDevice();

        isInitialised = true;
    }

    /**
     * It allocates the smallest of the requested heap size or the max global memory
     * size.
     */
    public void allocateHeapMemoryOnDevice() {
        //long memorySize = Math.min(DEFAULT_HEAP_ALLOCATION, deviceContext.getDevice().getDeviceMaxAllocationSize());
        //if (memorySize < DEFAULT_HEAP_ALLOCATION) {
            //Tornado.info("Unable to allocate %s of heap space - resized to %s", humanReadableByteCount(DEFAULT_HEAP_ALLOCATION, false), humanReadableByteCount(memorySize, false));
        //}
        //Tornado.info("%s: allocating %s of heap space", deviceContext.getDevice().getDeviceName(), humanReadableByteCount(memorySize, false));
        deviceContext.getMemoryManager().allocateRegion(DEFAULT_HEAP_ALLOCATION);
    }

    public CUDADeviceContext getDeviceContext() {
        return deviceContext;
    }

    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig nonNullRegisterConfig = (registerConfig == null) ? getCodeCache().getRegisterConfig() : registerConfig;
        return new PTXFrameMapBuilder(newFrameMap(nonNullRegisterConfig), getCodeCache(), registerConfig);
    }

    private FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new PTXFrameMap(getCodeCache(), registerConfig, this);
    }

    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier identifier,
                                                      LIR lir,
                                                      FrameMapBuilder frameMapBuilder,
                                                      RegisterAllocationConfig registerAllocationConfig) {

        return new PTXLIRGenerationResult(
                identifier,
                lir,
                frameMapBuilder,
                registerAllocationConfig,
                new CallingConvention(0, null, (AllocatableValue[]) null)
        );
    }

    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        return new PTXLIRGenerator(getProviders(), lirGenRes);
    }

    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new PTXNodeLIRBuilder(graph, lirGen, new PTXNodeMatchRules(lirGen));
    }

    public PTXCompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRes,
                                                                   FrameMap frameMap,
                                                                   PTXCompilationResult compilationResult,
                                                                   CompilationResultBuilderFactory factory,
                                                                   boolean isKernel,
                                                                   boolean isParallel) {
        PTXAssembler asm = createAssembler((PTXLIRGenerationResult) lirGenRes);
        PTXFrameContext frameContext = new PTXFrameContext();
        DataBuilder dataBuilder = new PTXDataBuilder();
        PTXCompilationResultBuilder crb = new PTXCompilationResultBuilder(
                codeCache,
                getForeignCalls(),
                frameMap,
                asm,
                dataBuilder,
                frameContext,
                options,
                compilationResult
        );
        crb.setKernel(isKernel);
        crb.setParallel(isParallel);
        return crb;
    }

    private PTXAssembler createAssembler(PTXLIRGenerationResult result) {
        return new PTXAssembler(target, result);
    }

    public void emitCode(PTXCompilationResultBuilder crb, PTXLIRGenerationResult lirGenRes) {
        final PTXAssembler asm = (PTXAssembler) crb.asm;
        emitPrologue(crb, asm, lirGenRes);
        crb.emit(lirGenRes.getLIR());
        emitEpilogue(asm);
    }

    private void emitEpilogue(PTXAssembler asm) {
        asm.emitLine("}");
    }

    private void emitPrologue(PTXCompilationResultBuilder crb, PTXAssembler asm, PTXLIRGenerationResult lirGenRes) {
        if (crb.isKernel()) {
            emitPTXHeader(asm);
            emitKernelFunction(asm, crb.compilationResult.getName());
            emitVariableDefs(asm, lirGenRes);
        }
        else {
            unimplemented("Non-kernel function calls are not implemented id CUDA_PTX yet.");
        }
    }

    private void emitKernelFunction(PTXAssembler asm, String methodName) {

        asm.emitLine(
                "%s %s %s(%s) {",
                PTXAssemblerConstants.EXTERNALLY_VISIBLE,
                PTXAssemblerConstants.KERNEL_ENTRYPOINT,
                methodName,
                arch.getABI()
        );
    }

    private void emitPTXHeader(PTXAssembler asm) {
        CUDADevice device = deviceContext.getDevice();
        String headerFormat = "%s %s";
        asm.emitLine(headerFormat, PTXAssemblerConstants.COMPUTE_VERSION, device.getDeviceComputeCapability());
        asm.emitLine(headerFormat, PTXAssemblerConstants.TARGET_ARCH, device.getTargetArchitecture());
        asm.emitLine(headerFormat, PTXAssemblerConstants.ADDRESS_HEADER, arch.getWordSize() * 8);
        asm.emitLine("");
    }

    private void emitVariableDefs(PTXAssembler asm, PTXLIRGenerationResult lirGenRes) {
        Map<PTXKind, Set<Variable>> kindToVariable = lirGenRes.getVariableTable();

        for (PTXKind type : kindToVariable.keySet()) {
            asm.emitLine(
                    "\t.reg .%s %s<%d>;",
                    type,
                    type.getRegisterTypeString(),
                    kindToVariable.get(type).size()
            );
        }

    }
}
