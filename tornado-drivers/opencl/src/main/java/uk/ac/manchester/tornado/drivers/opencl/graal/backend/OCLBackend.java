/*
 * Copyright (c) 2020-2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.graal.backend;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.isHalfFloat;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.ENABLE_EXCEPTIONS;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VIRTUAL_DEVICE_ENABLED;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
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
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.SuitesProvider;
import jdk.graal.compiler.phases.util.Providers;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.common.code.CodeUtil;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.common.utils.BackendDeopt;
import uk.ac.manchester.tornado.drivers.opencl.OCLBackendImpl;
import uk.ac.manchester.tornado.drivers.opencl.OCLDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDescription;
import uk.ac.manchester.tornado.drivers.opencl.OCLTargetDevice;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLArchitecture;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLCodeProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLFrameContext;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLFrameMap;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLFrameMapBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLProviders;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLSuitesProvider;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLUtils;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssembler;
import uk.ac.manchester.tornado.drivers.opencl.graal.asm.OCLAssemblerConstants;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLDataBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerationResult;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLLIRGenerator;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLNodeMatchRules;
import uk.ac.manchester.tornado.drivers.opencl.graal.compiler.OCLReferenceMapBuilder;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.FPGAWorkGroupSizeNode;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.OCLTokens;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.graal.backend.XPUBackend;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class OCLBackend extends XPUBackend<OCLProviders> implements FrameMap.ReferenceMapBuilderFactory {

    final OptionValues options;

    final OCLTargetDescription target;
    final OCLArchitecture architecture;
    final OCLDeviceContextInterface deviceContext;
    final OCLCodeProvider codeCache;
    private boolean backEndInitialized;

    public OCLBackend(OptionValues options, Providers providers, OCLTargetDescription target, OCLCodeProvider codeCache, OCLDeviceContextInterface deviceContext) {
        super(providers);
        this.options = options;
        this.target = target;
        this.codeCache = codeCache;
        this.deviceContext = deviceContext;
        architecture = (OCLArchitecture) target.arch;
    }

    public static boolean isDeviceAnFPGAAccelerator(OCLDeviceContextInterface deviceContext) {
        return deviceContext.isPlatformFPGA();
    }

    @Override
    public String decodeDeopt(long value) {
        return BackendDeopt.decodeDeopt(value, getProviders());
    }

    @Override
    public boolean isInitialised() {
        return backEndInitialized;
    }

    @Override
    public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
        return new OCLReferenceMapBuilder();
    }

    /**
     * It allocated the extra internal buffers that are used by this backend (constant and atomic).
     */
    @Override
    public void allocateTornadoVMBuffersOnDevice() {
        deviceContext.getMemoryManager().allocateDeviceMemoryRegions();
    }

    /**
     * We explore all devices in driver 0;
     *
     * @return int[]
     */
    public int[] getDriverAndDevice() {
        int numDev = TornadoCoreRuntime.getTornadoRuntime().getBackend(OCLBackendImpl.class).getNumDevices();
        int deviceIndex = 0;
        for (int i = 0; i < numDev; i++) {
            TornadoXPUDevice device = TornadoCoreRuntime.getTornadoRuntime().getBackend(OCLBackendImpl.class).getDevice(i);
            OCLTargetDevice dev = (OCLTargetDevice) device.getPhysicalDevice();
            if (dev == deviceContext.getDevice()) {
                deviceIndex = i;
            }
        }
        int driverIndex = TornadoCoreRuntime.getTornadoRuntime().getBackendIndex(OCLBackendImpl.class);
        return new int[] { driverIndex, deviceIndex };
    }

    @Override
    public void init() {
        if (VIRTUAL_DEVICE_ENABLED) {
            backEndInitialized = true;
            return;
        }

        allocateTornadoVMBuffersOnDevice();
        backEndInitialized = true;
    }

    @Override
    public int getMethodIndex() {
        return 0;
    }

    @Override
    public OCLDeviceContextInterface getDeviceContext() {
        return deviceContext;
    }

    protected OCLAssembler createAssembler() {
        return new OCLAssembler(target);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method, TornadoProfiler profiler) {
        emitCode((OCLCompilationResultBuilder) crb, lir, method, profiler);
    }

    public void emitCode(OCLCompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method, TornadoProfiler profiler) {
        TaskDataContext taskMetaData = crb.getTaskMetaData();
        profiler.start(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId());

        final OCLAssembler asm = (OCLAssembler) crb.asm;
        emitPrologue(crb, asm, method, lir);
        crb.emit(lir);
        emitEpilogue(asm);

        profiler.stop(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId());
        profiler.sum(ProfilerType.TOTAL_CODE_GENERATION_TIME, profiler.getTaskTimer(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId()));

    }

    private void emitEpilogue(OCLAssembler asm) {
        asm.endScope(" kernel");
    }

    private void addVariableDef(Map<OCLKind, Set<Variable>> kindToVariable, Variable value) {
        if (value != null) {

            if (!(value.getPlatformKind() instanceof OCLKind)) {
                shouldNotReachHere();
            }

            OCLKind oclKind = (OCLKind) value.getPlatformKind();
            if (oclKind == OCLKind.ILLEGAL) {
                shouldNotReachHere();
            }

            if (!kindToVariable.containsKey(oclKind)) {
                kindToVariable.put(oclKind, new HashSet<>());
            }

            final Set<Variable> varList = kindToVariable.get(oclKind);
            varList.add(value);
        }
    }

    private void emitVariableDefs(OCLCompilationResultBuilder crb, OCLAssembler asm, LIR lir) {
        Map<OCLKind, Set<Variable>> kindToVariable = new HashMap<>();
        final int expectedVariables = lir.numVariables();
        final AtomicInteger variableCount = new AtomicInteger();

        for (int b : lir.linearScanOrder()) {
            for (LIRInstruction lirInstruction : lir.getLIRforBlock(lir.getBlockById(b))) {

                lirInstruction.forEachOutput((instruction, value, mode, flags) -> {
                    if (value instanceof Variable) {
                        Variable variable = (Variable) value;
                        if (variable.toString() != null) {
                            addVariableDef(kindToVariable, variable);
                            variableCount.incrementAndGet();
                        }
                    }
                    return value;
                });
            }
        }

        Logger.traceCodeGen(Logger.BACKEND.OpenCL, "found %d variable, expected (%d)", variableCount.get(), expectedVariables);

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

    private void emitDebugKernelArgs(OCLAssembler asm, ResolvedJavaMethod method) {
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

    private void emitPrologue(OCLCompilationResultBuilder crb, OCLAssembler asm, ResolvedJavaMethod method, LIR lir) {

        String methodName = crb.compilationResult.getName();
        final CallingConvention incomingArguments = CodeUtil.getCallingConvention(codeCache, HotSpotCallingConventionType.JavaCallee, method);

        if (crb.isKernel()) {
            /*
             * BUG There is a bug on some OpenCL devices which requires us to insert an
             * extra OpenCL buffer into the kernel arguments. This has the effect of
             * shifting the devices address mappings, which allows us to avoid the heap
             * starting at address 0x0. (I assume that this is an interesting case that
             * leads to a few issues.) Iris Pro is the only culprit at the moment.
             */
            final ControlFlowGraph cfg = (ControlFlowGraph) lir.getControlFlowGraph();
            if (cfg.getStartBlock().getEndNode().predecessor() instanceof FPGAWorkGroupSizeNode) {
                FPGAWorkGroupSizeNode fpgaNode = (FPGAWorkGroupSizeNode) (cfg.getStartBlock().getEndNode().predecessor());
                String attribute = fpgaNode.createThreadAttribute();

                asm.emitSymbol(attribute);
                asm.emitLine("");
            }

            asm.emit("%s void %s(%s", OCLAssemblerConstants.KERNEL_MODIFIER, methodName, architecture.getABI());
            emitMethodParameters(asm, method, incomingArguments, true);
            asm.emitLine(")");

            asm.beginScope();
            emitVariableDefs(crb, asm, lir);

            if (TornadoOptions.DEBUG_KERNEL_ARGS && !method.getDeclaringClass().getUnqualifiedName().equalsIgnoreCase(this.getClass().getSimpleName())) {
                emitDebugKernelArgs(asm, method);
            }

            if (ENABLE_EXCEPTIONS) {
                asm.emitStmt("if(slots[0] != 0) return");
            }
            asm.eol();
        } else {

            methodName = OCLUtils.makeMethodName(method);

            final JavaKind returnKind = method.getSignature().getReturnKind();
            String returnStr;
            if (returnKind == JavaKind.Void) {
                returnStr = "void";
            } else {
                final ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(method.getDeclaringClass());
                OCLKind returnOclKind = (returnType.getAnnotation(Vector.class) == null) ? ((OCLTargetDescription) getTarget()).getOCLKind(returnKind) : OCLKind.fromResolvedJavaType(returnType);
                returnStr = returnOclKind.toString();
            }
            asm.emit("%s %s(%s", returnStr, methodName, architecture.getABI());

            emitMethodParameters(asm, method, incomingArguments, false);
            asm.emit(")");
            asm.eol();
            asm.beginScope();
            emitVariableDefs(crb, asm, lir);
            asm.eol();
        }
    }

    private String getParameterName(Local local) {
        String parameterName = local.getName();
        if (OCLTokens.openCLTokens.contains(parameterName)) {
            parameterName = "_" + parameterName;
        }
        return parameterName;
    }

    private void emitMethodParameters(OCLAssembler asm, ResolvedJavaMethod method, CallingConvention incomingArguments, boolean isKernel) {
        final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);

        for (int i = 0; i < incomingArguments.getArgumentCount(); i++) {
            var javaType = locals[i].getType();
            var javaKind = CodeUtil.convertJavaKind(javaType);
            if (isKernel) {
                if (javaKind.isPrimitive() || isHalfFloat(javaType)) {
                    final AllocatableValue param = incomingArguments.getArgument(i);
                    OCLKind kind = (OCLKind) param.getPlatformKind();
                    asm.emit(", ");
                    asm.emit("__private %s %s", kind.toString(), locals[i].getName());
                } else {
                    // Skip the kernel context object
                    if (javaType.toJavaName().equals(KernelContext.class.getName())) {
                        continue;
                    }
                    // Skip atomic integers
                    if (javaType.toJavaName().equals(AtomicInteger.class.getName())) {
                        continue;
                    }
                    asm.emit(", ");
                    String parameterName = getParameterName(locals[i]);
                    asm.emit("__global %s *%s", "uchar", parameterName);
                }
            } else {
                final AllocatableValue param = incomingArguments.getArgument(i);
                OCLKind oclKind = (OCLKind) param.getPlatformKind();
                if (javaKind.isObject()) {
                    OCLKind tmpKind = OCLKind.resolveToVectorKind(javaType.resolve(method.getDeclaringClass()));
                    if (tmpKind != OCLKind.ILLEGAL) {
                        oclKind = tmpKind;
                    }
                }
                guarantee(oclKind != OCLKind.ILLEGAL, "illegal type for %s", param.getPlatformKind());
                asm.emit(", ");
                asm.emit("%s %s", oclKind.toString(), locals[i].getName());
            }
        }
    }

    @Override
    public OCLSuitesProvider getTornadoSuites() {
        return ((OCLProviders) getProviders()).getSuitesProvider();
    }

    public OCLCompilationResultBuilder newCompilationResultBuilder(FrameMap frameMap, OCLCompilationResult compilationResult, boolean isKernel, boolean isParallel, LIR lir) {
        OCLAssembler asm = createAssembler();
        OCLFrameContext frameContext = new OCLFrameContext();
        DataBuilder dataBuilder = new OCLDataBuilder();
        OCLCompilationResultBuilder crb = new OCLCompilationResultBuilder(getProviders(), frameMap, asm, dataBuilder, frameContext, options, getDebugContext(), compilationResult, lir);
        crb.setKernel(isKernel);
        crb.setParallel(isParallel);
        crb.setDeviceContext(deviceContext);
        return crb;
    }

    private FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new OCLFrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new OCLFrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfig);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier identifier, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig) {
        return new OCLLIRGenerationResult(identifier, lir, frameMapBuilder, registerAllocationConfig, new CallingConvention(0, null, (AllocatableValue[]) null));
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
        return String.format("Backend: arch=%s, device=%s", architecture.getName(), deviceContext.getDevice().getDeviceName());
    }

    @Override
    public OCLCodeProvider getCodeCache() {
        return codeCache;
    }

    @Override
    public SuitesProvider getSuites() {
        unimplemented("Get suites method in OCLBackend not implemented yet.");
        return null;
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo, Object stub) {
        return null;
    }

    public void reset(long executionPlanId) {
        getDeviceContext().reset(executionPlanId);
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod rjm, CompilationRequest cr, CompilationResult cr1, boolean isDefault, OptionValues options) {
        unimplemented("Create compiled code method in OCLBackend not implemented yet.");
        return null;
    }

}
