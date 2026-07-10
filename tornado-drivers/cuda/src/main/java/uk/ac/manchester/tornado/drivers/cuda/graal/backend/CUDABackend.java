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
package uk.ac.manchester.tornado.drivers.cuda.graal.backend;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.isHalfFloat;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.ENABLE_EXCEPTIONS;
import static uk.ac.manchester.tornado.runtime.common.TornadoOptions.VIRTUAL_DEVICE_ENABLED;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import tornado.graal.compiler.code.CompilationResult;
import tornado.graal.compiler.core.common.CompilationIdentifier;
import tornado.graal.compiler.core.common.LIRKind;
import tornado.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import tornado.graal.compiler.lir.LIR;
import tornado.graal.compiler.lir.LIRInstruction;
import tornado.graal.compiler.lir.Variable;
import tornado.graal.compiler.lir.asm.CompilationResultBuilder;
import tornado.graal.compiler.lir.asm.DataBuilder;
import tornado.graal.compiler.lir.framemap.FrameMap;
import tornado.graal.compiler.lir.framemap.FrameMapBuilder;
import tornado.graal.compiler.lir.framemap.ReferenceMapBuilder;
import tornado.graal.compiler.lir.gen.LIRGenerationResult;
import tornado.graal.compiler.lir.gen.LIRGeneratorTool;
import tornado.graal.compiler.nodes.StructuredGraph;
import tornado.graal.compiler.nodes.cfg.ControlFlowGraph;
import tornado.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import tornado.graal.compiler.options.OptionValues;
import tornado.graal.compiler.phases.tiers.SuitesProvider;
import tornado.graal.compiler.phases.util.Providers;

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
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.common.code.CodeUtil;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.common.utils.BackendDeopt;
import uk.ac.manchester.tornado.drivers.cuda.CUDABackendImpl;
import uk.ac.manchester.tornado.drivers.cuda.CUDADeviceContextInterface;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDescription;
import uk.ac.manchester.tornado.drivers.cuda.CUDATargetDevice;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAArchitecture;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDACodeProvider;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAFrameContext;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAFrameMap;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAFrameMapBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAProviders;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDASuitesProvider;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAUtils;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssembler;
import uk.ac.manchester.tornado.drivers.cuda.graal.asm.CUDAAssemblerConstants;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDADataBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDALIRGenerationResult;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDALIRGenerator;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDANodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDANodeMatchRules;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDAReferenceMapBuilder;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDAKind;
import uk.ac.manchester.tornado.drivers.cuda.graal.lir.CUDALIRStmt;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.FPGAWorkGroupSizeNode;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.common.CUDATokens;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.common.TornadoXPUDevice;
import uk.ac.manchester.tornado.runtime.graal.backend.XPUBackend;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class CUDABackend extends XPUBackend<CUDAProviders> implements FrameMap.ReferenceMapBuilderFactory {

    final OptionValues options;

    final CUDATargetDescription target;
    final CUDAArchitecture architecture;
    final CUDADeviceContextInterface deviceContext;
    final CUDACodeProvider codeCache;
    private boolean backEndInitialized;

    public CUDABackend(OptionValues options, Providers providers, CUDATargetDescription target, CUDACodeProvider codeCache, CUDADeviceContextInterface deviceContext) {
        super(providers);
        this.options = options;
        this.target = target;
        this.codeCache = codeCache;
        this.deviceContext = deviceContext;
        architecture = (CUDAArchitecture) target.arch;
    }

    public static boolean isDeviceAnFPGAAccelerator(CUDADeviceContextInterface deviceContext) {
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
        return new CUDAReferenceMapBuilder();
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
        int numDev = TornadoCoreRuntime.getTornadoRuntime().getBackend(CUDABackendImpl.class).getNumDevices();
        int deviceIndex = 0;
        for (int i = 0; i < numDev; i++) {
            TornadoXPUDevice device = TornadoCoreRuntime.getTornadoRuntime().getBackend(CUDABackendImpl.class).getDevice(i);
            CUDATargetDevice dev = (CUDATargetDevice) device.getPhysicalDevice();
            if (dev == deviceContext.getDevice()) {
                deviceIndex = i;
            }
        }
        int driverIndex = TornadoCoreRuntime.getTornadoRuntime().getBackendIndex(CUDABackendImpl.class);
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
    public CUDADeviceContextInterface getDeviceContext() {
        return deviceContext;
    }

    protected CUDAAssembler createAssembler() {
        return new CUDAAssembler(target);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method, TornadoProfiler profiler) {
        emitCode((CUDACompilationResultBuilder) crb, lir, method, profiler);
    }

    public void emitCode(CUDACompilationResultBuilder crb, LIR lir, ResolvedJavaMethod method, TornadoProfiler profiler) {
        TaskDataContext taskMetaData = crb.getTaskMetaData();
        profiler.start(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId());

        final CUDAAssembler asm = (CUDAAssembler) crb.asm;
        emitPrologue(crb, asm, method, lir);
        crb.emit(lir);
        emitEpilogue(asm);

        profiler.stop(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId());
        profiler.sum(ProfilerType.TOTAL_CODE_GENERATION_TIME, profiler.getTaskTimer(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId()));

    }

    private void emitEpilogue(CUDAAssembler asm) {
        asm.endScope(" kernel");
    }

    private void addVariableDef(Map<CUDAKind, Set<Variable>> kindToVariable, Variable value) {
        if (value != null) {

            if (!(value.getPlatformKind() instanceof CUDAKind)) {
                shouldNotReachHere();
            }

            CUDAKind oclKind = (CUDAKind) value.getPlatformKind();
            if (oclKind == CUDAKind.ILLEGAL) {
                shouldNotReachHere();
            }

            if (!kindToVariable.containsKey(oclKind)) {
                kindToVariable.put(oclKind, new HashSet<>());
            }

            final Set<Variable> varList = kindToVariable.get(oclKind);
            varList.add(value);
        }
    }

    private void emitVariableDefs(CUDACompilationResultBuilder crb, CUDAAssembler asm, LIR lir) {
        Map<CUDAKind, Set<Variable>> kindToVariable = new HashMap<>();
        Map<Variable, CUDAKind> fragmentPhis = new LinkedHashMap<>();

        final int expectedVariables = lir.numVariables();
        final AtomicInteger variableCount = new AtomicInteger();

        for (int b : lir.linearScanOrder()) {
            for (LIRInstruction lirInstruction : lir.getLIRforBlock(lir.getBlockById(b))) {
                if (lirInstruction instanceof CUDALIRStmt.AssignStmt) {
                    CUDALIRStmt.AssignStmt assign = (CUDALIRStmt.AssignStmt) lirInstruction;
                    Value rhs = assign.getExpr();
                    AllocatableValue lhs = assign.getResult();
                    CUDAKind rhsKind = platformKindOf(rhs);
                    if (rhsKind != null && rhsKind.isMMAFragment() && lhs instanceof Variable) {
                        CUDAKind lhsKind = platformKindOf(lhs);
                        if (lhsKind == null || !lhsKind.isMMAFragment()) {
                            fragmentPhis.putIfAbsent((Variable) lhs, rhsKind);
                        }
                    }
                }

                lirInstruction.forEachOutput((instruction, value, mode, flags) -> {
                    if (value instanceof Variable) {
                        Variable variable = (Variable) value;
                        if (variable.toString() != null) {
                            CUDAKind kind = platformKindOf(variable);
                            if (kind != null && kind.isMMAFragment()) {
                                return value;
                            }
                            addVariableDef(kindToVariable, variable);
                            variableCount.incrementAndGet();
                        }
                    }
                    return value;
                });
            }
        }

        Logger.traceCodeGen(Logger.BACKEND.OpenCL, "found %d variable, expected (%d)", variableCount.get(), expectedVariables);

        for (CUDAKind type : kindToVariable.keySet()) {
            Set<Variable> vars = kindToVariable.get(type);
            vars.removeAll(fragmentPhis.keySet());
            if (vars.isEmpty()) {
                continue;
            }
            asm.indent();
            asm.emit("%s ", type.getCUDATypeName());
            for (Variable var : vars) {
                asm.emitValue(crb, var);
                asm.emit(", ");
            }
            asm.emitByte(';', asm.position() - 2);
            asm.eol();
        }

        // Emit fragment-phi variables as C arrays of the fragment's element type.
        // e.g. "float ul_52[4];" for MMA_FRAG_ACC_F32.
        for (Map.Entry<Variable, CUDAKind> entry : fragmentPhis.entrySet()) {
            Variable frag = entry.getKey();
            CUDAKind fragKind = entry.getValue();
            asm.indent();
            asm.emit("%s ", fragmentElementCType(fragKind));
            asm.emitValue(crb, frag);
            asm.emit("[%d];", fragKind.getVectorLength());
            asm.eol();
        }
    }

    /**
     * Extracts the CUDAKind from a Value's ValueKind, or null if the Value doesn't
     * carry a CUDAKind (constants, illegal, etc.).
     */
    private static CUDAKind platformKindOf(Value v) {
        if (v == null) return null;
        if (!(v.getValueKind() instanceof LIRKind)) return null;
        Object pk = ((LIRKind) v.getValueKind()).getPlatformKind();
        return (pk instanceof CUDAKind) ? (CUDAKind) pk : null;
    }

    /**
     * Element C type for an MMA fragment kind. Fragment lanes are:
     *   MMA_FRAG_ACC_F32 → float
     *   MMA_FRAG_ACC_S32 → int
     *   MMA_FRAG_A_F16 / B_F16 / A_S8 / B_S8 → unsigned int (packed b32)
     * Matches the CUDA C types used by the MMA inline-PTX asm constraints
     * ("=f" for f32, "=r" for b32).
     */
    private static String fragmentElementCType(CUDAKind fragKind) {
        switch (fragKind) {
            case MMA_FRAG_ACC_F32:
                return "float";
            case MMA_FRAG_ACC_S32:
                return "int";
            case MMA_FRAG_A_F16:
            case MMA_FRAG_B_F16:
            case MMA_FRAG_A_S8:
            case MMA_FRAG_B_S8:
                return "unsigned int";
            default:
                throw shouldNotReachHere("not an MMA fragment kind: " + fragKind);
        }
    }

    private void emitDebugKernelArgs(CUDAAssembler asm, ResolvedJavaMethod method) {
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

    private void emitPrologue(CUDACompilationResultBuilder crb, CUDAAssembler asm, ResolvedJavaMethod method, LIR lir) {

        String methodName = crb.compilationResult.getName();
        final CallingConvention incomingArguments = CodeUtil.getCallingConvention(codeCache, HotSpotCallingConventionType.JavaCallee, method);

        if (crb.isKernel()) {
            // Emit the CUDA C preamble (cuda_fp16.h include) once at the top of the
            // kernel, before the kernel signature.
            asm.emit(CUDAPreamble.PREAMBLE);

            /*
             * BUG There is a bug on some CUDADriver devices which requires us to insert an
             * extra CUDADriver buffer into the kernel arguments. This has the effect of
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

            asm.emit("%s void %s(%s", CUDAAssemblerConstants.KERNEL_MODIFIER, methodName, architecture.getABI());
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

            // Non-kernel callees are emitted ahead of the kernel in the same compilation unit, so the
            // cuda_fp16.h include from the kernel prologue lands after them. A __device__ callee compiled from a
            // HalfFloat method (e.g. getFloat32 -> __half2float) would then reference __half2float before the
            // header is included. Emit the preamble here too; cuda_fp16.h's include guard makes the duplicate safe.
            asm.emit(CUDAPreamble.PREAMBLE);

            methodName = CUDAUtils.makeMethodName(method);

            final JavaKind returnKind = method.getSignature().getReturnKind();
            String returnStr;
            if (returnKind == JavaKind.Void) {
                returnStr = "void";
            } else {
                final ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(method.getDeclaringClass());
                CUDAKind returnOclKind = (returnType.getAnnotation(Vector.class) == null) ? ((CUDATargetDescription) getTarget()).getCUDAKind(returnKind) : CUDAKind.fromResolvedJavaType(returnType);
                returnStr = returnOclKind.toString();
            }
            // Non-kernel callees must be __device__ functions; otherwise NVRTC treats
            // them as host functions, which is illegal in JIT mode.
            asm.emit("%s %s %s(%s", CUDAAssemblerConstants.DEVICE_MODIFIER, returnStr, methodName, architecture.getABI());

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
        if (CUDATokens.cudaTokens.contains(parameterName)) {
            parameterName = "_" + parameterName;
        }
        return parameterName;
    }

    private void emitMethodParameters(CUDAAssembler asm, ResolvedJavaMethod method, CallingConvention incomingArguments, boolean isKernel) {
        final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);

        for (int i = 0; i < incomingArguments.getArgumentCount(); i++) {
            var javaType = locals[i].getType();
            var javaKind = CodeUtil.convertJavaKind(javaType);
            if (isKernel) {
                if (javaKind.isPrimitive() || isHalfFloat(javaType)) {
                    final AllocatableValue param = incomingArguments.getArgument(i);
                    CUDAKind kind = (CUDAKind) param.getPlatformKind();
                    asm.emit(", ");
                    // CUDA C primitive kernel params take no address-space qualifier.
                    // Rename through getParameterName so a Java parameter whose name
                    // collides with a CUDA built-in (e.g. 'blockDim', which is a dim3)
                    // does not shadow the built-in inside the kernel body. This must
                    // match the name produced at use sites in CUDAGenTool.
                    asm.emit("%s %s", kind.toString(), getParameterName(locals[i]));
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
                    // CUDA C pointer params take no address-space qualifier (no __global).
                    asm.emit("%s *%s", "unsigned char", parameterName);
                }
            } else {
                final AllocatableValue param = incomingArguments.getArgument(i);
                CUDAKind oclKind = (CUDAKind) param.getPlatformKind();
                if (javaKind.isObject()) {
                    CUDAKind tmpKind = CUDAKind.resolveToVectorKind(javaType.resolve(method.getDeclaringClass()));
                    if (tmpKind != CUDAKind.ILLEGAL) {
                        oclKind = tmpKind;
                    }
                }
                guarantee(oclKind != CUDAKind.ILLEGAL, "illegal type for %s", param.getPlatformKind());
                asm.emit(", ");
                asm.emit("%s %s", oclKind.toString(), getParameterName(locals[i]));
            }
        }
    }

    @Override
    public CUDASuitesProvider getTornadoSuites() {
        return ((CUDAProviders) getProviders()).getSuitesProvider();
    }

    public CUDACompilationResultBuilder newCompilationResultBuilder(FrameMap frameMap, CUDACompilationResult compilationResult, boolean isKernel, boolean isParallel, LIR lir) {
        CUDAAssembler asm = createAssembler();
        CUDAFrameContext frameContext = new CUDAFrameContext();
        DataBuilder dataBuilder = new CUDADataBuilder();
        CUDACompilationResultBuilder crb = new CUDACompilationResultBuilder(getProviders(), frameMap, asm, dataBuilder, frameContext, options, getDebugContext(), compilationResult, lir);
        crb.setKernel(isKernel);
        crb.setParallel(isParallel);
        crb.setDeviceContext(deviceContext);
        return crb;
    }

    private FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new CUDAFrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new CUDAFrameMapBuilder(newFrameMap(registerConfigNonNull), getCodeCache(), registerConfig);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier identifier, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig) {
        return new CUDALIRGenerationResult(identifier, lir, frameMapBuilder, registerAllocationConfig, new CallingConvention(0, null, (AllocatableValue[]) null));
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenResult) {
        return new CUDALIRGenerator(getProviders(), lirGenResult);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new CUDANodeLIRBuilder(graph, lirGen, new CUDANodeMatchRules(lirGen));
    }

    @Override
    public String toString() {
        return String.format("Backend: arch=%s, device=%s", architecture.getName(), deviceContext.getDevice().getDeviceName());
    }

    @Override
    public CUDACodeProvider getCodeCache() {
        return codeCache;
    }

    @Override
    public SuitesProvider getSuites() {
        unimplemented("Get suites method in CUDABackend not implemented yet.");
        return null;
    }

    public void reset(long executionPlanId) {
        getDeviceContext().reset(executionPlanId);
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod rjm, CompilationRequest cr, CompilationResult cr1, boolean isDefault, OptionValues options) {
        unimplemented("Create compiled code method in CUDABackend not implemented yet.");
        return null;
    }

}
