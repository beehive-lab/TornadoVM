package tornado.drivers.opencl.graal.backend;

import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.asm.DataBuilder;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.framemap.ReferenceMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.phases.tiers.SuitesProvider;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.*;
import tornado.api.Vector;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.enums.Access;
import tornado.drivers.opencl.OCLContext;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.OCLTargetDescription;
import tornado.drivers.opencl.graal.*;
import tornado.drivers.opencl.graal.asm.OCLAssembler;
import tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import tornado.drivers.opencl.graal.compiler.*;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.mm.OCLByteBuffer;
import tornado.graal.backend.TornadoBackend;
import tornado.lang.CompilerInternals;
import tornado.meta.Meta;

import static tornado.common.Tornado.DEBUG_KERNEL_ARGS;
import static tornado.common.exceptions.TornadoInternalError.*;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;
import static tornado.runtime.TornadoRuntime.getTornadoRuntime;
import static tornado.graal.compiler.TornadoCodeGenerator.trace;

public class OCLBackend extends TornadoBackend<OCLProviders> implements FrameMap.ReferenceMapBuilderFactory {

    public final static boolean SHOW_OPENCL = Boolean.parseBoolean(System
            .getProperty(
                    "tornado.opencl.print",
                    "False"));
    public final static String OPENCL_PATH = System.getProperty(
            "tornado.opencl.path",
            "./opencl");

    @Override
    public OCLTargetDescription getTarget() {
        return (OCLTargetDescription) target;
    }

    final OCLTargetDescription target;
    final OCLArchitecture architecture;
    final OCLContext openclContext;
    final OCLDeviceContext deviceContext;
    final OpenCLCodeCache codeCache;
    OpenCLInstalledCode lookupCode;
    final AtomicInteger id = new AtomicInteger(0);

    public OCLBackend(
            OCLProviders providers,
            OCLTargetDescription target,
            OpenCLCodeCache codeCache,
            OCLContext openclContext,
            OCLDeviceContext deviceContext) {
        super(providers);
        this.target = target;
        this.codeCache = codeCache;
        this.openclContext = openclContext;
        this.deviceContext = deviceContext;
        architecture = (OCLArchitecture) target.arch;

    }

    @Override
    public String decodeDeopt(long value) {
        DeoptimizationReason reason = getProviders().getMetaAccess().decodeDeoptReason(JavaConstant.forLong(value));
        DeoptimizationAction action = getProviders().getMetaAccess().decodeDeoptAction(JavaConstant.forLong(value));

        return String.format("deopt: reason=%s, action=%s", reason.toString(), action.toString());
    }

    public boolean isInitialised() {
        return deviceContext.isInitialised();
    }

    @SuppressWarnings("unused")
    private static Object lookupBufferAddress() {
        return CompilerInternals.getSlotsAddress();
    }

    @Override
    public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
        return new OCLReferenceMapBuilder();
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig rc) {
        return null;
    }

    @Override
    public Set<Register> translateToCallerRegisters(Set<Register> set) {
        unimplemented();
        return Collections.EMPTY_SET;
    }

    private Method getLookupMethod() {
        Method method = null;
        try {
            method = this.getClass().getDeclaredMethod("lookupBufferAddress");
        } catch (NoSuchMethodException | SecurityException e) {
            Tornado.fatal("unable to find lookupBufferAddress method???");
        }
        return method;
    }

    public long readHeapBaseAddress() {
        final OCLByteBuffer bb = deviceContext.getMemoryManager().getSubBuffer(0, 16);
        bb.putLong(0);
        bb.putLong(0);

        lookupCode.execute(bb, null);

//        bb.dump();
        final long address = bb.getLong(0);
        Tornado.info("Heap address @ 0x%x on %s", address, deviceContext.getDevice().getName());
        return address;
    }

    public void init() {

        /*
         * Allocate the smallest of the requested heap size or the max global
         * memory size.
         */
        final long memorySize = Math.min(DEFAULT_HEAP_ALLOCATION, deviceContext.getDevice()
                .getMaxAllocationSize());
        if (memorySize < DEFAULT_HEAP_ALLOCATION) {
            Tornado.warn(
                    "Unable to allocate %s of heap space - resized to %s",
                    RuntimeUtilities.humanReadableByteCount(DEFAULT_HEAP_ALLOCATION, false),
                    RuntimeUtilities.humanReadableByteCount(memorySize, false));
        }
        Tornado.info("%s: allocating %s of heap space", deviceContext.getDevice().getName(),
                RuntimeUtilities.humanReadableByteCount(memorySize, false));
        deviceContext.getMemoryManager().allocateRegion(memorySize);

        /*
         * Retrive the address of the heap on the device
         */
        lookupCode = OCLCompiler.compileCodeForDevice(
                getTornadoRuntime().resolveMethod(getLookupMethod()), null, null, (OCLProviders) getProviders(), this);

        deviceContext.getMemoryManager().init(this, readHeapBaseAddress());
    }

    public OCLDeviceContext getDeviceContext() {
        return deviceContext;
    }

    @Override
    protected OCLAssembler createAssembler(FrameMap frameMap) {
        return new OCLAssembler(target);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method) {
        emitCode((OCLCompilationResultBuilder) crb, lir, method);
    }

    public void emitCode(OCLCompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method) {

        final OCLAssembler asm = (OCLAssembler) crb.asm;
        emitPrologue(crb, asm, method, lir);
        crb.emit(lir);
        emitEpilogue(asm);

    }

    private void emitEpilogue(OCLAssembler asm) {
        asm.endScope();

    }

    private void addVariableDef(Map<OCLKind, Set<Variable>> kindToVariable, Variable value) {
        if (value instanceof Variable) {
            Variable var = (Variable) value;

            if (!(var.getPlatformKind() instanceof OCLKind)) {
                shouldNotReachHere();
            }

            OCLKind oclKind = (OCLKind) var.getPlatformKind();
            if (oclKind == OCLKind.ILLEGAL) {
                shouldNotReachHere();
//                return;
            }
//            guarantee(oclKind != OCLKind.ILLEGAL,"invalid type for %s",var.getKind().name());
//            final String type = oclKind.toString(); //toOpenCLType(var.getKind(), var.getPlatformKind());

            if (!kindToVariable.containsKey(oclKind)) {
                kindToVariable.put(oclKind, new HashSet<>());
            }

            final Set<Variable> varList = kindToVariable.get(oclKind);
            varList.add(var);

        }
    }

    private void emitVariableDefs(OCLCompilationResultBuilder crb, OCLAssembler asm, LIR lir) {
        Map<OCLKind, Set<Variable>> kindToVariable = new HashMap<>();
        final int expectedVariables = lir.numVariables();
        final AtomicInteger variableCount = new AtomicInteger();

        for (AbstractBlockBase<?> b : lir.linearScanOrder()) {
            for (LIRInstruction insn : lir.getLIRforBlock(b)) {

                insn.forEachOutput((instruction, value, mode, flags) -> {
                    if (value instanceof Variable) {
                        Variable variable = (Variable) value;
                        if (variable.getName() != null) {
                            addVariableDef(kindToVariable, (Variable) variable);
                            variableCount.incrementAndGet();
                        }
                    }
                    return value;
                });
            }
        }

        trace("found %d variable, expected (%d)", variableCount.get(), expectedVariables);

        for (OCLKind type : kindToVariable.keySet()) {
            asm.indent();
            asm.emit("%s ", type);
            for (Variable var : kindToVariable.get(type)) {
                asm.value(crb, var);
                asm.emit(", ");
            }
            asm.emitByte(';', asm.position() - 2);
            asm.eol();
        }

    }

    private void emitPrologue(OCLCompilationResultBuilder crb, OCLAssembler asm,
            ResolvedJavaMethod method, LIR lir) {

        String methodName = crb.compilationResult.getName();

        if (crb.isKernel()) {
            /*
             * BUG There is a bug on some OpenCL devices which requires us to
             * insert an extra OpenCL buffer into the kernel arguments. This has
             * the effect of shifting the devices address mappings, which allows
             * us to avoid the heap starting at address 0x0. (I assume that this
             * is a interesting case that leads to a few issues.) Iris Pro is
             * the only culprit at the moment.
             */
            final String bumpBuffer = (deviceContext.needsBump()) ? String.format("%s void *dummy, ", OCLAssemblerConstants.GLOBAL_MEM_MODIFIER) : "";

            asm.emitLine("%s void %s(%s%s)",
                    OCLAssemblerConstants.KERNEL_MODIFIER, methodName,
                    bumpBuffer,
                    architecture.getABI());
            asm.beginScope();
            emitVariableDefs(crb, asm, lir);
            asm.eol();
            asm.emitStmt("%s ulong *slots = (%s ulong *) &%s[%s]",
                    OCLAssemblerConstants.GLOBAL_MEM_MODIFIER,
                    OCLAssemblerConstants.GLOBAL_MEM_MODIFIER,
                    OCLAssemblerConstants.HEAP_REF_NAME, OCLAssemblerConstants.STACK_REF_NAME);
            asm.eol();
            if (DEBUG_KERNEL_ARGS && (method != null && !method.getDeclaringClass().getUnqualifiedName().equalsIgnoreCase(this.getClass().getSimpleName()))) {
                asm.emitLine("if(get_global_id(0) == 0 && get_global_id(1) ==0){");
                asm.pushIndent();
                asm.emitStmt("int numArgs = slots[5] >> 32");
                asm.emitStmt("printf(\"got %%d args...\\n\",numArgs)");
                asm.emitLine("for(int i=0;i<numArgs;i++) {");
                asm.pushIndent();
                asm.emitStmt("printf(\"%20s - arg[%%d]: 0x%%lx\\n\", i, slots[6 + i])", method.getName());
                asm.popIndent();
                asm.emitLine("}");
                asm.popIndent();
                asm.emitLine("}");
            }

            if (ENABLE_EXCEPTIONS) {
                asm.emitStmt("if(slots[0] != 0) return");
            }
            asm.eol();
        } else {

            final CallingConvention incomingArguments = OpenCLCodeUtil.getCallingConvention(
                    codeCache, HotSpotCallingConventionType.JavaCallee, method, false);
            methodName = OCLUtils.makeMethodName(method);
            final JavaKind returnKind = method.getSignature().getReturnKind();
            final ResolvedJavaType returnType = method.getSignature().getReturnType(null)
                    .resolve(method.getDeclaringClass());
            OCLKind returnOclKind = (returnType.getAnnotation(Vector.class) == null)
                    ? getTarget().getOCLKind(returnKind)
                    : OCLKind.fromResolvedJavaType(returnType);
            //getTarget().getLIRKind(returnKind);
            asm.emit("%s %s(%s", returnOclKind.name(),
                    methodName, architecture.getABI());

            final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);
            final Value[] params = new Value[incomingArguments.getArgumentCount()];

//            for (int i = 0; i < locals.length; i++) {
//                Local local = locals[i];
//		System.out.printf("local: slot=%d, name=%s, type=%s\n",local.getSlot(),local.getName(),local.getType().resolve(method.getDeclaringClass()));
//            }
            if (params.length > 0) {
                asm.emit(", ");
            }

            for (int i = 0; i < params.length; i++) {
                final AllocatableValue param = incomingArguments.getArgument(i);

//                OCLKind oclKind = OCLKind.ILLEGAL;
//                if (param.getPlatformKind().isObject()) {
//
//                    oclKind = OCLKind.resolveToVectorKind(locals[i].getType().resolve(method.getDeclaringClass()));
//                    if (oclKind == OCLKind.ILLEGAL) {
//                        oclKind = target.getOCLKind(param.getKind());
//                    }
//
//                    asm.emit("%s %s", oclKind.toString(),
//                            locals[i].getName());
//                } else {
//                    oclKind = target.getOCLKind(param.getKind());
//
//                }
                OCLKind oclKind = (OCLKind) param.getPlatformKind();
                guarantee(oclKind != OCLKind.ILLEGAL, "illegal type for %s", param.getPlatformKind());
                asm.emit("%s %s", oclKind.toString(),
                        locals[i].getName());
                if (i < params.length - 1) {
                    asm.emit(", ");
                }
            }
            asm.emit(")");
            asm.eol();
            asm.beginScope();
            emitVariableDefs(crb, asm, lir);
            asm.eol();
        }
    }

    public OCLSuitesProvider getTornadoSuites() {
        return ((OCLProviders) getProviders()).getSuitesProvider();
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRes,
            FrameMap frameMap, CompilationResult compilationResult,
            CompilationResultBuilderFactory factory) {
        return newCompilationResultBuilder(lirGenRes, frameMap,
                (OCLCompilationResult) compilationResult, factory, false);
    }

    public OCLCompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRes,
            FrameMap frameMap, OCLCompilationResult compilationResult,
            CompilationResultBuilderFactory factory, boolean isKernel) {
        // final OCLLIRGenerationResult gen = (OCLLIRGenerationResult) lirGenRes;
        // LIR lir = gen.getLIR();

        OCLAssembler asm = createAssembler(frameMap);
        OpenCLFrameContext frameContext = new OpenCLFrameContext();
        DataBuilder dataBuilder = new OCLDataBuilder();
        OCLCompilationResultBuilder crb = new OCLCompilationResultBuilder(codeCache, getForeignCalls(), frameMap, asm, dataBuilder, frameContext, compilationResult);
        crb.setKernel(isKernel);

        return crb;
    }

    @Override
    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new OpenCLFrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache()
                .getRegisterConfig() : registerConfig;
        return new OpenCLFrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(),
                registerConfig);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(String compilationUnitName, LIR lir,
            FrameMapBuilder frameMapBuilder, StructuredGraph graph, Object stub) {
        return new OCLLIRGenerationResult(compilationUnitName, lir, frameMapBuilder, new CallingConvention(0, null, (AllocatableValue[]) null));
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenResult) {
        return new OCLLIRGenerator(getProviders(), lirGenResult);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new OCLNodeLIRBuilder(graph, lirGen, new OCLNodeMatchRules(lirGen));
    }

    public OpenCLInstalledCode compile(final Method method, final Object[] parameters,
            final Access[] access, final Meta meta) {
        Tornado.info("invoke: %s", method.getName());
        Tornado.info("args: %s", RuntimeUtilities.formatArray(parameters));
        Tornado.info("access: %s", RuntimeUtilities.formatArray(access));
        Tornado.info("meta: ");
        for (Object provider : meta.providers()) {
            Tornado.info("\t%s", provider.toString().trim());
        }

        final ResolvedJavaMethod resolvedMethod = getProviders().getMetaAccess().lookupJavaMethod(
                method);
        final OpenCLInstalledCode methodCode = OCLCompiler.compileCodeForDevice(resolvedMethod,
                parameters, meta, (OCLProviders) getProviders(), this);

        if (SHOW_OPENCL) {
            String filename = getFile(method.getName() + "-" + meta.hashCode());
            Tornado.info("Generated code for device %s - %s\n",
                    deviceContext.getDevice().getName(), filename);

            try (PrintWriter fileOut = new PrintWriter(filename)) {
                String source = new String(methodCode.getCode(), "ASCII");
                fileOut.println(source.trim());
            } catch (UnsupportedEncodingException | FileNotFoundException e) {
                Tornado.warn("Unable to write source to file: %s", e.getMessage());
            }
        }

        return methodCode;
    }

    private static String getFile(String name) {
        return String.format("%s/%s.cl", OPENCL_PATH.trim(), name.trim());
    }

    @Override
    public String toString() {
        return String.format("Backend: arch=%s, device=%s", architecture.getName(), deviceContext
                .getDevice().getName());
    }

    @Override
    public OpenCLCodeCache getCodeCache() {
        return codeCache;
    }

    @Override
    public SuitesProvider getSuites() {
        unimplemented();
        return null;
    }

    public void reset() {
        getDeviceContext().reset();
        codeCache.reset();
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod rjm, CompilationResult cr) {
        unimplemented();
        return null;
    }

}
