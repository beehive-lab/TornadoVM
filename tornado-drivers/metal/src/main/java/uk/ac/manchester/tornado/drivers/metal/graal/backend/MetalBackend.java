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
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalBinary;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalLIRStmt;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalNullary;
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

            MetalKind metalKind = (MetalKind) value.getPlatformKind();
            if (metalKind == MetalKind.ILLEGAL) {
                shouldNotReachHere();
            }

            if (!kindToVariable.containsKey(metalKind)) {
                kindToVariable.put(metalKind, new HashSet<>());
            }

            final Set<Variable> varList = kindToVariable.get(metalKind);
            varList.add(value);
        }
    }

    /**
     * Returns the Metal element type string for a {@code PRIVATE_*_ARRAY_PTR} opcode,
     * or {@code null} if the opcode is not a private-pointer declaration template.
     */
    private static String resolvePrivatePtrElemType(MetalAssembler.MetalBinaryOp op) {
        if (op == MetalAssembler.MetalBinaryTemplate.PRIVATE_INT_ARRAY_PTR)    return "int";
        if (op == MetalAssembler.MetalBinaryTemplate.PRIVATE_FLOAT_ARRAY_PTR)  return "float";
        if (op == MetalAssembler.MetalBinaryTemplate.PRIVATE_DOUBLE_ARRAY_PTR) return "double";
        if (op == MetalAssembler.MetalBinaryTemplate.PRIVATE_LONG_ARRAY_PTR)   return "long";
        if (op == MetalAssembler.MetalBinaryTemplate.PRIVATE_SHORT_ARRAY_PTR)  return "short";
        if (op == MetalAssembler.MetalBinaryTemplate.PRIVATE_CHAR_ARRAY_PTR)   return "char";
        if (op == MetalAssembler.MetalBinaryTemplate.PRIVATE_BYTE_ARRAY_PTR)   return "char";
        return null;
    }

    private void emitVariableDefs(MetalCompilationResultBuilder crb, MetalAssembler asm, LIR lir) {
        Map<MetalKind, Set<Variable>> kindToVariable = new HashMap<>();
        final int expectedVariables = lir.numVariables();
        final AtomicInteger variableCount = new AtomicInteger();

        // Pass 0a: identify ULONG device-pointer variables via dataflow.
        // Seed: AssignStmt where RHS is a MetalNullary.Parameter containing "(tornado_ptr_t)".
        // Propagate: ADD/SUB of pointer + anything, and simple copies of pointer vars.
        // All other ULONG variables must be declared as "ulong", not "tornado_ptr_t".
        Set<Variable> ulongPointerVars = new HashSet<>();
        for (int b : lir.linearScanOrder()) {
            for (LIRInstruction instr : lir.getLIRforBlock(lir.getBlockById(b))) {
                if (instr instanceof MetalLIRStmt.AssignStmt assign
                        && assign.getResult() instanceof Variable lhsVar
                        && lhsVar.getPlatformKind() == MetalKind.ULONG) {
                    Value rhs = assign.getExpr();
                    if (rhs instanceof MetalNullary.Parameter param
                            && param.toString().contains("(tornado_ptr_t)")) {
                        ulongPointerVars.add(lhsVar);
                    }
                }
            }
        }
        // Propagate pointer-ness (two rounds to handle any backward ordering)
        for (int round = 0; round < 2; round++) {
            for (int b : lir.linearScanOrder()) {
                for (LIRInstruction instr : lir.getLIRforBlock(lir.getBlockById(b))) {
                    Variable lhsVar = null;
                    Value rhsVal = null;
                    if (instr instanceof MetalLIRStmt.AssignStmt assign
                            && assign.getResult() instanceof Variable v
                            && v.getPlatformKind() == MetalKind.ULONG) {
                        lhsVar = v;
                        rhsVal = assign.getExpr();
                    } else if (instr instanceof MetalLIRStmt.MoveStmt move
                            && move.getResult() instanceof Variable v
                            && v.getPlatformKind() == MetalKind.ULONG) {
                        lhsVar = v;
                        rhsVal = move.getExpr();
                    }
                    if (lhsVar == null) continue;
                    if (rhsVal instanceof Variable rhsVar && ulongPointerVars.contains(rhsVar)) {
                        ulongPointerVars.add(lhsVar);
                    } else if (rhsVal instanceof MetalBinary.Expr bin) {
                        MetalAssembler.MetalBinaryOp op = bin.getOpcode();
                        if (op == MetalAssembler.MetalBinaryOp.ADD || op == MetalAssembler.MetalBinaryOp.SUB) {
                            boolean xIsPtr = bin.getX() instanceof Variable xv && ulongPointerVars.contains(xv);
                            boolean yIsPtr = bin.getY() instanceof Variable yv && ulongPointerVars.contains(yv);
                            if (xIsPtr || yIsPtr) {
                                ulongPointerVars.add(lhsVar);
                            }
                        }
                    }
                }
            }
        }

        // Pass 0b: identify ULONG variables that are targets of device-memory loads.
        // These hold integer byte offsets, NOT device pointers. They must be declared as
        // "ulong" so that tornado_ptr_t + ulong (pointer + integer) arithmetic is valid.
        Set<Variable> ulongIntLoadVars = new HashSet<>();
        for (int b : lir.linearScanOrder()) {
            for (LIRInstruction instr : lir.getLIRforBlock(lir.getBlockById(b))) {
                if (instr instanceof MetalLIRStmt.LoadStmt instrL) {
                    AllocatableValue lhs =  instrL.getResult();
                    if (lhs instanceof Variable variableLhs && lhs.getPlatformKind() == MetalKind.ULONG) {
                        ulongIntLoadVars.add(variableLhs);
                    }
                }
            }
        }

        // Pass 1: identify private-pointer variables (generated by FixedArrayNode).
        //
        // "original" private ptrs: declared inline by ExprStmt via PRIVATE_*_ARRAY_PTR template,
        //   e.g. "thread int* ul_3 = ul_1". These variables appear as @Use inside ExprStmt,
        //   so they never show up in forEachOutput and are not in kindToVariable.
        //
        // "derived" private ptrs: variables assigned from original private ptrs via
        //   AssignStmt/MoveStmt (e.g. phi-resolution moves). These appear as @Def in those
        //   instructions and therefore end up in kindToVariable. They must instead be declared
        //   as "thread <type>*" to match the thread address space of the private arrays.
        Map<Variable, String> originalPrivatePtrs = new HashMap<>();
        Map<Variable, String> derivedPrivatePtrs  = new HashMap<>();
        for (int b : lir.linearScanOrder()) {
            for (LIRInstruction instr : lir.getLIRforBlock(lir.getBlockById(b))) {
                if (instr instanceof MetalLIRStmt.ExprStmt exprStmt) {
                    Value exprVal = exprStmt.getExpr();
                    if (exprVal instanceof MetalBinary.Expr binExpr) {
                        String elemType = resolvePrivatePtrElemType(binExpr.getOpcode());
                        if (elemType != null && binExpr.getX() instanceof Variable ptrVar) {
                            originalPrivatePtrs.put(ptrVar, elemType);
                        }
                    }
                } else {
                    Variable lhsVar = null;
                    Value rhsVal = null;
                    if (instr instanceof MetalLIRStmt.AssignStmt assign) {
                        if (assign.getResult() instanceof Variable v) lhsVar = v;
                        rhsVal = assign.getExpr();
                    } else if (instr instanceof MetalLIRStmt.MoveStmt move) {
                        if (move.getResult() instanceof Variable v) lhsVar = v;
                        rhsVal = move.getExpr();
                    }
                    if (lhsVar != null && rhsVal instanceof Variable rhsVar) {
                        String elemType = originalPrivatePtrs.get(rhsVar);
                        if (elemType == null) elemType = derivedPrivatePtrs.get(rhsVar);
                        if (elemType != null) {
                            derivedPrivatePtrs.put(lhsVar, elemType);
                        }
                    }
                }
            }
        }

        // Pass 2: collect variable definitions, excluding ULONG load targets and derived
        // private ptrs from the normal kindToVariable map (emitted separately below).
        for (int b : lir.linearScanOrder()) {
            for (LIRInstruction lirInstruction : lir.getLIRforBlock(lir.getBlockById(b))) {

                lirInstruction.forEachOutput((instruction, value, mode, flags) -> {
                    if (value instanceof Variable variable) {
                        if (variable.toString() != null) {
                            if (!ulongIntLoadVars.contains(variable) && !derivedPrivatePtrs.containsKey(variable)) {
                                // ULONG variables that are not device pointers must be declared
                                // as "ulong" (integer), not "tornado_ptr_t" (device pointer).
                                if (variable.getPlatformKind() == MetalKind.ULONG
                                        && !ulongPointerVars.contains(variable)) {
                                    ulongIntLoadVars.add(variable);
                                } else {
                                    addVariableDef(kindToVariable, variable);
                                }
                            }
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

        // Emit integer declarations for ULONG load targets (byte-offset variables).
        if (!ulongIntLoadVars.isEmpty()) {
            asm.indent();
            asm.emit("ulong ");
            for (Variable var : ulongIntLoadVars) {
                asm.emitValue(crb, var);
                asm.emit(", ");
            }
            asm.emitByte(';', asm.position() - 2);
            asm.eol();
        }

        // Emit thread-address-space pointer declarations for derived private-pointer variables.
        // Grouped by element type to produce compact declarations like "thread int* v1, v2;".
        if (!derivedPrivatePtrs.isEmpty()) {
            Map<String, Set<Variable>> derivedByType = new HashMap<>();
            for (Map.Entry<Variable, String> e : derivedPrivatePtrs.entrySet()) {
                derivedByType.computeIfAbsent(e.getValue(), k -> new HashSet<>()).add(e.getKey());
            }
            for (Map.Entry<String, Set<Variable>> e : derivedByType.entrySet()) {
                asm.indent();
                asm.emit("thread %s* ", e.getKey());
                for (Variable var : e.getValue()) {
                    asm.emitValue(crb, var);
                    asm.emit(", ");
                }
                asm.emitByte(';', asm.position() - 2);
                asm.eol();
            }
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
            // Metal Shading Language preamble
            asm.emitLine("#include <metal_stdlib>");
            asm.emitLine("using namespace metal;");
            asm.emitLine("");
            // tornado_ptr_t is used as the type for all address-computation variables (ul_N).
            // device uchar* supports valid MSL pointer arithmetic with byte-level offsets,
            // and can be reinterpret-cast to any typed device pointer for load/store.
            asm.emitLine("typedef device uchar* tornado_ptr_t;");
            asm.emitLine("");
            // OpenCL-compatible scalar relational functions (Metal only provides vector variants)
            asm.emitLine("inline int isequal(float a, float b)    { return (int)(a == b); }");
            asm.emitLine("inline int isnotequal(float a, float b) { return (int)(a != b); }");
            asm.emitLine("inline int isgreater(float a, float b)  { return (int)(a >  b); }");
            asm.emitLine("inline int isless(float a, float b)     { return (int)(a <  b); }");
            // Java Math.signum(NaN) returns NaN, but Metal sign(NaN) returns 0; use wrapper
            asm.emitLine("inline float signum_f(float x) { return isnan(x) ? x : sign(x); }");
            // Float atomic add via CAS loop (portable: no atomic_float required pre-Metal 3.0)
            asm.emitLine("inline float tornado_atomic_add_float(device atomic_uint* p, float delta) {");
            asm.emitLine("    uint exp = atomic_load_explicit(p, memory_order_relaxed);");
            asm.emitLine("    while (!atomic_compare_exchange_weak_explicit(p, &exp, as_type<uint>(as_type<float>(exp) + delta), memory_order_relaxed, memory_order_relaxed)) {}");
            asm.emitLine("    return as_type<float>(exp);");
            asm.emitLine("}");
            asm.emitLine("");
            // TornadoVM OpenCL-compatibility shims: vloadN / vstoreN for device memory
            // float variants (vload2/3/4)
            asm.emitLine("inline float2  vload2 (uint n, const device float*  p) { return ((const device float2* )p)[n]; }");
            asm.emitLine("inline float3  vload3 (uint n, const device float*  p) { return ((const device float3* )p)[n]; }");
            asm.emitLine("inline float4  vload4 (uint n, const device float*  p) { return ((const device float4* )p)[n]; }");
            asm.emitLine("inline void    vstore2(float2  v, uint n, device float*  p) { ((device float2* )p)[n] = v; }");
            asm.emitLine("inline void    vstore3(float3  v, uint n, device float*  p) { ((device float3* )p)[n] = v; }");
            asm.emitLine("inline void    vstore4(float4  v, uint n, device float*  p) { ((device float4* )p)[n] = v; }");
            // int variants (vload2/3/4)
            asm.emitLine("inline int2    vload2 (uint n, const device int*    p) { return ((const device int2*  )p)[n]; }");
            asm.emitLine("inline int3    vload3 (uint n, const device int*    p) { return ((const device int3*  )p)[n]; }");
            asm.emitLine("inline int4    vload4 (uint n, const device int*    p) { return ((const device int4*  )p)[n]; }");
            asm.emitLine("inline void    vstore2(int2    v, uint n, device int*    p) { ((device int2*  )p)[n] = v; }");
            asm.emitLine("inline void    vstore3(int3    v, uint n, device int*    p) { ((device int3*  )p)[n] = v; }");
            asm.emitLine("inline void    vstore4(int4    v, uint n, device int*    p) { ((device int4*  )p)[n] = v; }");
            // half variants (vload2/3/4)
            asm.emitLine("inline half2   vload2 (uint n, const device half*   p) { return ((const device half2*  )p)[n]; }");
            asm.emitLine("inline half3   vload3 (uint n, const device half*   p) { return ((const device half3*  )p)[n]; }");
            asm.emitLine("inline half4   vload4 (uint n, const device half*   p) { return ((const device half4*  )p)[n]; }");
            asm.emitLine("inline void    vstore2(half2   v, uint n, device half*   p) { ((device half2*  )p)[n] = v; }");
            asm.emitLine("inline void    vstore3(half3   v, uint n, device half*   p) { ((device half3*  )p)[n] = v; }");
            asm.emitLine("inline void    vstore4(half4   v, uint n, device half*   p) { ((device half4*  )p)[n] = v; }");
            // uchar variants (vload2/3/4) - used for unsigned byte data
            asm.emitLine("inline uchar2  vload2 (uint n, const device uchar*  p) { return ((const device uchar2* )p)[n]; }");
            asm.emitLine("inline uchar3  vload3 (uint n, const device uchar*  p) { return ((const device uchar3* )p)[n]; }");
            asm.emitLine("inline uchar4  vload4 (uint n, const device uchar*  p) { return ((const device uchar4* )p)[n]; }");
            asm.emitLine("inline void    vstore2(uchar2  v, uint n, device uchar*  p) { ((device uchar2* )p)[n] = v; }");
            asm.emitLine("inline void    vstore3(uchar3  v, uint n, device uchar*  p) { ((device uchar3* )p)[n] = v; }");
            asm.emitLine("inline void    vstore4(uchar4  v, uint n, device uchar*  p) { ((device uchar4* )p)[n] = v; }");
            // char (signed byte) variants (vload2/3/4) - used for ImageByte3/4 kernels
            asm.emitLine("inline char2   vload2 (uint n, const device char*   p) { return ((const device char2*  )p)[n]; }");
            asm.emitLine("inline char3   vload3 (uint n, const device char*   p) { return ((const device char3*  )p)[n]; }");
            asm.emitLine("inline char4   vload4 (uint n, const device char*   p) { return ((const device char4*  )p)[n]; }");
            asm.emitLine("inline void    vstore2(char2   v, uint n, device char*   p) { ((device char2*  )p)[n] = v; }");
            asm.emitLine("inline void    vstore3(char3   v, uint n, device char*   p) { ((device char3*  )p)[n] = v; }");
            asm.emitLine("inline void    vstore4(char4   v, uint n, device char*   p) { ((device char4*  )p)[n] = v; }");
            // short variants (vload2/3/4)
            asm.emitLine("inline short2  vload2 (uint n, const device short*  p) { return ((const device short2* )p)[n]; }");
            asm.emitLine("inline short4  vload4 (uint n, const device short*  p) { return ((const device short4* )p)[n]; }");
            asm.emitLine("inline void    vstore2(short2  v, uint n, device short*  p) { ((device short2* )p)[n] = v; }");
            asm.emitLine("inline void    vstore4(short4  v, uint n, device short*  p) { ((device short4* )p)[n] = v; }");
            // thread-space (private memory) overloads — needed when vload/vstore are called
            // with thread-address-space pointers from private arrays.
            // float thread variants
            asm.emitLine("inline float2  vload2 (uint n, const thread float*  p) { return ((const thread float2* )p)[n]; }");
            asm.emitLine("inline float3  vload3 (uint n, const thread float*  p) { return ((const thread float3* )p)[n]; }");
            asm.emitLine("inline float4  vload4 (uint n, const thread float*  p) { return ((const thread float4* )p)[n]; }");
            asm.emitLine("inline void    vstore2(float2  v, uint n, thread float*  p) { ((thread float2* )p)[n] = v; }");
            asm.emitLine("inline void    vstore3(float3  v, uint n, thread float*  p) { ((thread float3* )p)[n] = v; }");
            asm.emitLine("inline void    vstore4(float4  v, uint n, thread float*  p) { ((thread float4* )p)[n] = v; }");
            // int thread variants
            asm.emitLine("inline int2    vload2 (uint n, const thread int*    p) { return ((const thread int2*  )p)[n]; }");
            asm.emitLine("inline int3    vload3 (uint n, const thread int*    p) { return ((const thread int3*  )p)[n]; }");
            asm.emitLine("inline int4    vload4 (uint n, const thread int*    p) { return ((const thread int4*  )p)[n]; }");
            asm.emitLine("inline void    vstore2(int2    v, uint n, thread int*    p) { ((thread int2*  )p)[n] = v; }");
            asm.emitLine("inline void    vstore3(int3    v, uint n, thread int*    p) { ((thread int3*  )p)[n] = v; }");
            asm.emitLine("inline void    vstore4(int4    v, uint n, thread int*    p) { ((thread int4*  )p)[n] = v; }");
            // half thread variants
            asm.emitLine("inline half2   vload2 (uint n, const thread half*   p) { return ((const thread half2*  )p)[n]; }");
            asm.emitLine("inline half3   vload3 (uint n, const thread half*   p) { return ((const thread half3*  )p)[n]; }");
            asm.emitLine("inline half4   vload4 (uint n, const thread half*   p) { return ((const thread half4*  )p)[n]; }");
            asm.emitLine("inline void    vstore2(half2   v, uint n, thread half*   p) { ((thread half2*  )p)[n] = v; }");
            asm.emitLine("inline void    vstore3(half3   v, uint n, thread half*   p) { ((thread half3*  )p)[n] = v; }");
            asm.emitLine("inline void    vstore4(half4   v, uint n, thread half*   p) { ((thread half4*  )p)[n] = v; }");
            // short thread variants
            asm.emitLine("inline short2  vload2 (uint n, const thread short*  p) { return ((const thread short2* )p)[n]; }");
            asm.emitLine("inline short4  vload4 (uint n, const thread short*  p) { return ((const thread short4* )p)[n]; }");
            asm.emitLine("inline void    vstore2(short2  v, uint n, thread short*  p) { ((thread short2* )p)[n] = v; }");
            asm.emitLine("inline void    vstore4(short4  v, uint n, thread short*  p) { ((thread short4* )p)[n] = v; }");
            // Extended vector types: float8/float16/int8/int16/half8/half16
            // Metal reserves these names as incomplete types; complete the underlying structs.
            asm.emitLine("struct __Reserved_Name__Do_not_use_float8  { float4 lo, hi; };");
            asm.emitLine("struct __Reserved_Name__Do_not_use_float16 { float8 lo, hi; };");
            asm.emitLine("struct __Reserved_Name__Do_not_use_int8    { int4   lo, hi; };");
            asm.emitLine("struct __Reserved_Name__Do_not_use_int16   { int8   lo, hi; };");
            asm.emitLine("struct __Reserved_Name__Do_not_use_half8   { half4  lo, hi; };");
            asm.emitLine("struct __Reserved_Name__Do_not_use_half16  { half8  lo, hi; };");
            // Arithmetic operators for extended float/int/half vector types
            asm.emitLine("inline float8   operator+(float8   a,float8   b){return{a.lo+b.lo,a.hi+b.hi};}");
            asm.emitLine("inline float8   operator-(float8   a,float8   b){return{a.lo-b.lo,a.hi-b.hi};}");
            asm.emitLine("inline float8   operator*(float8   a,float8   b){return{a.lo*b.lo,a.hi*b.hi};}");
            asm.emitLine("inline float8   operator/(float8   a,float8   b){return{a.lo/b.lo,a.hi/b.hi};}");
            asm.emitLine("inline float16  operator+(float16  a,float16  b){return{a.lo+b.lo,a.hi+b.hi};}");
            asm.emitLine("inline float16  operator-(float16  a,float16  b){return{a.lo-b.lo,a.hi-b.hi};}");
            asm.emitLine("inline float16  operator*(float16  a,float16  b){return{a.lo*b.lo,a.hi*b.hi};}");
            asm.emitLine("inline float16  operator/(float16  a,float16  b){return{a.lo/b.lo,a.hi/b.hi};}");
            asm.emitLine("inline int8     operator+(int8     a,int8     b){return{a.lo+b.lo,a.hi+b.hi};}");
            asm.emitLine("inline int8     operator-(int8     a,int8     b){return{a.lo-b.lo,a.hi-b.hi};}");
            asm.emitLine("inline int8     operator*(int8     a,int8     b){return{a.lo*b.lo,a.hi*b.hi};}");
            asm.emitLine("inline int8     operator/(int8     a,int8     b){return{a.lo/b.lo,a.hi/b.hi};}");
            asm.emitLine("inline int16    operator+(int16    a,int16    b){return{a.lo+b.lo,a.hi+b.hi};}");
            asm.emitLine("inline int16    operator-(int16    a,int16    b){return{a.lo-b.lo,a.hi-b.hi};}");
            asm.emitLine("inline int16    operator*(int16    a,int16    b){return{a.lo*b.lo,a.hi*b.hi};}");
            asm.emitLine("inline int16    operator/(int16    a,int16    b){return{a.lo/b.lo,a.hi/b.hi};}");
            asm.emitLine("inline half8    operator+(half8    a,half8    b){return{a.lo+b.lo,a.hi+b.hi};}");
            asm.emitLine("inline half8    operator-(half8    a,half8    b){return{a.lo-b.lo,a.hi-b.hi};}");
            asm.emitLine("inline half8    operator*(half8    a,half8    b){return{a.lo*b.lo,a.hi*b.hi};}");
            asm.emitLine("inline half8    operator/(half8    a,half8    b){return{a.lo/b.lo,a.hi/b.hi};}");
            asm.emitLine("inline half16   operator+(half16   a,half16   b){return{a.lo+b.lo,a.hi+b.hi};}");
            asm.emitLine("inline half16   operator-(half16   a,half16   b){return{a.lo-b.lo,a.hi-b.hi};}");
            asm.emitLine("inline half16   operator*(half16   a,half16   b){return{a.lo*b.lo,a.hi*b.hi};}");
            asm.emitLine("inline half16   operator/(half16   a,half16   b){return{a.lo/b.lo,a.hi/b.hi};}");
            // vload8/vstore8 for float8 (device memory)
            asm.emitLine("inline float8  vload8 (uint n,const device float* p){const device float4* q=(const device float4*)p+n*2;return{q[0],q[1]};}");
            asm.emitLine("inline void    vstore8(float8  v,uint n,device float* p){device float4* q=(device float4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}");
            asm.emitLine("inline float16 vload16(uint n,const device float* p){float8 lo=vload8(0,p+n*16);float8 hi=vload8(0,p+n*16+8);return{lo,hi};}");
            asm.emitLine("inline void    vstore16(float16 v,uint n,device float* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}");
            // vload8/vstore8 for int8 (device memory)
            asm.emitLine("inline int8    vload8 (uint n,const device int* p){const device int4* q=(const device int4*)p+n*2;return{q[0],q[1]};}");
            asm.emitLine("inline void    vstore8(int8    v,uint n,device int* p){device int4* q=(device int4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}");
            asm.emitLine("inline int16   vload16(uint n,const device int* p){int8 lo=vload8(0,p+n*16);int8 hi=vload8(0,p+n*16+8);return{lo,hi};}");
            asm.emitLine("inline void    vstore16(int16 v,uint n,device int* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}");
            // vload8/vstore8 for half8 (device memory)
            asm.emitLine("inline half8   vload8 (uint n,const device half* p){const device half4* q=(const device half4*)p+n*2;return{q[0],q[1]};}");
            asm.emitLine("inline void    vstore8(half8  v,uint n,device half* p){device half4* q=(device half4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}");
            asm.emitLine("inline half16  vload16(uint n,const device half* p){half8 lo=vload8(0,p+n*16);half8 hi=vload8(0,p+n*16+8);return{lo,hi};}");
            asm.emitLine("inline void    vstore16(half16 v,uint n,device half* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}");
            // thread-space (private memory) vload8/vstore8 overloads for float8/int8/half8
            asm.emitLine("inline float8  vload8 (uint n,const thread float* p){const thread float4* q=(const thread float4*)p+n*2;return{q[0],q[1]};}");
            asm.emitLine("inline void    vstore8(float8  v,uint n,thread float* p){thread float4* q=(thread float4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}");
            asm.emitLine("inline float16 vload16(uint n,const thread float* p){float8 lo=vload8(0,p+n*16);float8 hi=vload8(0,p+n*16+8);return{lo,hi};}");
            asm.emitLine("inline void    vstore16(float16 v,uint n,thread float* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}");
            asm.emitLine("inline int8    vload8 (uint n,const thread int* p){const thread int4* q=(const thread int4*)p+n*2;return{q[0],q[1]};}");
            asm.emitLine("inline void    vstore8(int8    v,uint n,thread int* p){thread int4* q=(thread int4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}");
            asm.emitLine("inline int16   vload16(uint n,const thread int* p){int8 lo=vload8(0,p+n*16);int8 hi=vload8(0,p+n*16+8);return{lo,hi};}");
            asm.emitLine("inline void    vstore16(int16 v,uint n,thread int* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}");
            asm.emitLine("inline half8   vload8 (uint n,const thread half* p){const thread half4* q=(const thread half4*)p+n*2;return{q[0],q[1]};}");
            asm.emitLine("inline void    vstore8(half8  v,uint n,thread half* p){thread half4* q=(thread half4*)p+n*2;q[0]=v.lo;q[1]=v.hi;}");
            asm.emitLine("inline half16  vload16(uint n,const thread half* p){half8 lo=vload8(0,p+n*16);half8 hi=vload8(0,p+n*16+8);return{lo,hi};}");
            asm.emitLine("inline void    vstore16(half16 v,uint n,thread half* p){vstore8(v.lo,0,p+n*16);vstore8(v.hi,0,p+n*16+8);}");
            // MSL atomic helpers: integer multiply (no native intrinsic — CAS loop)
            asm.emitLine("inline int atomicMul_Tornado_Int(device atomic_int* a, int val) {");
            asm.emitLine("  int expected = atomic_load_explicit(a, memory_order_relaxed);");
            asm.emitLine("  int desired;");
            asm.emitLine("  do { desired = expected * val; }");
            asm.emitLine("  while (!atomic_compare_exchange_weak_explicit(a, &expected, desired, memory_order_relaxed, memory_order_relaxed));");
            asm.emitLine("  return desired;");
            asm.emitLine("}");
            asm.emitLine("");

            asm.emit("%s void %s(%s", MetalAssemblerConstants.KERNEL_MODIFIER, methodName, architecture.getABIPretty());
            int nextBufferIdx = emitMethodParameters(asm, method, incomingArguments, true);
            // Metal system values for thread/threadgroup positions and sizes.
            // These are always declared so reduction kernels can use them.
            asm.emit(",\n    uint3 _thread_position_in_grid [[thread_position_in_grid]]");
            asm.emit(",\n    uint3 _thread_position_in_threadgroup [[thread_position_in_threadgroup]]");
            asm.emit(",\n    uint3 _threadgroup_position_in_grid [[threadgroup_position_in_grid]]");
            asm.emit(",\n    uint3 _local_size [[threads_per_threadgroup]]");
            asm.emit(",\n    device uint* _global_sizes [[buffer(%d)]]", nextBufferIdx);
            asm.emitLine("\n)");

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
                    // MSL kernel scalar parameters must use constant address space reference
                    // (bound via setBytes: on the encoder side)
                    String paramName = getParameterName(locals[i]);
                    asm.emit(",\n    constant %s& %s [[buffer(%d)]]", kind.toString(), paramName, metalArgIndex);
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
                    String parameterName = getParameterName(locals[i]);
                    // Use centralized constant for the global memory qualifier instead of a hard-coded literal
                    asm.emit(",\n    %s %s *%s [[buffer(%d)]]", MetalAssemblerConstants.GLOBAL_MEM_MODIFIER, "uchar", parameterName, metalArgIndex);
                    metalArgIndex++;
                }
            } else {
                final AllocatableValue param = incomingArguments.getArgument(i);
                MetalKind metalKind = (MetalKind) param.getPlatformKind();
                if (javaKind.isObject()) {
                    MetalKind tmpKind = MetalKind.resolveToVectorKind(javaType.resolve(method.getDeclaringClass()));
                    if (tmpKind != MetalKind.ILLEGAL) {
                        metalKind = tmpKind;
                    }
                }
                guarantee(metalKind != MetalKind.ILLEGAL, "illegal type for %s", param.getPlatformKind());
                asm.emit(", ");
                asm.emit("%s %s", metalKind.toString(), getParameterName(locals[i]));
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
