package tornado.drivers.opencl.graal.backend;

import com.oracle.graal.api.code.Architecture;
import com.oracle.graal.api.code.CallingConvention;
import com.oracle.graal.api.code.CallingConvention.Type;
import com.oracle.graal.api.code.CompilationResult;
import com.oracle.graal.api.code.DisassemblerProvider;
import com.oracle.graal.api.code.RegisterConfig;
import com.oracle.graal.api.code.TargetDescription;
import com.oracle.graal.api.code.stack.StackIntrospection;
import com.oracle.graal.api.meta.AllocatableValue;
import com.oracle.graal.api.meta.DeoptimizationAction;
import com.oracle.graal.api.meta.DeoptimizationReason;
import com.oracle.graal.api.meta.JavaConstant;
import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.LIRKind;
import com.oracle.graal.api.meta.Local;
import com.oracle.graal.api.meta.PlatformKind;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.api.meta.ResolvedJavaType;
import com.oracle.graal.api.meta.Value;
import com.oracle.graal.asm.Assembler;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.CompilationResultBuilderFactory;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.lir.framemap.FrameMapBuilder;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.phases.tiers.SuitesProvider;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import tornado.api.Vector;
import tornado.common.RuntimeUtilities;
import tornado.common.Tornado;
import tornado.common.enums.Access;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.OCLContext;
import tornado.drivers.opencl.OCLDeviceContext;
import tornado.drivers.opencl.graal.OCLProviders;
import tornado.drivers.opencl.graal.OCLSuitesProvider;
import tornado.drivers.opencl.graal.OCLUtils;
import tornado.drivers.opencl.graal.OpenCLCodeCache;
import tornado.drivers.opencl.graal.OpenCLCodeUtil;
import tornado.drivers.opencl.graal.OpenCLFrameContext;
import tornado.drivers.opencl.graal.OpenCLFrameMap;
import tornado.drivers.opencl.graal.OpenCLFrameMapBuilder;
import tornado.drivers.opencl.graal.OpenCLInstalledCode;
import tornado.drivers.opencl.graal.asm.OpenCLAssembler;
import tornado.drivers.opencl.graal.asm.OpenCLAssemblerConstants;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import tornado.drivers.opencl.graal.compiler.OCLCompiler;
import tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import tornado.drivers.opencl.graal.compiler.OCLNodeLIRBuilder;
import tornado.drivers.opencl.graal.compiler.OpenCLLIRGenerationResult;
import tornado.drivers.opencl.graal.lir.OCLLIRInstruction.AssignStmt;
import tornado.drivers.opencl.mm.OCLByteBuffer;
import tornado.graal.backend.TornadoBackend;
import tornado.graal.nodes.vector.VectorKind;
import tornado.lang.CompilerInternals;
import tornado.meta.Meta;
import tornado.runtime.TornadoRuntime;

public class OCLBackend extends TornadoBackend<OCLProviders> {

    public final static boolean SHOW_OPENCL = Boolean.parseBoolean(System
            .getProperty(
                    "tornado.opencl.print",
                    "False"));
    public final static String OPENCL_PATH = System.getProperty(
            "tornado.opencl.path",
            "./opencl");

    @Override
    public TargetDescription getTarget() {
        return target;
    }

    final TargetDescription target;
    final Architecture architecture;
    final OCLContext openclContext;
    final OCLDeviceContext deviceContext;
    final OpenCLCodeCache codeCache;
    OpenCLInstalledCode lookupCode;

    public OCLBackend(
            OCLProviders providers,
            TargetDescription target,
            OCLContext context,
            int deviceIndex) {
        super(providers);
        this.openclContext = context;
        deviceContext = context.createDeviceContext(deviceIndex);
        architecture = target.arch;
        this.target = target;

        codeCache = new OpenCLCodeCache(getTarget());
        codeCache.setBackend(this);
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

        // bb.dump();
        final long address = bb.getLong(0);
        Tornado.info("Heap address @ 0x%x on %s", address, deviceContext.getDevice().getName());
        return address;
    }

    public void init() {

        /*
         * Allocate the smallest of the requested heap size or the max global memory size.
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
                TornadoRuntime.runtime.resolveMethod(getLookupMethod()), null, null, (OCLProviders) getProviders(), this);

        deviceContext.getMemoryManager().init(this, readHeapBaseAddress());
    }

    public OCLDeviceContext getDeviceContext() {
        return deviceContext;
    }

    @Override
    protected Assembler createAssembler(FrameMap frameMap) {
        return new OpenCLAssembler(target);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method) {
        emitCode((OCLCompilationResultBuilder) crb, lir, method);
    }

    public void emitCode(OCLCompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method) {

        final OpenCLAssembler asm = (OpenCLAssembler) crb.asm;
        emitPrologue(crb, asm, method, lir);
        crb.emit(lir);
        emitEpilogue(asm);

    }

    private void emitEpilogue(OpenCLAssembler asm) {
        asm.endScope();

    }

    public static String platformKindToOpenCLKind(PlatformKind kind) {
        switch (kind.name().toLowerCase()) {
            case "object":
                return "ulong";
            case "byte":
                return "char";
            case "byte3":
                return "char3";
            case "byte4":
                return "char4";
            default:
                return kind.name().toLowerCase();
        }
    }

    public static String toOpenCLType(Kind kind, PlatformKind platformKind) {
        String type = "";

        if (kind.isObject()) {
            final VectorKind vectorKind = VectorKind.fromClass(kind.toJavaClass());
            if (vectorKind != VectorKind.Illegal) {
                return vectorKind.getJavaName();
            }
        }

        if (kind == Kind.Boolean) {
            return "bool";
        }

        if (kind.isUnsigned()) {
            type += "u";
        }
        type += platformKindToOpenCLKind(platformKind);

        return type;
    }

    private void addVariableDef(Map<String, Set<Variable>> kindToVariable, Variable value) {
        if (value instanceof Variable) {
            Variable var = (Variable) value;
            final String type = toOpenCLType(var.getKind(), var.getPlatformKind());

            if (!kindToVariable.containsKey(type)) {
                kindToVariable.put(type, new HashSet<>());
            }

            final Set<Variable> varList = kindToVariable.get(type);
            varList.add(var);

        }
    }

    private void emitVariableDefs(OCLCompilationResultBuilder crb, OpenCLAssembler asm, LIR lir) {
        Map<String, Set<Variable>> kindToVariable = new HashMap<>();
        final int expectedVariables = lir.numVariables();
        int variableCount = 0;

        for (AbstractBlockBase<?> b : lir.linearScanOrder()) {
            for (LIRInstruction insn : lir.getLIRforBlock(b)) {
                if (insn instanceof AssignStmt) {
                    final Value value = ((AssignStmt) insn).getResult();

                    if (value instanceof Variable) {
                        Variable var = (Variable) value;
                        addVariableDef(kindToVariable, var);
                        variableCount++;

                    }
                }

            }
        }

        Tornado.trace("found %d variable, expected (%d)", variableCount, expectedVariables);

        for (String type : kindToVariable.keySet()) {
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

    private void emitPrologue(OCLCompilationResultBuilder crb, OpenCLAssembler asm,
            ResolvedJavaMethod method, LIR lir) {
        if (crb.isKernel()) {
            /*
             * BUG There is a bug on some OpenCL devices which requires us to insert an extra OpenCL buffer into the kernel arguments.
             * This has the effect of shifting the devices address mappings, which allows us to avoid the heap starting at address 0x0.
             * (I assume that this is a interesting case that leads to a few issues.) Iris Pro is the only culprit at the moment.
             */
            final String bumpBuffer = (deviceContext.needsBump()) ? String.format("%s void *dummy, ", OpenCLAssemblerConstants.GLOBAL_MEM_MODIFIER) : "";

            asm.emitLine("%s void %s(%s%s char *%s, ulong %s)",
                    OpenCLAssemblerConstants.KERNEL_MODIFIER, method.getName(),
                    bumpBuffer,
                    OpenCLAssemblerConstants.GLOBAL_MEM_MODIFIER,
                    OpenCLAssemblerConstants.HEAP_REF_NAME, OpenCLAssemblerConstants.STACK_REF_NAME);
            asm.beginScope();
            emitVariableDefs(crb, asm, lir);
            asm.eol();
            asm.emitStmt("%s ulong *slots = (%s ulong *) &%s[%s]",
                    OpenCLAssemblerConstants.GLOBAL_MEM_MODIFIER,
                    OpenCLAssemblerConstants.GLOBAL_MEM_MODIFIER,
                    OpenCLAssemblerConstants.HEAP_REF_NAME, OpenCLAssemblerConstants.STACK_REF_NAME);
            asm.eol();
            if (!method.getDeclaringClass().getUnqualifiedName().equalsIgnoreCase(this.getClass().getSimpleName())) {
//                asm.emitLine("if(get_global_id(0) == 0 && get_global_id(1) ==0){");
//                asm.emitStmt("int numArgs = slots[5] >> 32");
//                asm.emitStmt("printf(\"got %%d args...\\n\",numArgs)");
//                asm.emitStmt("for(int i=0;i<numArgs;i++) {  printf(\"%20s - arg[%%d]: 0x%%lx\\n\", i, slots[6 + i]); }",method.getName());
//                asm.emitLine("}");
            }

            if (ENABLE_EXCEPTIONS) {
                asm.emitStmt("if(slots[0] != 0) return");
            }
            asm.eol();
        } else {

            final CallingConvention incomingArguments = OpenCLCodeUtil.getCallingConvention(
                    codeCache, Type.JavaCallee, method, false);
            final String methodName = OCLUtils.makeMethodName(method);
            final Kind returnKind = method.getSignature().getReturnKind();
            final ResolvedJavaType returnType = method.getSignature().getReturnType(null)
                    .resolve(method.getDeclaringClass());
            final PlatformKind platformKind = (returnType.getAnnotation(Vector.class) == null) ? LIRKind
                    .value(returnKind).getPlatformKind() : VectorKind
                    .fromResolvedJavaType(returnType);
            asm.emit("%s %s(%s char *%s, ulong %s", toOpenCLType(returnKind, platformKind),
                    methodName, OpenCLAssemblerConstants.GLOBAL_MEM_MODIFIER,
                    OpenCLAssemblerConstants.HEAP_REF_NAME, OpenCLAssemblerConstants.STACK_REF_NAME);

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

                if (param.getKind().isObject()) {
                    VectorKind vectorKind = VectorKind.fromResolvedJavaType(locals[i].getType().resolve(method.getDeclaringClass()));
                    if (vectorKind != VectorKind.Illegal) {
                        asm.emit("%s %s", vectorKind.getJavaName(),
                                locals[i].getName());
                    } else {
                        asm.emit("%s %s", toOpenCLType(param.getKind(), param.getPlatformKind()),
                                locals[i].getName());
                    }
                } else {

                    asm.emit("%s %s", toOpenCLType(param.getKind(), param.getPlatformKind()),
                            locals[i].getName());

                }
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

    @Override
    public DisassemblerProvider getDisassembler() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public StackIntrospection getStackIntrospection() {
        // System.out.println("getStackIntrospection -> unimplemented");
        return null;
    }

    public OCLSuitesProvider getTornadoSuites() {
        return ((OCLProviders) getProviders()).getSuitesProvider();
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRes,
            FrameMap frameMap, CompilationResult compilationResult,
            CompilationResultBuilderFactory factory) {
        return newCompilationResultBuilder(lirGenRes, frameMap,
                (OCLCompilationResult) compilationResult, factory);
    }

    public OCLCompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRes,
            FrameMap frameMap, OCLCompilationResult compilationResult,
            CompilationResultBuilderFactory factory, boolean isKernel) {
        // final OpenCLLIRGenerationResult gen = (OpenCLLIRGenerationResult) lirGenRes;
        // LIR lir = gen.getLIR();

        Assembler asm = createAssembler(frameMap);
        OpenCLFrameContext frameContext = new OpenCLFrameContext();

        OCLCompilationResultBuilder crb = new OCLCompilationResultBuilder(codeCache,
                getForeignCalls(), frameMap, asm, frameContext, compilationResult, isKernel);

        return crb;
    }

    @Override
    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new OpenCLFrameMap(getCodeCache(), registerConfig);
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
            FrameMapBuilder frameMapBuilder, ResolvedJavaMethod method, Object stub) {
        return new OpenCLLIRGenerationResult(compilationUnitName, lir, frameMapBuilder);
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(CallingConvention cc, LIRGenerationResult lirGenResult) {
        return new OCLLIRGenerator((OCLProviders) getProviders(), codeCache, cc, lirGenResult);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new OCLNodeLIRBuilder(graph, lirGen);
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
        TornadoInternalError.unimplemented();
        return null;
    }

    public void reset() {
        getDeviceContext().reset();
        codeCache.reset();
    }

}
