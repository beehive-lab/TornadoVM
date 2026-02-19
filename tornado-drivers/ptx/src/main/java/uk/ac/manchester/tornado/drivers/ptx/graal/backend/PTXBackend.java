/*
 * Copyright (c) 2020-2022, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

package uk.ac.manchester.tornado.drivers.ptx.graal.backend;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.guarantee;
import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;
import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.isHalfFloat;
import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getDebugContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.asm.DataBuilder;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;
import jdk.graal.compiler.lir.framemap.ReferenceMapBuilder;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.SuitesProvider;

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
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.api.internal.annotations.Vector;
import uk.ac.manchester.tornado.api.profiler.ProfilerType;
import uk.ac.manchester.tornado.api.profiler.TornadoProfiler;
import uk.ac.manchester.tornado.drivers.common.code.CodeUtil;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.common.utils.BackendDeopt;
import uk.ac.manchester.tornado.drivers.ptx.PTXDeviceContext;
import uk.ac.manchester.tornado.drivers.ptx.PTXTargetDescription;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXArchitecture;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeProvider;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXFrameContext;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXFrameMap;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXFrameMapBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXProviders;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssembler;
import uk.ac.manchester.tornado.drivers.ptx.graal.asm.PTXAssemblerConstants;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResultBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXDataBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerationResult;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXLIRGenerator;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXNodeLIRBuilder;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXNodeMatchRules;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXKind;
import uk.ac.manchester.tornado.drivers.ptx.graal.lir.PTXVectorSplit;
import uk.ac.manchester.tornado.runtime.graal.backend.XPUBackend;
import uk.ac.manchester.tornado.runtime.graal.compiler.TornadoSuitesProvider;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class PTXBackend extends XPUBackend<PTXProviders> implements FrameMap.ReferenceMapBuilderFactory {

    final PTXDeviceContext deviceContext;
    final PTXTargetDescription target;
    private final PTXArchitecture architecture;
    private final PTXCodeProvider codeCache;
    private final OptionValues options;
    private boolean isInitialised;

    public PTXBackend(PTXProviders providers, PTXDeviceContext deviceContext, PTXTargetDescription target, PTXCodeProvider codeCache, OptionValues options) {
        super(providers);

        this.deviceContext = deviceContext;
        this.target = target;
        this.codeCache = codeCache;
        this.options = options;
        architecture = target.getArch();
        isInitialised = false;
    }

    @Override
    public String decodeDeopt(long value) {
        return BackendDeopt.decodeDeopt(value, getProviders());
    }

    @Override
    public SuitesProvider getSuites() {
        return null;
    }

    @Override
    protected CompiledCode createCompiledCode(ResolvedJavaMethod method, CompilationRequest compilationRequest, CompilationResult compilationResult, boolean isDefault, OptionValues options) {
        unimplemented("Create compiled code method in PTXBackend not implemented yet.");
        return null;
    }

    @Override
    public ReferenceMapBuilder newReferenceMapBuilder(int totalFrameSize) {
        return null;
    }

    @Override
    public TornadoSuitesProvider getTornadoSuites() {
        return ((PTXProviders) getProviders()).getSuitesProvider();
    }

    @Override
    public PTXTargetDescription getTarget() {
        return target;
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo, Object stub) {
        return null;
    }

    @Override
    public boolean isInitialised() {
        return isInitialised;
    }

    @Override
    public void init() {
        if (isInitialised) {
            return;
        }

        isInitialised = true;
    }

    @Override
    public int getMethodIndex() {
        return 0;
    }

    /**
     * It allocated the extra buffers that are used by this backend.
     */
    @Override
    public void allocateTornadoVMBuffersOnDevice() {
        TornadoInternalError.shouldNotReachHere("Should not allocate extra buffers on the device.");
    }

    @Override
    public PTXDeviceContext getDeviceContext() {
        return deviceContext;
    }

    @Override
    public FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig nonNullRegisterConfig = (registerConfig == null) ? getCodeCache().getRegisterConfig() : registerConfig;
        return new PTXFrameMapBuilder(newFrameMap(nonNullRegisterConfig), getCodeCache(), registerConfig);
    }

    private FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new PTXFrameMap(getCodeCache(), registerConfig, this);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(CompilationIdentifier identifier, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig) {
        return new PTXLIRGenerationResult(identifier, lir, frameMapBuilder,
                registerAllocationConfig, new CallingConvention(0, null, new AllocatableValue[0] ));
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        return new PTXLIRGenerator(getProviders(), lirGenRes);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new PTXNodeLIRBuilder(graph, lirGen, new PTXNodeMatchRules(lirGen));
    }

    public PTXCompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRes, FrameMap frameMap, PTXCompilationResult compilationResult, CompilationResultBuilderFactory factory,
            boolean isKernel, boolean isParallel, boolean includePrintf, LIR lir) {
        PTXAssembler asm = createAssembler((PTXLIRGenerationResult) lirGenRes);
        PTXFrameContext frameContext = new PTXFrameContext();
        DataBuilder dataBuilder = new PTXDataBuilder();
        PTXCompilationResultBuilder crb = new PTXCompilationResultBuilder(getProviders(), frameMap, asm, dataBuilder, frameContext, options, getDebugContext(), compilationResult, lir);
        crb.setTaskMetaData(compilationResult.metaData());
        crb.setKernel(isKernel);
        crb.setParallel(isParallel);
        crb.setDeviceContext(deviceContext);
        crb.setIncludePrintf(includePrintf);
        return crb;
    }

    private PTXAssembler createAssembler(PTXLIRGenerationResult result) {
        return new PTXAssembler(target, result);
    }

    @Override
    public void emitCode(CompilationResultBuilder resultBuilder, LIR lir, ResolvedJavaMethod method, TornadoProfiler profiler) {

        // Enable Profiler for code generation
        PTXCompilationResultBuilder builder = (PTXCompilationResultBuilder) resultBuilder;
        TaskDataContext taskMetaData = builder.getTaskMetaData();
        profiler.start(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId());

        PTXCompilationResultBuilder crb = (PTXCompilationResultBuilder) resultBuilder;
        final PTXAssembler asm = crb.getAssembler();
        PTXLIRGenerationResult lirGenRes = crb.getPTXLIRGenerationResult();
        emitPrologue(crb, asm, lirGenRes, method);
        crb.emit(lir);
        emitEpilogue(asm);

        profiler.stop(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId());
        profiler.sum(ProfilerType.TOTAL_CODE_GENERATION_TIME, profiler.getTaskTimer(ProfilerType.TASK_CODE_GENERATION_TIME, taskMetaData.getId()));

        asm.cleanUpVarsMapNaming();
    }

    private void emitEpilogue(PTXAssembler asm) {
        asm.emitLine("}");
    }

    private void emitPrologue(PTXCompilationResultBuilder crb, PTXAssembler asm, PTXLIRGenerationResult lirGenRes, ResolvedJavaMethod method) {
        emitPrintfPrototype(crb);
        final CallingConvention incomingArguments = CodeUtil.getCallingConvention(codeCache, HotSpotCallingConventionType.JavaCallee, method);
        if (crb.isKernel()) {
            asm.emit("%s %s %s(%s", PTXAssemblerConstants.EXTERNALLY_VISIBLE, PTXAssemblerConstants.KERNEL_ENTRYPOINT, crb.compilationResult.getName(), architecture.getABI());
            emitMethodParameters(asm, method, incomingArguments, true);
            asm.emit(") {");
            asm.eol();
            emitVariableDefs(asm, lirGenRes);
        } else {
            emitFunctionHeader(asm, method, lirGenRes);
            emitVariableDefs(asm, lirGenRes);
        }
    }

    private void emitMethodParameters(PTXAssembler asm, ResolvedJavaMethod method, CallingConvention incomingArguments, boolean isKernel) {
        final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);

        for (int i = 0; i < incomingArguments.getArgumentCount(); i++) {
            if (isKernel) {
                if (locals[i].getType().getJavaKind().isPrimitive() || isHalfFloat(locals[i].getType())) {
                    final AllocatableValue param = incomingArguments.getArgument(i);
                    asm.emit(", ");
                    asm.emit(".param .align 8 .u64 %s", locals[i].getName());
                } else {
                    // Skip the kernel context object
                    if (locals[i].getType().toJavaName().equals(KernelContext.class.getName())) {
                        asm.emit(", ");
                        asm.emit(".param .u64 .ptr .global .align 8 %s", PTXAssemblerConstants.KERNEL_CONTEXT_ARGUMENT_NAME);
                        continue;
                    }
                    // Skip atomic integers
                    if (locals[i].getType().toJavaName().equals(AtomicInteger.class.getName())) {
                        continue;
                    }
                    asm.emit(", ");
                    final AllocatableValue param = incomingArguments.getArgument(i);
                    PTXKind kind = (PTXKind) param.getPlatformKind();
                    asm.emit(".param .u64 .ptr .global .align %s %s", kind.getSizeInBytes(), locals[i].getName());
                }
            } else {
                final AllocatableValue param = incomingArguments.getArgument(i);
                PTXKind ptxKind = (PTXKind) param.getPlatformKind();
                if (locals[i].getType().getJavaKind().isObject()) {
                    PTXKind tmpKind = PTXKind.resolveToVectorKind(locals[i].getType().resolve(method.getDeclaringClass()));
                    if (tmpKind != PTXKind.ILLEGAL) {
                        ptxKind = tmpKind;
                    }
                }
                guarantee(ptxKind != PTXKind.ILLEGAL, "illegal type for %s", param.getPlatformKind());
                asm.emit(", ");
                asm.emit("%s %s", ptxKind.toString(), locals[i].getName());
            }
        }
    }

    private void emitFunctionHeader(PTXAssembler asm, ResolvedJavaMethod method, PTXLIRGenerationResult lirGenRes) {
        final CallingConvention incomingArguments = PTXCodeUtil.getCallingConvention(codeCache, method);
        String methodName = PTXCodeUtil.makeMethodName(method);

        final JavaKind returnKind = method.getSignature().getReturnKind();
        if (returnKind == JavaKind.Void) {
            asm.emit(".func %s (", methodName);
        } else {
            final ResolvedJavaType returnType = method.getSignature().getReturnType(null).resolve(method.getDeclaringClass());
            PTXKind returnPtxKind = (returnType.getAnnotation(Vector.class) == null) ? getTarget().getPTXKind(returnKind) : PTXKind.fromResolvedJavaType(returnType);
            if (returnPtxKind.isVector()) {
                asm.emit(".func (.param .align 8 .b8 %s[%d]) %s (", "retVar", returnPtxKind.getSizeInBytes(), methodName);
            } else {
                asm.emit(".func (.reg .%s %s) %s (", returnPtxKind, "retVar", methodName);
            }
        }

        final Local[] locals = method.getLocalVariableTable().getLocalsAt(0);
        final Value[] params = new Value[incomingArguments.getArgumentCount()];

        for (int i = 0; i < params.length; i++) {
            final AllocatableValue param = incomingArguments.getArgument(i);
            PTXKind ptxKind = (PTXKind) param.getPlatformKind();
            if (locals[i].getType().getJavaKind().isObject()) {
                PTXKind tmpKind = PTXKind.resolveToVectorKind(locals[i].getType().resolve(method.getDeclaringClass()));
                if (tmpKind != PTXKind.ILLEGAL) {
                    ptxKind = tmpKind;
                }
            }
            guarantee(ptxKind != PTXKind.ILLEGAL, "illegal type for %s", param.getPlatformKind());

            if (ptxKind.isVector()) {
                PTXVectorSplit vectorSplitData = new PTXVectorSplit(locals[i].getName(), ptxKind);
                for (int j = 0; j < vectorSplitData.vectorNames.length; j++) {
                    asm.emit(".reg .%s %s", ptxKind.getElementKind(), vectorSplitData.vectorNames[j]);
                    if (j < vectorSplitData.vectorNames.length - 1) {
                        asm.emit(", ");
                    }
                }

            } else {
                asm.emit(".reg .%s %s", ptxKind, locals[i].getName());
            }
            if (i < params.length - 1) {
                asm.emit(", ");
            }
        }
        asm.emit(") {");
        asm.eol();
    }

    private void emitPrintfPrototype(PTXCompilationResultBuilder crb) {
        if (crb.getIncludePrintf()) {
            PTXAssembler asm = crb.getAssembler();
            asm.emitLine(PTXAssemblerConstants.VPRINTF_PROTOTYPE);
            asm.emitLine("");
        }
    }


    private void emitVariableDefs(PTXAssembler asm, PTXLIRGenerationResult lirGenRes) {
        Logger.traceBuildLIR(Logger.BACKEND.PTX, "emitVariableDefs");
        Map<PTXKind, Set<PTXLIRGenerationResult.VariableData>> kindToVariable = lirGenRes.getVariableTable();

        for (PTXKind type : kindToVariable.keySet()) {
            Set<PTXLIRGenerationResult.VariableData> vars = kindToVariable.get(type);
            int regVarCount = 0;
            for (PTXLIRGenerationResult.VariableData varData : vars) {
                PTXKind kind = (PTXKind) varData.variable.getPlatformKind();
                if (!varData.isArray && !kind.isVector()) {
                    regVarCount++;
                }
                if (kind.isVector()) {
                    PTXVectorSplit vectorSplitData = new PTXVectorSplit(varData.variable);
                    if (vectorSplitData.fullUnwrapVector) {
                        IntStream.range(0, vectorSplitData.vectorNames.length).forEach(i -> asm.emitLine("\t.reg .%s %s;", type.getElementKind(), vectorSplitData.vectorNames[i]));
                    } else {
                        IntStream.range(0, vectorSplitData.vectorNames.length).forEach(i -> asm.emitLine("\t.reg .v%d .%s %s;", vectorSplitData.newKind.getVectorLength(), type.getElementKind(),
                                vectorSplitData.vectorNames[i]));
                    }
                }
            }

            if (regVarCount != 0) {
                asm.emitLine("\t.reg .%s %s<%d>;", type, type.getRegisterTypeString(), regVarCount + 1);
            }
        }
    }
}
