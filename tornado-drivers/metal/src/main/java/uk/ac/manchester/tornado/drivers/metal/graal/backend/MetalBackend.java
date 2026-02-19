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
package uk.ac.manchester.tornado.drivers.metal.graal.backend;

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

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
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
import org.graalvm.compiler.phases.util.Providers;

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
import uk.ac.manchester.tornado.drivers.metal.MetalBackendImpl;
import uk.ac.manchester.tornado.drivers.metal.MetalDeviceContextInterface;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDescription;
import uk.ac.manchester.tornado.drivers.metal.MetalTargetDevice;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalArchitecture;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalCodeProvider;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalFrameContext;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalFrameMap;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalFrameMapBuilder;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalProviders;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalSuitesProvider;
import uk.ac.manchester.tornado.drivers.metal.graal.MetalUtils;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssembler;
import uk.ac.manchester.tornado.drivers.metal.graal.asm.MetalAssemblerConstants;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResult;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalDataBuilder;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalLIRGenerationResult;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalLIRGenerator;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalNodeMatchRules;
import uk.ac.manchester.tornado.drivers.metal.graal.compiler.MetalReferenceMapBuilder;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;
import uk.ac.manchester.tornado.drivers.metal.graal.nodes.FPGAWorkGroupSizeNode;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.MetalTokens;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.graal.backend.XPUBackend;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class MetalBackend extends XPUBackend<MetalProviders> implements FrameMap.ReferenceMapBuilderFactory {

    final OptionValues options;

    final MetalTargetDescription target;
    final MetalArchitecture architecture;
    final MetalDeviceContextInterface deviceContext;
    final MetalCodeProvider codeCache;
    private boolean backEndInitialized;

    public MetalBackend(OptionValues options, Providers providers, MetalTargetDescription target, MetalCodeProvider codeCache, MetalDeviceContextInterface deviceContext) {
        super(providers);
        this.options = options;
        this.target = target;
        this.codeCache = codeCache;
        this.deviceContext = deviceContext;
        architecture = (MetalArchitecture) target.arch;
    }

    public static boolean isDeviceAnFPGAAccelerator(MetalDeviceContextInterface deviceContext) {
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
        return new MetalReferenceMapBuilder();
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
        int numDev = TornadoCoreRuntime.getTornadoRuntime().getBackend(MetalBackendImpl.class).getNumDevices();
        int deviceIndex = 0;
        for (int i = 0; i < numDev; i++) {
            TornadoXPUDevice device = TornadoCoreRuntime.getTornadoRuntime().getBackend(MetalBackendImpl.class).getDevice(i);
            MetalTargetDevice dev = (MetalTargetDevice) device.getPhysicalDevice();
            if (dev == deviceContext.getDevice()) {
                deviceIndex = i;
            }
        }
        int driverIndex = TornadoCoreRuntime.getTornadoRuntime().getBackendIndex(MetalBackendImpl.class);
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
    public MetalDeviceContextInterface getDeviceContext() {
        return deviceContext;
    }

    protected MetalAssembler createAssembler() {
        return new MetalAssembler(target);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method, TornadoProfiler profiler) {
        emitCode((MetalCompilationResultBuilder) crb, lir, method, profiler);
    }

    public void emitCode(MetalCompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method, TornadoProfiler profiler) {
        TaskDataContext taskMetaData = crb.getTaskMetaData();
        profiler.start(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId());

        final MetalAssembler asm = (MetalAssembler) crb.asm;
        emitPrologue(crb, asm, method, lir);
        crb.emit(lir);
        emitEpilogue(asm);

        profiler.stop(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId());
        profiler.sum(ProfilerType.TOTAL_CODE_GENERATION_TIME, profiler.getTaskTimer(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId()));

    }

    private void emitEpilogue(MetalAssembler asm) {
        asm.endScope(" kernel");
    }

    private void addVariableDef(Map<MetalKind, Set<Variable>> kindToVariable, Variable value) {
        if (value != null) {

            if (!(value.getPlatformKind() instanceof MetalKind)) {
                shouldNotReachHere();
            }

            MetalKind oclKind = (MetalKind) value.getPlatformKind();
            if (oclKind == MetalKind.ILLEGAL) {
                shouldNotReachHere();
            }

            if (!kindToVariable.containsKey(oclKind)) {
                kindToVariable.put(oclKind, new HashSet<>());
            }

            final Set<Variable> varList = kindToVariable.get(oclKind);
            varList.add(value);
        }
    }

    private void emitVariableDefs(MetalCompilationResultBuilder crb, MetalAssembler asm, LIR lir) {
        Map<MetalKind, Set<Variable>> kindToVariable = new HashMap<>();
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

        Logger.traceCodeGen(Logger.BACKEND.Metal, "found %d variable, expected (%d)", variableCount.get(), expectedVariables);

        for (MetalKind type : kindToVariable.keySet()) {
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

    private void emitDebugKernelArgs(MetalAssembler asm, ResolvedJavaMethod method) {
        // MSL does not support printf - emit debug args as comment
        asm.emitLine("// debug kernel args (printf not supported in MSL)");
    }

    private void emitPrologue(MetalCompilationResultBuilder crb, MetalAssembler asm, ResolvedJavaMethod method, LIR lir) {

        String methodName = crb.compilationResult.getName();
        final CallingConvention incomingArguments = CodeUtil.getCallingConvention(codeCache, HotSpotCallingConventionType.JavaCallee, method);

        if (crb.isKernel()) {
            /*
             * BUG There is a bug on some Metal devices which requires us to insert an
             * extra Metal buffer into the kernel arguments. This has the effect of
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

            asm.emit("%s void %s(%s", MetalAssemblerConstants.KERNEL_MODIFIER, methodName, architecture.getABI());
            int nextBufferIdx = emitMethodParameters(asm, method, incomingArguments, true);
            // Add a Metal system value for thread position and a small device-side
            // uint array '_global_sizes' which holds the global work sizes [x,y,z].
            // The native enqueue will allocate and bind a 3-element MTLBuffer for this.
            // Explicit [[buffer(N)]] ensures the index matches the Java-side arg index.
            asm.emit(", uint3 _thread_position_in_grid [[thread_position_in_grid]], device uint* _global_sizes [[buffer(%d)]]", nextBufferIdx);
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

            methodName = MetalUtils.makeMethodName(method);

            final JavaKind returnKind = method.getSignature().getReturnKind();
            String returnStr;
            if (returnKind == JavaKind.Void) {
                returnStr = "void";
            } else {
                final ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(method.getDeclaringClass());
                MetalKind returnOclKind = (returnType.getAnnotation(Vector.class) == null) ? ((MetalTargetDescription) getTarget()).getMetalKind(returnKind) : MetalKind.fromResolvedJavaType(returnType);
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
        if (MetalTokens.metalTokens.contains(parameterName)) {
            parameterName = "_" + parameterName;
        }
        return parameterName;
    }

    /**
     * Emits kernel or function parameter declarations.
     * Returns the next available Metal buffer index (only meaningful for kernel mode).
     */
    private int emitMethodParameters(MetalAssembler asm, ResolvedJavaMethod method, CallingConvention incomingArguments, boolean isKernel) {
        final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);
        // Metal buffer index starts after the ABI registers (kernelContext, constantRegion, localRegion, atomics)
        int metalArgIndex = MetalArchitecture.abiRegisters.length;

        for (int i = 0; i < incomingArguments.getArgumentCount(); i++) {
            var javaType = locals[i].getType();
            var javaKind = CodeUtil.convertJavaKind(javaType);
            if (isKernel) {
                    if (javaKind.isPrimitive() || isHalfFloat(javaType)) {
                    final AllocatableValue param = incomingArguments.getArgument(i);
                    MetalKind kind = (MetalKind) param.getPlatformKind();
                    asm.emit(", ");
                    // MSL kernel scalar parameters must use constant address space reference
                    // (bound via setBytes: on the encoder side)
                    String paramName = getParameterName(locals[i]);
                    asm.emit("constant %s& %s [[buffer(%d)]]", kind.toString(), paramName, metalArgIndex);
                    metalArgIndex++;
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
                    // Use centralized constant for the global memory qualifier instead of a hard-coded literal
                    asm.emit("%s %s *%s [[buffer(%d)]]", MetalAssemblerConstants.GLOBAL_MEM_MODIFIER, "uchar", parameterName, metalArgIndex);
                    metalArgIndex++;
                }
            } else {
                final AllocatableValue param = incomingArguments.getArgument(i);
                MetalKind oclKind = (MetalKind) param.getPlatformKind();
                if (javaKind.isObject()) {
                    MetalKind tmpKind = MetalKind.resolveToVectorKind(javaType.resolve(method.getDeclaringClass()));
                    if (tmpKind != MetalKind.ILLEGAL) {
                        oclKind = tmpKind;
                    }
                }
                guarantee(oclKind != MetalKind.ILLEGAL, "illegal type for %s", param.getPlatformKind());
                asm.emit(", ");
                asm.emit("%s %s", oclKind.toString(), locals[i].getName());
            }
        }
        return metalArgIndex;
    }

    @Override
    public MetalSuitesProvider getTornadoSuites() {
        return ((MetalProviders) getProviders()).getSuitesProvider();
    }

    public MetalCompilationResultBuilder newCompilationResultBuilder(FrameMap frameMap, MetalCompilationResult compilationResult, boolean isKernel, boolean isParallel, LIR lir) {
        MetalAssembler asm = createAssembler();
        MetalFrameContext frameContext = new MetalFrameContext();
        DataBuilder dataBuilder = new MetalDataBuilder();
        MetalCompilationResultBuilder crb = new MetalCompilationResultBuilder(getProviders(), frameMap, asm, dataBuilder, frameContext, options, getDebugContext(), compilationResult, lir);
        crb.setKernel(isKernel);
        crb.setParallel(isParallel);
        crb.setDeviceContext(deviceContext);
        return crb;
    }

    private FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new MetalFrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new MetalFrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfig);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier identifier, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig) {
        return new MetalLIRGenerationResult(identifier, lir, frameMapBuilder, registerAllocationConfig, new CallingConvention(0, null, (AllocatableValue[]) null));
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenResult) {
        return new MetalLIRGenerator(getProviders(), lirGenResult);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new MetalNodeLIRBuilder(graph, lirGen, new MetalNodeMatchRules(lirGen));
    }

    @Override
    public String toString() {
        return String.format("Backend: arch=%s, device=%s", architecture.getName(), deviceContext.getDevice().getDeviceName());
    }

    @Override
    public MetalCodeProvider getCodeCache() {
        return codeCache;
    }

    @Override
    public SuitesProvider getSuites() {
        unimplemented("Get suites method in MetalBackend not implemented yet.");
        return null;
    }

    public void reset(long executionPlanId) {
        getDeviceContext().reset(executionPlanId);
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod rjm, CompilationRequest cr, CompilationResult cr1, boolean isDefault, OptionValues options) {
        unimplemented("Create compiled code method in MetalBackend not implemented yet.");
        return null;
    }

}
