/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.backend;

import static uk.ac.manchester.tornado.common.RuntimeUtilities.humanReadableByteCount;
import static uk.ac.manchester.tornado.common.Tornado.DEBUG_KERNEL_ARGS;
import static uk.ac.manchester.tornado.common.Tornado.error;
import static uk.ac.manchester.tornado.common.Tornado.info;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.common.exceptions.TornadoInternalError.unimplemented;
import uk.ac.manchester.tornado.drivers.opencl.*;
import uk.ac.manchester.tornado.drivers.opencl.exceptions.*;
import static uk.ac.manchester.tornado.graal.compiler.TornadoCodeGenerator.trace;
import static uk.ac.manchester.tornado.runtime.TornadoRuntime.getTornadoRuntime;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
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
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.util.EconomicSet;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.Vector;
import uk.ac.manchester.tornado.api.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.api.meta.TaskMetaData;
import uk.ac.manchester.tornado.common.Tornado;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLCodeProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLCodeUtil;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLFrameContext;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLFrameMap;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLFrameMapBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLSuitesProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLUtils;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompiler;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLDataBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLNodeMatchRules;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLReferenceMapBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.mm.OCLByteBuffer;
import uk.ac.manchester.tornado.graal.backend.TornadoBackend;
import uk.ac.manchester.tornado.lang.CompilerInternals;

public class OCLBackend extends TornadoBackend<OCLProviders> implements FrameMap.ReferenceMapBuilderFactory {

    public final static boolean SHOW_OPENCL = Boolean.parseBoolean(System.getProperty("tornado.opencl.print", "False"));
    public final static String OPENCL_PATH = System.getProperty("tornado.opencl.path", "./opencl");

    @Override
    public OCLTargetDescription getTarget() {
        return target;
    }

    final OptionValues options;

    final OCLTargetDescription target;
    final OCLArchitecture architecture;
    final OCLContext openclContext;
    final OCLDeviceContext deviceContext;
    final OCLCodeProvider codeCache;
    OCLInstalledCode lookupCode;
    final AtomicInteger id = new AtomicInteger(0);

    final ScheduleMetaData scheduleMeta;

    public OCLBackend(OptionValues options, Providers providers, OCLTargetDescription target, OCLCodeProvider codeCache, OCLContext openclContext, OCLDeviceContext deviceContext) {
        super(providers);
        this.options = options;
        this.target = target;
        this.codeCache = codeCache;
        this.openclContext = openclContext;
        this.deviceContext = deviceContext;
        architecture = (OCLArchitecture) target.arch;
        scheduleMeta = new ScheduleMetaData("oclbackend");

    }

    public SnippetReflectionProvider getSnippetReflection() {
        return ((OCLProviders) this.getProviders()).getSnippetReflection();
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
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        return new RegisterAllocationConfig(registerConfig, allocationRestrictedTo);
    }

    @Override
    public EconomicSet<Register> translateToCallerRegisters(EconomicSet<Register> calleeRegisters) {
        unimplemented();
        return null;
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

    public long readHeapBaseAddress(TaskMetaData meta) {
        final OCLByteBuffer bb = deviceContext.getMemoryManager().getSubBuffer(0, 16);

        bb.putLong(0);
        bb.putLong(0);

        int task = lookupCode.executeTask(bb, meta);
        lookupCode.readValue(bb, meta, task);
        lookupCode.resolveEvent(bb, meta, task);

        final long address = bb.getLong(0);
        Tornado.info("Heap address @ 0x%x on %s ", address, deviceContext.getDevice().getName());
        return address;
    }

    /**
     * It allocates the smallest of the requested heap size or the max global
     * memory size.
     */
    public void allocateHeapMemoryOnDevice() {

        final long memorySize = Math.min(DEFAULT_HEAP_ALLOCATION, deviceContext.getDevice().getMaxAllocationSize());
        if (memorySize < DEFAULT_HEAP_ALLOCATION) {
            Tornado.info("Unable to allocate %s of heap space - resized to %s", humanReadableByteCount(DEFAULT_HEAP_ALLOCATION, false), humanReadableByteCount(memorySize, false));
        }
        Tornado.info("%s: allocating %s of heap space", deviceContext.getDevice().getName(), humanReadableByteCount(memorySize, false));
        deviceContext.getMemoryManager().allocateRegion(memorySize);
    }

    /*
     * Retrieve the address of the heap on the device
     */
    public TaskMetaData compileLookupBufferKernel() {
        int numKernelParameters = 0;
        TaskMetaData meta = new TaskMetaData(scheduleMeta, "lookupBufferAddress", numKernelParameters);
        OCLCodeCache check = new OCLCodeCache(deviceContext);
        if (deviceContext.isCached("internal", "lookupBufferAddress")) {
            // Option 1) Getting the lookupBufferAddress from the cache
            lookupCode = deviceContext.getCode("internal", "lookupBufferAddress");
        } else if (check.getBinStatus() && check.getFPGABinDir() != null) {
            // Option 2) Loading precompiled lookupBufferAddress kernel FPGA
            // binary
            Path lookupPath = Paths.get(check.getFPGABinDir());
            lookupCode = check.installEntryPointForBinaryForFPGAs(lookupPath, "lookupBufferAddress");
        } else {
            // Option 3) Compiling lookupBufferAddress kernel at runtime
            OCLCompilationResult result = OCLCompiler.compileCodeForDevice(getTornadoRuntime().resolveMethod(getLookupMethod()), null, meta, (OCLProviders) getProviders(), this);
            lookupCode = deviceContext.installCode(result);
        }
        return meta;
    }

    public void runAndReadLookUpKernel(TaskMetaData meta) {
        deviceContext.getMemoryManager().init(this, readHeapBaseAddress(meta));
    }

    public void init() {
        allocateHeapMemoryOnDevice();
        TaskMetaData meta = compileLookupBufferKernel();
        runAndReadLookUpKernel(meta);
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
                // return;
            }

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
                asm.emitValue(crb, var);
                asm.emit(", ");
            }
            asm.emitByte(';', asm.position() - 2);
            asm.eol();
        }

    }

    private void emitPrologue(OCLCompilationResultBuilder crb, OCLAssembler asm, ResolvedJavaMethod method, LIR lir) {

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

            asm.emitLine("%s void %s(%s%s)", OCLAssemblerConstants.KERNEL_MODIFIER, methodName, bumpBuffer, architecture.getABI());
            asm.beginScope();
            emitVariableDefs(crb, asm, lir);
            asm.eol();
            asm.emitStmt("%s ulong *%s = (%s ulong *) &%s[%s]", OCLAssemblerConstants.GLOBAL_MEM_MODIFIER, OCLAssemblerConstants.FRAME_REF_NAME, OCLAssemblerConstants.GLOBAL_MEM_MODIFIER,
                    OCLAssemblerConstants.HEAP_REF_NAME, OCLAssemblerConstants.FRAME_BASE_NAME);
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

            final CallingConvention incomingArguments = OCLCodeUtil.getCallingConvention(codeCache, HotSpotCallingConventionType.JavaCallee, method, false);
            methodName = OCLUtils.makeMethodName(method);

            final JavaKind returnKind = method.getSignature().getReturnKind();
            String returnStr = "<unknown>";
            if (returnKind == JavaKind.Void) {
                returnStr = "void";
            } else {
                final ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(method.getDeclaringClass());
                OCLKind returnOclKind = (returnType.getAnnotation(Vector.class) == null) ? getTarget().getOCLKind(returnKind) : OCLKind.fromResolvedJavaType(returnType);
                returnStr = returnOclKind.toString();
            }
            // getTarget().getLIRKind(returnKind);
            asm.emit("%s %s(%s", returnStr, methodName, architecture.getABI());

            final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);
            final Value[] params = new Value[incomingArguments.getArgumentCount()];

            if (params.length > 0) {
                asm.emit(", ");
            }

            for (int i = 0; i < params.length; i++) {
                final AllocatableValue param = incomingArguments.getArgument(i);
                OCLKind oclKind = (OCLKind) param.getPlatformKind();
                if (locals[i].getType().getJavaKind().isObject()) {
                    OCLKind tmpKind = OCLKind.resolveToVectorKind(locals[i].getType().resolve(method.getDeclaringClass()));
                    if (tmpKind != OCLKind.ILLEGAL) {
                        oclKind = tmpKind;
                    }
                }
                guarantee(oclKind != OCLKind.ILLEGAL, "illegal type for %s", param.getPlatformKind());
                asm.emit("%s %s", oclKind.toString(), locals[i].getName());
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
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRes, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        return newCompilationResultBuilder(lirGenRes, frameMap, (OCLCompilationResult) compilationResult, factory, false);
    }

    public OCLCompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRes, FrameMap frameMap, OCLCompilationResult compilationResult, CompilationResultBuilderFactory factory,
            boolean isKernel) {

        OCLAssembler asm = createAssembler(frameMap);
        OCLFrameContext frameContext = new OCLFrameContext();
        DataBuilder dataBuilder = new OCLDataBuilder();
        OCLCompilationResultBuilder crb = new OCLCompilationResultBuilder(codeCache, getForeignCalls(), frameMap, asm, dataBuilder, frameContext, compilationResult, options);
        crb.setKernel(isKernel);

        return crb;
    }

    @Override
    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new OCLFrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new OCLFrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfig);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier identifier, LIR lir, FrameMapBuilder frameMapBuilder, StructuredGraph graph, Object stub) {
        return new OCLLIRGenerationResult(identifier, lir, frameMapBuilder, new CallingConvention(0, null, (AllocatableValue[]) null));
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenResult) {
        return new OCLLIRGenerator(getProviders(), lirGenResult);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new OCLNodeLIRBuilder(graph, lirGen, new OCLNodeMatchRules(lirGen));
    }

    @Override
    public String toString() {
        return String.format("Backend: arch=%s, device=%s", architecture.getName(), deviceContext.getDevice().getName());
    }

    @Override
    public OCLCodeProvider getCodeCache() {
        return codeCache;
    }

    @Override
    public SuitesProvider getSuites() {
        unimplemented();
        return null;
    }

    public void reset() {
        getDeviceContext().reset();
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod rjm, CompilationRequest cr, CompilationResult cr1) {
        unimplemented();
        return null;
    }
}
