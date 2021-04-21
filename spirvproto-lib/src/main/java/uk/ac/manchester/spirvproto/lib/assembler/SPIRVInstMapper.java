package uk.ac.manchester.spirvproto.lib.assembler;

import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.instructions.*;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import java.util.Arrays;
import java.util.Iterator;
import javax.annotation.Generated;

@Generated("beehive-lab.spirv-proto.generator")
class SPIRVInstMapper {
    public static SPIRVInstruction createInst(SPIRVToken instruction, SPIRVToken[] tokens, SPIRVInstScope scope) {
        Iterator<SPIRVToken> tokenIterator = Arrays.stream(tokens).iterator();
        SPIRVInstruction decoded;
        switch (instruction.value) {
            case "OpNop": decoded = createOpNop(tokenIterator, scope); break;
            case "OpUndef": decoded = createOpUndef(tokenIterator, scope); break;
            case "OpSourceContinued": decoded = createOpSourceContinued(tokenIterator, scope); break;
            case "OpSource": decoded = createOpSource(tokenIterator, scope); break;
            case "OpSourceExtension": decoded = createOpSourceExtension(tokenIterator, scope); break;
            case "OpName": decoded = createOpName(tokenIterator, scope); break;
            case "OpMemberName": decoded = createOpMemberName(tokenIterator, scope); break;
            case "OpString": decoded = createOpString(tokenIterator, scope); break;
            case "OpLine": decoded = createOpLine(tokenIterator, scope); break;
            case "OpExtension": decoded = createOpExtension(tokenIterator, scope); break;
            case "OpExtInstImport": decoded = createOpExtInstImport(tokenIterator, scope); break;
            case "OpExtInst": decoded = createOpExtInst(tokenIterator, scope); break;
            case "OpMemoryModel": decoded = createOpMemoryModel(tokenIterator, scope); break;
            case "OpEntryPoint": decoded = createOpEntryPoint(tokenIterator, scope); break;
            case "OpExecutionMode": decoded = createOpExecutionMode(tokenIterator, scope); break;
            case "OpCapability": decoded = createOpCapability(tokenIterator, scope); break;
            case "OpTypeVoid": decoded = createOpTypeVoid(tokenIterator, scope); break;
            case "OpTypeBool": decoded = createOpTypeBool(tokenIterator, scope); break;
            case "OpTypeInt": decoded = createOpTypeInt(tokenIterator, scope); break;
            case "OpTypeFloat": decoded = createOpTypeFloat(tokenIterator, scope); break;
            case "OpTypeVector": decoded = createOpTypeVector(tokenIterator, scope); break;
            case "OpTypeMatrix": decoded = createOpTypeMatrix(tokenIterator, scope); break;
            case "OpTypeImage": decoded = createOpTypeImage(tokenIterator, scope); break;
            case "OpTypeSampler": decoded = createOpTypeSampler(tokenIterator, scope); break;
            case "OpTypeSampledImage": decoded = createOpTypeSampledImage(tokenIterator, scope); break;
            case "OpTypeArray": decoded = createOpTypeArray(tokenIterator, scope); break;
            case "OpTypeRuntimeArray": decoded = createOpTypeRuntimeArray(tokenIterator, scope); break;
            case "OpTypeStruct": decoded = createOpTypeStruct(tokenIterator, scope); break;
            case "OpTypeOpaque": decoded = createOpTypeOpaque(tokenIterator, scope); break;
            case "OpTypePointer": decoded = createOpTypePointer(tokenIterator, scope); break;
            case "OpTypeFunction": decoded = createOpTypeFunction(tokenIterator, scope); break;
            case "OpTypeEvent": decoded = createOpTypeEvent(tokenIterator, scope); break;
            case "OpTypeDeviceEvent": decoded = createOpTypeDeviceEvent(tokenIterator, scope); break;
            case "OpTypeReserveId": decoded = createOpTypeReserveId(tokenIterator, scope); break;
            case "OpTypeQueue": decoded = createOpTypeQueue(tokenIterator, scope); break;
            case "OpTypePipe": decoded = createOpTypePipe(tokenIterator, scope); break;
            case "OpTypeForwardPointer": decoded = createOpTypeForwardPointer(tokenIterator, scope); break;
            case "OpConstantTrue": decoded = createOpConstantTrue(tokenIterator, scope); break;
            case "OpConstantFalse": decoded = createOpConstantFalse(tokenIterator, scope); break;
            case "OpConstant": decoded = createOpConstant(tokenIterator, scope); break;
            case "OpConstantComposite": decoded = createOpConstantComposite(tokenIterator, scope); break;
            case "OpConstantSampler": decoded = createOpConstantSampler(tokenIterator, scope); break;
            case "OpConstantNull": decoded = createOpConstantNull(tokenIterator, scope); break;
            case "OpSpecConstantTrue": decoded = createOpSpecConstantTrue(tokenIterator, scope); break;
            case "OpSpecConstantFalse": decoded = createOpSpecConstantFalse(tokenIterator, scope); break;
            case "OpSpecConstant": decoded = createOpSpecConstant(tokenIterator, scope); break;
            case "OpSpecConstantComposite": decoded = createOpSpecConstantComposite(tokenIterator, scope); break;
            case "OpSpecConstantOp": decoded = createOpSpecConstantOp(tokenIterator, scope); break;
            case "OpFunction": decoded = createOpFunction(tokenIterator, scope); break;
            case "OpFunctionParameter": decoded = createOpFunctionParameter(tokenIterator, scope); break;
            case "OpFunctionEnd": decoded = createOpFunctionEnd(tokenIterator, scope); break;
            case "OpFunctionCall": decoded = createOpFunctionCall(tokenIterator, scope); break;
            case "OpVariable": decoded = createOpVariable(tokenIterator, scope); break;
            case "OpImageTexelPointer": decoded = createOpImageTexelPointer(tokenIterator, scope); break;
            case "OpLoad": decoded = createOpLoad(tokenIterator, scope); break;
            case "OpStore": decoded = createOpStore(tokenIterator, scope); break;
            case "OpCopyMemory": decoded = createOpCopyMemory(tokenIterator, scope); break;
            case "OpCopyMemorySized": decoded = createOpCopyMemorySized(tokenIterator, scope); break;
            case "OpAccessChain": decoded = createOpAccessChain(tokenIterator, scope); break;
            case "OpInBoundsAccessChain": decoded = createOpInBoundsAccessChain(tokenIterator, scope); break;
            case "OpPtrAccessChain": decoded = createOpPtrAccessChain(tokenIterator, scope); break;
            case "OpArrayLength": decoded = createOpArrayLength(tokenIterator, scope); break;
            case "OpGenericPtrMemSemantics": decoded = createOpGenericPtrMemSemantics(tokenIterator, scope); break;
            case "OpInBoundsPtrAccessChain": decoded = createOpInBoundsPtrAccessChain(tokenIterator, scope); break;
            case "OpDecorate": decoded = createOpDecorate(tokenIterator, scope); break;
            case "OpMemberDecorate": decoded = createOpMemberDecorate(tokenIterator, scope); break;
            case "OpDecorationGroup": decoded = createOpDecorationGroup(tokenIterator, scope); break;
            case "OpGroupDecorate": decoded = createOpGroupDecorate(tokenIterator, scope); break;
            case "OpGroupMemberDecorate": decoded = createOpGroupMemberDecorate(tokenIterator, scope); break;
            case "OpVectorExtractDynamic": decoded = createOpVectorExtractDynamic(tokenIterator, scope); break;
            case "OpVectorInsertDynamic": decoded = createOpVectorInsertDynamic(tokenIterator, scope); break;
            case "OpVectorShuffle": decoded = createOpVectorShuffle(tokenIterator, scope); break;
            case "OpCompositeConstruct": decoded = createOpCompositeConstruct(tokenIterator, scope); break;
            case "OpCompositeExtract": decoded = createOpCompositeExtract(tokenIterator, scope); break;
            case "OpCompositeInsert": decoded = createOpCompositeInsert(tokenIterator, scope); break;
            case "OpCopyObject": decoded = createOpCopyObject(tokenIterator, scope); break;
            case "OpTranspose": decoded = createOpTranspose(tokenIterator, scope); break;
            case "OpSampledImage": decoded = createOpSampledImage(tokenIterator, scope); break;
            case "OpImageSampleImplicitLod": decoded = createOpImageSampleImplicitLod(tokenIterator, scope); break;
            case "OpImageSampleExplicitLod": decoded = createOpImageSampleExplicitLod(tokenIterator, scope); break;
            case "OpImageSampleDrefImplicitLod": decoded = createOpImageSampleDrefImplicitLod(tokenIterator, scope); break;
            case "OpImageSampleDrefExplicitLod": decoded = createOpImageSampleDrefExplicitLod(tokenIterator, scope); break;
            case "OpImageSampleProjImplicitLod": decoded = createOpImageSampleProjImplicitLod(tokenIterator, scope); break;
            case "OpImageSampleProjExplicitLod": decoded = createOpImageSampleProjExplicitLod(tokenIterator, scope); break;
            case "OpImageSampleProjDrefImplicitLod": decoded = createOpImageSampleProjDrefImplicitLod(tokenIterator, scope); break;
            case "OpImageSampleProjDrefExplicitLod": decoded = createOpImageSampleProjDrefExplicitLod(tokenIterator, scope); break;
            case "OpImageFetch": decoded = createOpImageFetch(tokenIterator, scope); break;
            case "OpImageGather": decoded = createOpImageGather(tokenIterator, scope); break;
            case "OpImageDrefGather": decoded = createOpImageDrefGather(tokenIterator, scope); break;
            case "OpImageRead": decoded = createOpImageRead(tokenIterator, scope); break;
            case "OpImageWrite": decoded = createOpImageWrite(tokenIterator, scope); break;
            case "OpImage": decoded = createOpImage(tokenIterator, scope); break;
            case "OpImageQueryFormat": decoded = createOpImageQueryFormat(tokenIterator, scope); break;
            case "OpImageQueryOrder": decoded = createOpImageQueryOrder(tokenIterator, scope); break;
            case "OpImageQuerySizeLod": decoded = createOpImageQuerySizeLod(tokenIterator, scope); break;
            case "OpImageQuerySize": decoded = createOpImageQuerySize(tokenIterator, scope); break;
            case "OpImageQueryLod": decoded = createOpImageQueryLod(tokenIterator, scope); break;
            case "OpImageQueryLevels": decoded = createOpImageQueryLevels(tokenIterator, scope); break;
            case "OpImageQuerySamples": decoded = createOpImageQuerySamples(tokenIterator, scope); break;
            case "OpConvertFToU": decoded = createOpConvertFToU(tokenIterator, scope); break;
            case "OpConvertFToS": decoded = createOpConvertFToS(tokenIterator, scope); break;
            case "OpConvertSToF": decoded = createOpConvertSToF(tokenIterator, scope); break;
            case "OpConvertUToF": decoded = createOpConvertUToF(tokenIterator, scope); break;
            case "OpUConvert": decoded = createOpUConvert(tokenIterator, scope); break;
            case "OpSConvert": decoded = createOpSConvert(tokenIterator, scope); break;
            case "OpFConvert": decoded = createOpFConvert(tokenIterator, scope); break;
            case "OpQuantizeToF16": decoded = createOpQuantizeToF16(tokenIterator, scope); break;
            case "OpConvertPtrToU": decoded = createOpConvertPtrToU(tokenIterator, scope); break;
            case "OpSatConvertSToU": decoded = createOpSatConvertSToU(tokenIterator, scope); break;
            case "OpSatConvertUToS": decoded = createOpSatConvertUToS(tokenIterator, scope); break;
            case "OpConvertUToPtr": decoded = createOpConvertUToPtr(tokenIterator, scope); break;
            case "OpPtrCastToGeneric": decoded = createOpPtrCastToGeneric(tokenIterator, scope); break;
            case "OpGenericCastToPtr": decoded = createOpGenericCastToPtr(tokenIterator, scope); break;
            case "OpGenericCastToPtrExplicit": decoded = createOpGenericCastToPtrExplicit(tokenIterator, scope); break;
            case "OpBitcast": decoded = createOpBitcast(tokenIterator, scope); break;
            case "OpSNegate": decoded = createOpSNegate(tokenIterator, scope); break;
            case "OpFNegate": decoded = createOpFNegate(tokenIterator, scope); break;
            case "OpIAdd": decoded = createOpIAdd(tokenIterator, scope); break;
            case "OpFAdd": decoded = createOpFAdd(tokenIterator, scope); break;
            case "OpISub": decoded = createOpISub(tokenIterator, scope); break;
            case "OpFSub": decoded = createOpFSub(tokenIterator, scope); break;
            case "OpIMul": decoded = createOpIMul(tokenIterator, scope); break;
            case "OpFMul": decoded = createOpFMul(tokenIterator, scope); break;
            case "OpUDiv": decoded = createOpUDiv(tokenIterator, scope); break;
            case "OpSDiv": decoded = createOpSDiv(tokenIterator, scope); break;
            case "OpFDiv": decoded = createOpFDiv(tokenIterator, scope); break;
            case "OpUMod": decoded = createOpUMod(tokenIterator, scope); break;
            case "OpSRem": decoded = createOpSRem(tokenIterator, scope); break;
            case "OpSMod": decoded = createOpSMod(tokenIterator, scope); break;
            case "OpFRem": decoded = createOpFRem(tokenIterator, scope); break;
            case "OpFMod": decoded = createOpFMod(tokenIterator, scope); break;
            case "OpVectorTimesScalar": decoded = createOpVectorTimesScalar(tokenIterator, scope); break;
            case "OpMatrixTimesScalar": decoded = createOpMatrixTimesScalar(tokenIterator, scope); break;
            case "OpVectorTimesMatrix": decoded = createOpVectorTimesMatrix(tokenIterator, scope); break;
            case "OpMatrixTimesVector": decoded = createOpMatrixTimesVector(tokenIterator, scope); break;
            case "OpMatrixTimesMatrix": decoded = createOpMatrixTimesMatrix(tokenIterator, scope); break;
            case "OpOuterProduct": decoded = createOpOuterProduct(tokenIterator, scope); break;
            case "OpDot": decoded = createOpDot(tokenIterator, scope); break;
            case "OpIAddCarry": decoded = createOpIAddCarry(tokenIterator, scope); break;
            case "OpISubBorrow": decoded = createOpISubBorrow(tokenIterator, scope); break;
            case "OpUMulExtended": decoded = createOpUMulExtended(tokenIterator, scope); break;
            case "OpSMulExtended": decoded = createOpSMulExtended(tokenIterator, scope); break;
            case "OpAny": decoded = createOpAny(tokenIterator, scope); break;
            case "OpAll": decoded = createOpAll(tokenIterator, scope); break;
            case "OpIsNan": decoded = createOpIsNan(tokenIterator, scope); break;
            case "OpIsInf": decoded = createOpIsInf(tokenIterator, scope); break;
            case "OpIsFinite": decoded = createOpIsFinite(tokenIterator, scope); break;
            case "OpIsNormal": decoded = createOpIsNormal(tokenIterator, scope); break;
            case "OpSignBitSet": decoded = createOpSignBitSet(tokenIterator, scope); break;
            case "OpLessOrGreater": decoded = createOpLessOrGreater(tokenIterator, scope); break;
            case "OpOrdered": decoded = createOpOrdered(tokenIterator, scope); break;
            case "OpUnordered": decoded = createOpUnordered(tokenIterator, scope); break;
            case "OpLogicalEqual": decoded = createOpLogicalEqual(tokenIterator, scope); break;
            case "OpLogicalNotEqual": decoded = createOpLogicalNotEqual(tokenIterator, scope); break;
            case "OpLogicalOr": decoded = createOpLogicalOr(tokenIterator, scope); break;
            case "OpLogicalAnd": decoded = createOpLogicalAnd(tokenIterator, scope); break;
            case "OpLogicalNot": decoded = createOpLogicalNot(tokenIterator, scope); break;
            case "OpSelect": decoded = createOpSelect(tokenIterator, scope); break;
            case "OpIEqual": decoded = createOpIEqual(tokenIterator, scope); break;
            case "OpINotEqual": decoded = createOpINotEqual(tokenIterator, scope); break;
            case "OpUGreaterThan": decoded = createOpUGreaterThan(tokenIterator, scope); break;
            case "OpSGreaterThan": decoded = createOpSGreaterThan(tokenIterator, scope); break;
            case "OpUGreaterThanEqual": decoded = createOpUGreaterThanEqual(tokenIterator, scope); break;
            case "OpSGreaterThanEqual": decoded = createOpSGreaterThanEqual(tokenIterator, scope); break;
            case "OpULessThan": decoded = createOpULessThan(tokenIterator, scope); break;
            case "OpSLessThan": decoded = createOpSLessThan(tokenIterator, scope); break;
            case "OpULessThanEqual": decoded = createOpULessThanEqual(tokenIterator, scope); break;
            case "OpSLessThanEqual": decoded = createOpSLessThanEqual(tokenIterator, scope); break;
            case "OpFOrdEqual": decoded = createOpFOrdEqual(tokenIterator, scope); break;
            case "OpFUnordEqual": decoded = createOpFUnordEqual(tokenIterator, scope); break;
            case "OpFOrdNotEqual": decoded = createOpFOrdNotEqual(tokenIterator, scope); break;
            case "OpFUnordNotEqual": decoded = createOpFUnordNotEqual(tokenIterator, scope); break;
            case "OpFOrdLessThan": decoded = createOpFOrdLessThan(tokenIterator, scope); break;
            case "OpFUnordLessThan": decoded = createOpFUnordLessThan(tokenIterator, scope); break;
            case "OpFOrdGreaterThan": decoded = createOpFOrdGreaterThan(tokenIterator, scope); break;
            case "OpFUnordGreaterThan": decoded = createOpFUnordGreaterThan(tokenIterator, scope); break;
            case "OpFOrdLessThanEqual": decoded = createOpFOrdLessThanEqual(tokenIterator, scope); break;
            case "OpFUnordLessThanEqual": decoded = createOpFUnordLessThanEqual(tokenIterator, scope); break;
            case "OpFOrdGreaterThanEqual": decoded = createOpFOrdGreaterThanEqual(tokenIterator, scope); break;
            case "OpFUnordGreaterThanEqual": decoded = createOpFUnordGreaterThanEqual(tokenIterator, scope); break;
            case "OpShiftRightLogical": decoded = createOpShiftRightLogical(tokenIterator, scope); break;
            case "OpShiftRightArithmetic": decoded = createOpShiftRightArithmetic(tokenIterator, scope); break;
            case "OpShiftLeftLogical": decoded = createOpShiftLeftLogical(tokenIterator, scope); break;
            case "OpBitwiseOr": decoded = createOpBitwiseOr(tokenIterator, scope); break;
            case "OpBitwiseXor": decoded = createOpBitwiseXor(tokenIterator, scope); break;
            case "OpBitwiseAnd": decoded = createOpBitwiseAnd(tokenIterator, scope); break;
            case "OpNot": decoded = createOpNot(tokenIterator, scope); break;
            case "OpBitFieldInsert": decoded = createOpBitFieldInsert(tokenIterator, scope); break;
            case "OpBitFieldSExtract": decoded = createOpBitFieldSExtract(tokenIterator, scope); break;
            case "OpBitFieldUExtract": decoded = createOpBitFieldUExtract(tokenIterator, scope); break;
            case "OpBitReverse": decoded = createOpBitReverse(tokenIterator, scope); break;
            case "OpBitCount": decoded = createOpBitCount(tokenIterator, scope); break;
            case "OpDPdx": decoded = createOpDPdx(tokenIterator, scope); break;
            case "OpDPdy": decoded = createOpDPdy(tokenIterator, scope); break;
            case "OpFwidth": decoded = createOpFwidth(tokenIterator, scope); break;
            case "OpDPdxFine": decoded = createOpDPdxFine(tokenIterator, scope); break;
            case "OpDPdyFine": decoded = createOpDPdyFine(tokenIterator, scope); break;
            case "OpFwidthFine": decoded = createOpFwidthFine(tokenIterator, scope); break;
            case "OpDPdxCoarse": decoded = createOpDPdxCoarse(tokenIterator, scope); break;
            case "OpDPdyCoarse": decoded = createOpDPdyCoarse(tokenIterator, scope); break;
            case "OpFwidthCoarse": decoded = createOpFwidthCoarse(tokenIterator, scope); break;
            case "OpEmitVertex": decoded = createOpEmitVertex(tokenIterator, scope); break;
            case "OpEndPrimitive": decoded = createOpEndPrimitive(tokenIterator, scope); break;
            case "OpEmitStreamVertex": decoded = createOpEmitStreamVertex(tokenIterator, scope); break;
            case "OpEndStreamPrimitive": decoded = createOpEndStreamPrimitive(tokenIterator, scope); break;
            case "OpControlBarrier": decoded = createOpControlBarrier(tokenIterator, scope); break;
            case "OpMemoryBarrier": decoded = createOpMemoryBarrier(tokenIterator, scope); break;
            case "OpAtomicLoad": decoded = createOpAtomicLoad(tokenIterator, scope); break;
            case "OpAtomicStore": decoded = createOpAtomicStore(tokenIterator, scope); break;
            case "OpAtomicExchange": decoded = createOpAtomicExchange(tokenIterator, scope); break;
            case "OpAtomicCompareExchange": decoded = createOpAtomicCompareExchange(tokenIterator, scope); break;
            case "OpAtomicCompareExchangeWeak": decoded = createOpAtomicCompareExchangeWeak(tokenIterator, scope); break;
            case "OpAtomicIIncrement": decoded = createOpAtomicIIncrement(tokenIterator, scope); break;
            case "OpAtomicIDecrement": decoded = createOpAtomicIDecrement(tokenIterator, scope); break;
            case "OpAtomicIAdd": decoded = createOpAtomicIAdd(tokenIterator, scope); break;
            case "OpAtomicISub": decoded = createOpAtomicISub(tokenIterator, scope); break;
            case "OpAtomicSMin": decoded = createOpAtomicSMin(tokenIterator, scope); break;
            case "OpAtomicUMin": decoded = createOpAtomicUMin(tokenIterator, scope); break;
            case "OpAtomicSMax": decoded = createOpAtomicSMax(tokenIterator, scope); break;
            case "OpAtomicUMax": decoded = createOpAtomicUMax(tokenIterator, scope); break;
            case "OpAtomicAnd": decoded = createOpAtomicAnd(tokenIterator, scope); break;
            case "OpAtomicOr": decoded = createOpAtomicOr(tokenIterator, scope); break;
            case "OpAtomicXor": decoded = createOpAtomicXor(tokenIterator, scope); break;
            case "OpPhi": decoded = createOpPhi(tokenIterator, scope); break;
            case "OpLoopMerge": decoded = createOpLoopMerge(tokenIterator, scope); break;
            case "OpSelectionMerge": decoded = createOpSelectionMerge(tokenIterator, scope); break;
            case "OpLabel": decoded = createOpLabel(tokenIterator, scope); break;
            case "OpBranch": decoded = createOpBranch(tokenIterator, scope); break;
            case "OpBranchConditional": decoded = createOpBranchConditional(tokenIterator, scope); break;
            case "OpSwitch": decoded = createOpSwitch(tokenIterator, scope); break;
            case "OpKill": decoded = createOpKill(tokenIterator, scope); break;
            case "OpReturn": decoded = createOpReturn(tokenIterator, scope); break;
            case "OpReturnValue": decoded = createOpReturnValue(tokenIterator, scope); break;
            case "OpUnreachable": decoded = createOpUnreachable(tokenIterator, scope); break;
            case "OpLifetimeStart": decoded = createOpLifetimeStart(tokenIterator, scope); break;
            case "OpLifetimeStop": decoded = createOpLifetimeStop(tokenIterator, scope); break;
            case "OpGroupAsyncCopy": decoded = createOpGroupAsyncCopy(tokenIterator, scope); break;
            case "OpGroupWaitEvents": decoded = createOpGroupWaitEvents(tokenIterator, scope); break;
            case "OpGroupAll": decoded = createOpGroupAll(tokenIterator, scope); break;
            case "OpGroupAny": decoded = createOpGroupAny(tokenIterator, scope); break;
            case "OpGroupBroadcast": decoded = createOpGroupBroadcast(tokenIterator, scope); break;
            case "OpGroupIAdd": decoded = createOpGroupIAdd(tokenIterator, scope); break;
            case "OpGroupFAdd": decoded = createOpGroupFAdd(tokenIterator, scope); break;
            case "OpGroupFMin": decoded = createOpGroupFMin(tokenIterator, scope); break;
            case "OpGroupUMin": decoded = createOpGroupUMin(tokenIterator, scope); break;
            case "OpGroupSMin": decoded = createOpGroupSMin(tokenIterator, scope); break;
            case "OpGroupFMax": decoded = createOpGroupFMax(tokenIterator, scope); break;
            case "OpGroupUMax": decoded = createOpGroupUMax(tokenIterator, scope); break;
            case "OpGroupSMax": decoded = createOpGroupSMax(tokenIterator, scope); break;
            case "OpReadPipe": decoded = createOpReadPipe(tokenIterator, scope); break;
            case "OpWritePipe": decoded = createOpWritePipe(tokenIterator, scope); break;
            case "OpReservedReadPipe": decoded = createOpReservedReadPipe(tokenIterator, scope); break;
            case "OpReservedWritePipe": decoded = createOpReservedWritePipe(tokenIterator, scope); break;
            case "OpReserveReadPipePackets": decoded = createOpReserveReadPipePackets(tokenIterator, scope); break;
            case "OpReserveWritePipePackets": decoded = createOpReserveWritePipePackets(tokenIterator, scope); break;
            case "OpCommitReadPipe": decoded = createOpCommitReadPipe(tokenIterator, scope); break;
            case "OpCommitWritePipe": decoded = createOpCommitWritePipe(tokenIterator, scope); break;
            case "OpIsValidReserveId": decoded = createOpIsValidReserveId(tokenIterator, scope); break;
            case "OpGetNumPipePackets": decoded = createOpGetNumPipePackets(tokenIterator, scope); break;
            case "OpGetMaxPipePackets": decoded = createOpGetMaxPipePackets(tokenIterator, scope); break;
            case "OpGroupReserveReadPipePackets": decoded = createOpGroupReserveReadPipePackets(tokenIterator, scope); break;
            case "OpGroupReserveWritePipePackets": decoded = createOpGroupReserveWritePipePackets(tokenIterator, scope); break;
            case "OpGroupCommitReadPipe": decoded = createOpGroupCommitReadPipe(tokenIterator, scope); break;
            case "OpGroupCommitWritePipe": decoded = createOpGroupCommitWritePipe(tokenIterator, scope); break;
            case "OpEnqueueMarker": decoded = createOpEnqueueMarker(tokenIterator, scope); break;
            case "OpEnqueueKernel": decoded = createOpEnqueueKernel(tokenIterator, scope); break;
            case "OpGetKernelNDrangeSubGroupCount": decoded = createOpGetKernelNDrangeSubGroupCount(tokenIterator, scope); break;
            case "OpGetKernelNDrangeMaxSubGroupSize": decoded = createOpGetKernelNDrangeMaxSubGroupSize(tokenIterator, scope); break;
            case "OpGetKernelWorkGroupSize": decoded = createOpGetKernelWorkGroupSize(tokenIterator, scope); break;
            case "OpGetKernelPreferredWorkGroupSizeMultiple": decoded = createOpGetKernelPreferredWorkGroupSizeMultiple(tokenIterator, scope); break;
            case "OpRetainEvent": decoded = createOpRetainEvent(tokenIterator, scope); break;
            case "OpReleaseEvent": decoded = createOpReleaseEvent(tokenIterator, scope); break;
            case "OpCreateUserEvent": decoded = createOpCreateUserEvent(tokenIterator, scope); break;
            case "OpIsValidEvent": decoded = createOpIsValidEvent(tokenIterator, scope); break;
            case "OpSetUserEventStatus": decoded = createOpSetUserEventStatus(tokenIterator, scope); break;
            case "OpCaptureEventProfilingInfo": decoded = createOpCaptureEventProfilingInfo(tokenIterator, scope); break;
            case "OpGetDefaultQueue": decoded = createOpGetDefaultQueue(tokenIterator, scope); break;
            case "OpBuildNDRange": decoded = createOpBuildNDRange(tokenIterator, scope); break;
            case "OpImageSparseSampleImplicitLod": decoded = createOpImageSparseSampleImplicitLod(tokenIterator, scope); break;
            case "OpImageSparseSampleExplicitLod": decoded = createOpImageSparseSampleExplicitLod(tokenIterator, scope); break;
            case "OpImageSparseSampleDrefImplicitLod": decoded = createOpImageSparseSampleDrefImplicitLod(tokenIterator, scope); break;
            case "OpImageSparseSampleDrefExplicitLod": decoded = createOpImageSparseSampleDrefExplicitLod(tokenIterator, scope); break;
            case "OpImageSparseSampleProjImplicitLod": decoded = createOpImageSparseSampleProjImplicitLod(tokenIterator, scope); break;
            case "OpImageSparseSampleProjExplicitLod": decoded = createOpImageSparseSampleProjExplicitLod(tokenIterator, scope); break;
            case "OpImageSparseSampleProjDrefImplicitLod": decoded = createOpImageSparseSampleProjDrefImplicitLod(tokenIterator, scope); break;
            case "OpImageSparseSampleProjDrefExplicitLod": decoded = createOpImageSparseSampleProjDrefExplicitLod(tokenIterator, scope); break;
            case "OpImageSparseFetch": decoded = createOpImageSparseFetch(tokenIterator, scope); break;
            case "OpImageSparseGather": decoded = createOpImageSparseGather(tokenIterator, scope); break;
            case "OpImageSparseDrefGather": decoded = createOpImageSparseDrefGather(tokenIterator, scope); break;
            case "OpImageSparseTexelsResident": decoded = createOpImageSparseTexelsResident(tokenIterator, scope); break;
            case "OpNoLine": decoded = createOpNoLine(tokenIterator, scope); break;
            case "OpAtomicFlagTestAndSet": decoded = createOpAtomicFlagTestAndSet(tokenIterator, scope); break;
            case "OpAtomicFlagClear": decoded = createOpAtomicFlagClear(tokenIterator, scope); break;
            case "OpImageSparseRead": decoded = createOpImageSparseRead(tokenIterator, scope); break;
            case "OpSizeOf": decoded = createOpSizeOf(tokenIterator, scope); break;
            case "OpTypePipeStorage": decoded = createOpTypePipeStorage(tokenIterator, scope); break;
            case "OpConstantPipeStorage": decoded = createOpConstantPipeStorage(tokenIterator, scope); break;
            case "OpCreatePipeFromPipeStorage": decoded = createOpCreatePipeFromPipeStorage(tokenIterator, scope); break;
            case "OpGetKernelLocalSizeForSubgroupCount": decoded = createOpGetKernelLocalSizeForSubgroupCount(tokenIterator, scope); break;
            case "OpGetKernelMaxNumSubgroups": decoded = createOpGetKernelMaxNumSubgroups(tokenIterator, scope); break;
            case "OpTypeNamedBarrier": decoded = createOpTypeNamedBarrier(tokenIterator, scope); break;
            case "OpNamedBarrierInitialize": decoded = createOpNamedBarrierInitialize(tokenIterator, scope); break;
            case "OpMemoryNamedBarrier": decoded = createOpMemoryNamedBarrier(tokenIterator, scope); break;
            case "OpModuleProcessed": decoded = createOpModuleProcessed(tokenIterator, scope); break;
            case "OpExecutionModeId": decoded = createOpExecutionModeId(tokenIterator, scope); break;
            case "OpDecorateId": decoded = createOpDecorateId(tokenIterator, scope); break;
            case "OpSubgroupBallotKHR": decoded = createOpSubgroupBallotKHR(tokenIterator, scope); break;
            case "OpSubgroupFirstInvocationKHR": decoded = createOpSubgroupFirstInvocationKHR(tokenIterator, scope); break;
            case "OpSubgroupAllKHR": decoded = createOpSubgroupAllKHR(tokenIterator, scope); break;
            case "OpSubgroupAnyKHR": decoded = createOpSubgroupAnyKHR(tokenIterator, scope); break;
            case "OpSubgroupAllEqualKHR": decoded = createOpSubgroupAllEqualKHR(tokenIterator, scope); break;
            case "OpSubgroupReadInvocationKHR": decoded = createOpSubgroupReadInvocationKHR(tokenIterator, scope); break;
            case "OpGroupIAddNonUniformAMD": decoded = createOpGroupIAddNonUniformAMD(tokenIterator, scope); break;
            case "OpGroupFAddNonUniformAMD": decoded = createOpGroupFAddNonUniformAMD(tokenIterator, scope); break;
            case "OpGroupFMinNonUniformAMD": decoded = createOpGroupFMinNonUniformAMD(tokenIterator, scope); break;
            case "OpGroupUMinNonUniformAMD": decoded = createOpGroupUMinNonUniformAMD(tokenIterator, scope); break;
            case "OpGroupSMinNonUniformAMD": decoded = createOpGroupSMinNonUniformAMD(tokenIterator, scope); break;
            case "OpGroupFMaxNonUniformAMD": decoded = createOpGroupFMaxNonUniformAMD(tokenIterator, scope); break;
            case "OpGroupUMaxNonUniformAMD": decoded = createOpGroupUMaxNonUniformAMD(tokenIterator, scope); break;
            case "OpGroupSMaxNonUniformAMD": decoded = createOpGroupSMaxNonUniformAMD(tokenIterator, scope); break;
            case "OpFragmentMaskFetchAMD": decoded = createOpFragmentMaskFetchAMD(tokenIterator, scope); break;
            case "OpFragmentFetchAMD": decoded = createOpFragmentFetchAMD(tokenIterator, scope); break;
            case "OpSubgroupShuffleINTEL": decoded = createOpSubgroupShuffleINTEL(tokenIterator, scope); break;
            case "OpSubgroupShuffleDownINTEL": decoded = createOpSubgroupShuffleDownINTEL(tokenIterator, scope); break;
            case "OpSubgroupShuffleUpINTEL": decoded = createOpSubgroupShuffleUpINTEL(tokenIterator, scope); break;
            case "OpSubgroupShuffleXorINTEL": decoded = createOpSubgroupShuffleXorINTEL(tokenIterator, scope); break;
            case "OpSubgroupBlockReadINTEL": decoded = createOpSubgroupBlockReadINTEL(tokenIterator, scope); break;
            case "OpSubgroupBlockWriteINTEL": decoded = createOpSubgroupBlockWriteINTEL(tokenIterator, scope); break;
            case "OpSubgroupImageBlockReadINTEL": decoded = createOpSubgroupImageBlockReadINTEL(tokenIterator, scope); break;
            case "OpSubgroupImageBlockWriteINTEL": decoded = createOpSubgroupImageBlockWriteINTEL(tokenIterator, scope); break;
            case "OpDecorateStringGOOGLE": decoded = createOpDecorateStringGOOGLE(tokenIterator, scope); break;
            case "OpMemberDecorateStringGOOGLE": decoded = createOpMemberDecorateStringGOOGLE(tokenIterator, scope); break;
            default: throw new IllegalArgumentException(instruction.value);
        }

        return decoded;
    }

    private static SPIRVInstruction createOpNop(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {

        return new SPIRVOpNop();
    }

    private static SPIRVInstruction createOpUndef(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpUndef(operand2, operand1);
    }

    private static SPIRVInstruction createOpSourceContinued(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVLiteralString operand1 = SPIRVOperandMapper.mapLiteralString(tokens, scope);

        return new SPIRVOpSourceContinued(operand1);
    }

    private static SPIRVInstruction createOpSource(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVSourceLanguage operand1 = SPIRVOperandMapper.mapSourceLanguage(tokens, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVOptionalOperand<SPIRVId> operand3 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand3.setValue(SPIRVOperandMapper.mapId(tokens, scope));
        SPIRVOptionalOperand<SPIRVLiteralString> operand4 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand4.setValue(SPIRVOperandMapper.mapLiteralString(tokens, scope));

        return new SPIRVOpSource(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSourceExtension(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVLiteralString operand1 = SPIRVOperandMapper.mapLiteralString(tokens, scope);

        return new SPIRVOpSourceExtension(operand1);
    }

    private static SPIRVInstruction createOpName(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralString operand2 = SPIRVOperandMapper.mapLiteralString(tokens, scope);

        return new SPIRVOpName(operand1, operand2);
    }

    private static SPIRVInstruction createOpMemberName(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVLiteralString operand3 = SPIRVOperandMapper.mapLiteralString(tokens, scope);

        return new SPIRVOpMemberName(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpString(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralString operand2 = SPIRVOperandMapper.mapLiteralString(tokens, scope);

        return new SPIRVOpString(operand1, operand2);
    }

    private static SPIRVInstruction createOpLine(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);

        return new SPIRVOpLine(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpExtension(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVLiteralString operand1 = SPIRVOperandMapper.mapLiteralString(tokens, scope);

        return new SPIRVOpExtension(operand1);
    }

    private static SPIRVInstruction createOpExtInstImport(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralString operand2 = SPIRVOperandMapper.mapLiteralString(tokens, scope);

        return new SPIRVOpExtInstImport(operand1, operand2);
    }

    private static SPIRVInstruction createOpExtInst(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralExtInstInteger operand4 = SPIRVOperandMapper.mapLiteralExtInstInteger(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand5 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand5.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpExtInst(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpMemoryModel(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVAddressingModel operand1 = SPIRVOperandMapper.mapAddressingModel(tokens, scope);
        SPIRVMemoryModel operand2 = SPIRVOperandMapper.mapMemoryModel(tokens, scope);

        return new SPIRVOpMemoryModel(operand1, operand2);
    }

    private static SPIRVInstruction createOpEntryPoint(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVExecutionModel operand1 = SPIRVOperandMapper.mapExecutionModel(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralString operand3 = SPIRVOperandMapper.mapLiteralString(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand4 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand4.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpEntryPoint(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpExecutionMode(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVExecutionMode operand2 = SPIRVOperandMapper.mapExecutionMode(tokens, scope);

        return new SPIRVOpExecutionMode(operand1, operand2);
    }

    private static SPIRVInstruction createOpCapability(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVCapability operand1 = SPIRVOperandMapper.mapCapability(tokens, scope);

        return new SPIRVOpCapability(operand1);
    }

    private static SPIRVInstruction createOpTypeVoid(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeVoid(operand1);
    }

    private static SPIRVInstruction createOpTypeBool(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeBool(operand1);
    }

    private static SPIRVInstruction createOpTypeInt(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);

        return new SPIRVOpTypeInt(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeFloat(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);

        return new SPIRVOpTypeFloat(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypeVector(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);

        return new SPIRVOpTypeVector(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeMatrix(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);

        return new SPIRVOpTypeMatrix(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeImage(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVDim operand3 = SPIRVOperandMapper.mapDim(tokens, scope);
        SPIRVLiteralInteger operand4 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVLiteralInteger operand5 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVLiteralInteger operand6 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVLiteralInteger operand7 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVImageFormat operand8 = SPIRVOperandMapper.mapImageFormat(tokens, scope);
        SPIRVOptionalOperand<SPIRVAccessQualifier> operand9 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand9.setValue(SPIRVOperandMapper.mapAccessQualifier(tokens, scope));

        return new SPIRVOpTypeImage(operand1, operand2, operand3, operand4, operand5, operand6, operand7, operand8, operand9);
    }

    private static SPIRVInstruction createOpTypeSampler(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeSampler(operand1);
    }

    private static SPIRVInstruction createOpTypeSampledImage(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeSampledImage(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypeArray(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeArray(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeRuntimeArray(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeRuntimeArray(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypeStruct(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand2 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand2.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpTypeStruct(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypeOpaque(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralString operand2 = SPIRVOperandMapper.mapLiteralString(tokens, scope);

        return new SPIRVOpTypeOpaque(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypePointer(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVStorageClass operand2 = SPIRVOperandMapper.mapStorageClass(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypePointer(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeFunction(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand3 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand3.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpTypeFunction(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeEvent(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeEvent(operand1);
    }

    private static SPIRVInstruction createOpTypeDeviceEvent(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeDeviceEvent(operand1);
    }

    private static SPIRVInstruction createOpTypeReserveId(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeReserveId(operand1);
    }

    private static SPIRVInstruction createOpTypeQueue(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeQueue(operand1);
    }

    private static SPIRVInstruction createOpTypePipe(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVAccessQualifier operand2 = SPIRVOperandMapper.mapAccessQualifier(tokens, scope);

        return new SPIRVOpTypePipe(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypeForwardPointer(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVStorageClass operand2 = SPIRVOperandMapper.mapStorageClass(tokens, scope);

        return new SPIRVOpTypeForwardPointer(operand1, operand2);
    }

    private static SPIRVInstruction createOpConstantTrue(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpConstantTrue(operand2, operand1);
    }

    private static SPIRVInstruction createOpConstantFalse(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpConstantFalse(operand2, operand1);
    }

    private static SPIRVInstruction createOpConstant(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralContextDependentNumber operand3 = SPIRVOperandMapper.mapLiteralContextDependentNumber(tokens, scope, operand2);

        return new SPIRVOpConstant(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpConstantComposite(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand3 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand3.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpConstantComposite(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpConstantSampler(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVSamplerAddressingMode operand3 = SPIRVOperandMapper.mapSamplerAddressingMode(tokens, scope);
        SPIRVLiteralInteger operand4 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVSamplerFilterMode operand5 = SPIRVOperandMapper.mapSamplerFilterMode(tokens, scope);

        return new SPIRVOpConstantSampler(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpConstantNull(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpConstantNull(operand2, operand1);
    }

    private static SPIRVInstruction createOpSpecConstantTrue(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSpecConstantTrue(operand2, operand1);
    }

    private static SPIRVInstruction createOpSpecConstantFalse(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSpecConstantFalse(operand2, operand1);
    }

    private static SPIRVInstruction createOpSpecConstant(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralContextDependentNumber operand3 = SPIRVOperandMapper.mapLiteralContextDependentNumber(tokens, scope, operand2);

        return new SPIRVOpSpecConstant(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSpecConstantComposite(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand3 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand3.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpSpecConstantComposite(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSpecConstantOp(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralSpecConstantOpInteger operand3 = SPIRVOperandMapper.mapLiteralSpecConstantOpInteger(tokens, scope);

        return new SPIRVOpSpecConstantOp(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpFunction(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVFunctionControl operand3 = SPIRVOperandMapper.mapFunctionControl(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFunction(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFunctionParameter(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFunctionParameter(operand2, operand1);
    }

    private static SPIRVInstruction createOpFunctionEnd(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {

        return new SPIRVOpFunctionEnd();
    }

    private static SPIRVInstruction createOpFunctionCall(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand4 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand4.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpFunctionCall(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpVariable(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVStorageClass operand3 = SPIRVOperandMapper.mapStorageClass(tokens, scope);
        SPIRVOptionalOperand<SPIRVId> operand4 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand4.setValue(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpVariable(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpImageTexelPointer(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpImageTexelPointer(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpLoad(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVMemoryAccess> operand4 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand4.setValue(SPIRVOperandMapper.mapMemoryAccess(tokens, scope));

        return new SPIRVOpLoad(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpStore(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVMemoryAccess> operand3 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand3.setValue(SPIRVOperandMapper.mapMemoryAccess(tokens, scope));

        return new SPIRVOpStore(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpCopyMemory(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVMemoryAccess> operand3 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand3.setValue(SPIRVOperandMapper.mapMemoryAccess(tokens, scope));

        return new SPIRVOpCopyMemory(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpCopyMemorySized(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVMemoryAccess> operand4 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand4.setValue(SPIRVOperandMapper.mapMemoryAccess(tokens, scope));

        return new SPIRVOpCopyMemorySized(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpAccessChain(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand4 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand4.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpAccessChain(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpInBoundsAccessChain(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand4 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand4.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpInBoundsAccessChain(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpPtrAccessChain(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand5 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand5.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpPtrAccessChain(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpArrayLength(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand4 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);

        return new SPIRVOpArrayLength(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpGenericPtrMemSemantics(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGenericPtrMemSemantics(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpInBoundsPtrAccessChain(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand5 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand5.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpInBoundsPtrAccessChain(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpDecorate(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVDecoration operand2 = SPIRVOperandMapper.mapDecoration(tokens, scope);

        return new SPIRVOpDecorate(operand1, operand2);
    }

    private static SPIRVInstruction createOpMemberDecorate(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVDecoration operand3 = SPIRVOperandMapper.mapDecoration(tokens, scope);

        return new SPIRVOpMemberDecorate(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpDecorationGroup(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpDecorationGroup(operand1);
    }

    private static SPIRVInstruction createOpGroupDecorate(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand2 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand2.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpGroupDecorate(operand1, operand2);
    }

    private static SPIRVInstruction createOpGroupMemberDecorate(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVPairIdRefLiteralInteger> operand2 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand2.add(SPIRVOperandMapper.mapPairIdRefLiteralInteger(tokens, scope));

        return new SPIRVOpGroupMemberDecorate(operand1, operand2);
    }

    private static SPIRVInstruction createOpVectorExtractDynamic(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpVectorExtractDynamic(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpVectorInsertDynamic(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpVectorInsertDynamic(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpVectorShuffle(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVLiteralInteger> operand5 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand5.add(SPIRVOperandMapper.mapLiteralInteger(tokens, scope));

        return new SPIRVOpVectorShuffle(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpCompositeConstruct(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand3 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand3.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpCompositeConstruct(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpCompositeExtract(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVLiteralInteger> operand4 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand4.add(SPIRVOperandMapper.mapLiteralInteger(tokens, scope));

        return new SPIRVOpCompositeExtract(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpCompositeInsert(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVLiteralInteger> operand5 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand5.add(SPIRVOperandMapper.mapLiteralInteger(tokens, scope));

        return new SPIRVOpCompositeInsert(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpCopyObject(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpCopyObject(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpTranspose(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTranspose(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSampledImage(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSampledImage(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpImageSampleImplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSampleImplicitLod(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSampleExplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVImageOperands operand5 = SPIRVOperandMapper.mapImageOperands(tokens, scope);

        return new SPIRVOpImageSampleExplicitLod(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSampleDrefImplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSampleDrefImplicitLod(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSampleDrefExplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVImageOperands operand6 = SPIRVOperandMapper.mapImageOperands(tokens, scope);

        return new SPIRVOpImageSampleDrefExplicitLod(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSampleProjImplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSampleProjImplicitLod(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSampleProjExplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVImageOperands operand5 = SPIRVOperandMapper.mapImageOperands(tokens, scope);

        return new SPIRVOpImageSampleProjExplicitLod(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSampleProjDrefImplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSampleProjDrefImplicitLod(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSampleProjDrefExplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVImageOperands operand6 = SPIRVOperandMapper.mapImageOperands(tokens, scope);

        return new SPIRVOpImageSampleProjDrefExplicitLod(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageFetch(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageFetch(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageGather(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageGather(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageDrefGather(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageDrefGather(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageRead(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageRead(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageWrite(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand4 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand4.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageWrite(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpImage(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpImage(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpImageQueryFormat(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpImageQueryFormat(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpImageQueryOrder(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpImageQueryOrder(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpImageQuerySizeLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpImageQuerySizeLod(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpImageQuerySize(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpImageQuerySize(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpImageQueryLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpImageQueryLod(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpImageQueryLevels(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpImageQueryLevels(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpImageQuerySamples(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpImageQuerySamples(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpConvertFToU(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpConvertFToU(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpConvertFToS(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpConvertFToS(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpConvertSToF(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpConvertSToF(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpConvertUToF(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpConvertUToF(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpUConvert(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpUConvert(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSConvert(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSConvert(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpFConvert(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFConvert(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpQuantizeToF16(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpQuantizeToF16(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpConvertPtrToU(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpConvertPtrToU(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSatConvertSToU(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSatConvertSToU(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSatConvertUToS(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSatConvertUToS(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpConvertUToPtr(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpConvertUToPtr(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpPtrCastToGeneric(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpPtrCastToGeneric(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpGenericCastToPtr(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGenericCastToPtr(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpGenericCastToPtrExplicit(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVStorageClass operand4 = SPIRVOperandMapper.mapStorageClass(tokens, scope);

        return new SPIRVOpGenericCastToPtrExplicit(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpBitcast(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBitcast(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSNegate(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSNegate(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpFNegate(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFNegate(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpIAdd(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpIAdd(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFAdd(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFAdd(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpISub(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpISub(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFSub(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFSub(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpIMul(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpIMul(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFMul(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFMul(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpUDiv(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpUDiv(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSDiv(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSDiv(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFDiv(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFDiv(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpUMod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpUMod(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSRem(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSRem(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSMod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSMod(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFRem(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFRem(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFMod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFMod(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpVectorTimesScalar(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpVectorTimesScalar(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpMatrixTimesScalar(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpMatrixTimesScalar(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpVectorTimesMatrix(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpVectorTimesMatrix(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpMatrixTimesVector(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpMatrixTimesVector(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpMatrixTimesMatrix(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpMatrixTimesMatrix(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpOuterProduct(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpOuterProduct(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpDot(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpDot(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpIAddCarry(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpIAddCarry(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpISubBorrow(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpISubBorrow(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpUMulExtended(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpUMulExtended(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSMulExtended(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSMulExtended(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpAny(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAny(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpAll(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAll(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpIsNan(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpIsNan(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpIsInf(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpIsInf(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpIsFinite(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpIsFinite(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpIsNormal(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpIsNormal(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSignBitSet(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSignBitSet(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpLessOrGreater(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpLessOrGreater(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpOrdered(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpOrdered(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpUnordered(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpUnordered(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpLogicalEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpLogicalEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpLogicalNotEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpLogicalNotEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpLogicalOr(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpLogicalOr(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpLogicalAnd(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpLogicalAnd(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpLogicalNot(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpLogicalNot(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSelect(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSelect(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpIEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpIEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpINotEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpINotEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpUGreaterThan(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpUGreaterThan(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSGreaterThan(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSGreaterThan(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpUGreaterThanEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpUGreaterThanEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSGreaterThanEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSGreaterThanEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpULessThan(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpULessThan(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSLessThan(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSLessThan(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpULessThanEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpULessThanEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSLessThanEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSLessThanEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFOrdEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFUnordEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdNotEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFOrdNotEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordNotEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFUnordNotEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdLessThan(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFOrdLessThan(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordLessThan(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFUnordLessThan(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdGreaterThan(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFOrdGreaterThan(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordGreaterThan(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFUnordGreaterThan(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdLessThanEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFOrdLessThanEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordLessThanEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFUnordLessThanEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdGreaterThanEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFOrdGreaterThanEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordGreaterThanEqual(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFUnordGreaterThanEqual(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpShiftRightLogical(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpShiftRightLogical(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpShiftRightArithmetic(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpShiftRightArithmetic(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpShiftLeftLogical(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpShiftLeftLogical(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpBitwiseOr(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBitwiseOr(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpBitwiseXor(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBitwiseXor(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpBitwiseAnd(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBitwiseAnd(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpNot(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpNot(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpBitFieldInsert(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBitFieldInsert(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpBitFieldSExtract(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBitFieldSExtract(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpBitFieldUExtract(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBitFieldUExtract(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpBitReverse(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBitReverse(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpBitCount(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBitCount(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpDPdx(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpDPdx(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpDPdy(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpDPdy(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpFwidth(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFwidth(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpDPdxFine(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpDPdxFine(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpDPdyFine(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpDPdyFine(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpFwidthFine(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFwidthFine(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpDPdxCoarse(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpDPdxCoarse(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpDPdyCoarse(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpDPdyCoarse(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpFwidthCoarse(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFwidthCoarse(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpEmitVertex(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {

        return new SPIRVOpEmitVertex();
    }

    private static SPIRVInstruction createOpEndPrimitive(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {

        return new SPIRVOpEndPrimitive();
    }

    private static SPIRVInstruction createOpEmitStreamVertex(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpEmitStreamVertex(operand1);
    }

    private static SPIRVInstruction createOpEndStreamPrimitive(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpEndStreamPrimitive(operand1);
    }

    private static SPIRVInstruction createOpControlBarrier(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpControlBarrier(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpMemoryBarrier(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpMemoryBarrier(operand1, operand2);
    }

    private static SPIRVInstruction createOpAtomicLoad(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicLoad(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpAtomicStore(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicStore(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpAtomicExchange(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicExchange(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicCompareExchange(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicCompareExchange(operand2, operand1, operand3, operand4, operand5, operand6, operand7, operand8);
    }

    private static SPIRVInstruction createOpAtomicCompareExchangeWeak(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicCompareExchangeWeak(operand2, operand1, operand3, operand4, operand5, operand6, operand7, operand8);
    }

    private static SPIRVInstruction createOpAtomicIIncrement(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicIIncrement(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpAtomicIDecrement(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicIDecrement(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpAtomicIAdd(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicIAdd(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicISub(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicISub(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicSMin(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicSMin(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicUMin(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicUMin(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicSMax(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicSMax(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicUMax(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicUMax(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicAnd(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicAnd(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicOr(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicOr(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicXor(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicXor(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpPhi(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVPairIdRefIdRef> operand3 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand3.add(SPIRVOperandMapper.mapPairIdRefIdRef(tokens, scope));

        return new SPIRVOpPhi(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpLoopMerge(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLoopControl operand3 = SPIRVOperandMapper.mapLoopControl(tokens, scope);

        return new SPIRVOpLoopMerge(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSelectionMerge(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVSelectionControl operand2 = SPIRVOperandMapper.mapSelectionControl(tokens, scope);

        return new SPIRVOpSelectionMerge(operand1, operand2);
    }

    private static SPIRVInstruction createOpLabel(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpLabel(operand1);
    }

    private static SPIRVInstruction createOpBranch(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBranch(operand1);
    }

    private static SPIRVInstruction createOpBranchConditional(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVLiteralInteger> operand4 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand4.add(SPIRVOperandMapper.mapLiteralInteger(tokens, scope));

        return new SPIRVOpBranchConditional(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSwitch(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVPairLiteralIntegerIdRef> operand3 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand3.add(SPIRVOperandMapper.mapPairLiteralIntegerIdRef(tokens, scope));

        return new SPIRVOpSwitch(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpKill(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {

        return new SPIRVOpKill();
    }

    private static SPIRVInstruction createOpReturn(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {

        return new SPIRVOpReturn();
    }

    private static SPIRVInstruction createOpReturnValue(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpReturnValue(operand1);
    }

    private static SPIRVInstruction createOpUnreachable(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {

        return new SPIRVOpUnreachable();
    }

    private static SPIRVInstruction createOpLifetimeStart(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);

        return new SPIRVOpLifetimeStart(operand1, operand2);
    }

    private static SPIRVInstruction createOpLifetimeStop(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);

        return new SPIRVOpLifetimeStop(operand1, operand2);
    }

    private static SPIRVInstruction createOpGroupAsyncCopy(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupAsyncCopy(operand2, operand1, operand3, operand4, operand5, operand6, operand7, operand8);
    }

    private static SPIRVInstruction createOpGroupWaitEvents(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupWaitEvents(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpGroupAll(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupAll(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpGroupAny(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupAny(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpGroupBroadcast(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupBroadcast(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupIAdd(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupIAdd(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFAdd(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupFAdd(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFMin(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupFMin(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupUMin(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupUMin(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupSMin(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupSMin(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFMax(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupFMax(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupUMax(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupUMax(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupSMax(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupSMax(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpReadPipe(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpReadPipe(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpWritePipe(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpWritePipe(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpReservedReadPipe(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpReservedReadPipe(operand2, operand1, operand3, operand4, operand5, operand6, operand7, operand8);
    }

    private static SPIRVInstruction createOpReservedWritePipe(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpReservedWritePipe(operand2, operand1, operand3, operand4, operand5, operand6, operand7, operand8);
    }

    private static SPIRVInstruction createOpReserveReadPipePackets(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpReserveReadPipePackets(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpReserveWritePipePackets(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpReserveWritePipePackets(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpCommitReadPipe(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpCommitReadPipe(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpCommitWritePipe(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpCommitWritePipe(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpIsValidReserveId(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpIsValidReserveId(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpGetNumPipePackets(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGetNumPipePackets(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGetMaxPipePackets(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGetMaxPipePackets(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupReserveReadPipePackets(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupReserveReadPipePackets(operand2, operand1, operand3, operand4, operand5, operand6, operand7);
    }

    private static SPIRVInstruction createOpGroupReserveWritePipePackets(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupReserveWritePipePackets(operand2, operand1, operand3, operand4, operand5, operand6, operand7);
    }

    private static SPIRVInstruction createOpGroupCommitReadPipe(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupCommitReadPipe(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupCommitWritePipe(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupCommitWritePipe(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpEnqueueMarker(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpEnqueueMarker(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpEnqueueKernel(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand9 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand10 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand11 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand12 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVMultipleOperands<SPIRVId> operand13 = new SPIRVMultipleOperands<>();
        while (tokens.hasNext()) operand13.add(SPIRVOperandMapper.mapId(tokens, scope));

        return new SPIRVOpEnqueueKernel(operand2, operand1, operand3, operand4, operand5, operand6, operand7, operand8, operand9, operand10, operand11, operand12, operand13);
    }

    private static SPIRVInstruction createOpGetKernelNDrangeSubGroupCount(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGetKernelNDrangeSubGroupCount(operand2, operand1, operand3, operand4, operand5, operand6, operand7);
    }

    private static SPIRVInstruction createOpGetKernelNDrangeMaxSubGroupSize(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGetKernelNDrangeMaxSubGroupSize(operand2, operand1, operand3, operand4, operand5, operand6, operand7);
    }

    private static SPIRVInstruction createOpGetKernelWorkGroupSize(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGetKernelWorkGroupSize(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpGetKernelPreferredWorkGroupSizeMultiple(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGetKernelPreferredWorkGroupSizeMultiple(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpRetainEvent(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpRetainEvent(operand1);
    }

    private static SPIRVInstruction createOpReleaseEvent(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpReleaseEvent(operand1);
    }

    private static SPIRVInstruction createOpCreateUserEvent(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpCreateUserEvent(operand2, operand1);
    }

    private static SPIRVInstruction createOpIsValidEvent(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpIsValidEvent(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSetUserEventStatus(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSetUserEventStatus(operand1, operand2);
    }

    private static SPIRVInstruction createOpCaptureEventProfilingInfo(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpCaptureEventProfilingInfo(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpGetDefaultQueue(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGetDefaultQueue(operand2, operand1);
    }

    private static SPIRVInstruction createOpBuildNDRange(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpBuildNDRange(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseSampleImplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSparseSampleImplicitLod(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseSampleExplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVImageOperands operand5 = SPIRVOperandMapper.mapImageOperands(tokens, scope);

        return new SPIRVOpImageSparseSampleExplicitLod(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseSampleDrefImplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSparseSampleDrefImplicitLod(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseSampleDrefExplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVImageOperands operand6 = SPIRVOperandMapper.mapImageOperands(tokens, scope);

        return new SPIRVOpImageSparseSampleDrefExplicitLod(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseSampleProjImplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSparseSampleProjImplicitLod(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseSampleProjExplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVImageOperands operand5 = SPIRVOperandMapper.mapImageOperands(tokens, scope);

        return new SPIRVOpImageSparseSampleProjExplicitLod(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseSampleProjDrefImplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSparseSampleProjDrefImplicitLod(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseSampleProjDrefExplicitLod(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVImageOperands operand6 = SPIRVOperandMapper.mapImageOperands(tokens, scope);

        return new SPIRVOpImageSparseSampleProjDrefExplicitLod(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseFetch(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSparseFetch(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseGather(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSparseGather(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseDrefGather(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSparseDrefGather(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseTexelsResident(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpImageSparseTexelsResident(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpNoLine(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {

        return new SPIRVOpNoLine();
    }

    private static SPIRVInstruction createOpAtomicFlagTestAndSet(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicFlagTestAndSet(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpAtomicFlagClear(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpAtomicFlagClear(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpImageSparseRead(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (tokens.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(tokens, scope));

        return new SPIRVOpImageSparseRead(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpSizeOf(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSizeOf(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpTypePipeStorage(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypePipeStorage(operand1);
    }

    private static SPIRVInstruction createOpConstantPipeStorage(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVLiteralInteger operand4 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVLiteralInteger operand5 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);

        return new SPIRVOpConstantPipeStorage(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpCreatePipeFromPipeStorage(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpCreatePipeFromPipeStorage(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpGetKernelLocalSizeForSubgroupCount(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGetKernelLocalSizeForSubgroupCount(operand2, operand1, operand3, operand4, operand5, operand6, operand7);
    }

    private static SPIRVInstruction createOpGetKernelMaxNumSubgroups(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGetKernelMaxNumSubgroups(operand2, operand1, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpTypeNamedBarrier(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpTypeNamedBarrier(operand1);
    }

    private static SPIRVInstruction createOpNamedBarrierInitialize(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpNamedBarrierInitialize(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpMemoryNamedBarrier(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpMemoryNamedBarrier(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpModuleProcessed(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVLiteralString operand1 = SPIRVOperandMapper.mapLiteralString(tokens, scope);

        return new SPIRVOpModuleProcessed(operand1);
    }

    private static SPIRVInstruction createOpExecutionModeId(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVExecutionMode operand2 = SPIRVOperandMapper.mapExecutionMode(tokens, scope);

        return new SPIRVOpExecutionModeId(operand1, operand2);
    }

    private static SPIRVInstruction createOpDecorateId(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVDecoration operand2 = SPIRVOperandMapper.mapDecoration(tokens, scope);

        return new SPIRVOpDecorateId(operand1, operand2);
    }

    private static SPIRVInstruction createOpSubgroupBallotKHR(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupBallotKHR(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSubgroupFirstInvocationKHR(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupFirstInvocationKHR(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSubgroupAllKHR(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupAllKHR(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSubgroupAnyKHR(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupAnyKHR(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSubgroupAllEqualKHR(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupAllEqualKHR(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSubgroupReadInvocationKHR(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupReadInvocationKHR(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpGroupIAddNonUniformAMD(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupIAddNonUniformAMD(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFAddNonUniformAMD(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupFAddNonUniformAMD(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFMinNonUniformAMD(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupFMinNonUniformAMD(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupUMinNonUniformAMD(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupUMinNonUniformAMD(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupSMinNonUniformAMD(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupSMinNonUniformAMD(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFMaxNonUniformAMD(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupFMaxNonUniformAMD(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupUMaxNonUniformAMD(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupUMaxNonUniformAMD(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupSMaxNonUniformAMD(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpGroupSMaxNonUniformAMD(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpFragmentMaskFetchAMD(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFragmentMaskFetchAMD(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpFragmentFetchAMD(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpFragmentFetchAMD(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpSubgroupShuffleINTEL(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupShuffleINTEL(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSubgroupShuffleDownINTEL(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupShuffleDownINTEL(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpSubgroupShuffleUpINTEL(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupShuffleUpINTEL(operand2, operand1, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpSubgroupShuffleXorINTEL(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupShuffleXorINTEL(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSubgroupBlockReadINTEL(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupBlockReadINTEL(operand2, operand1, operand3);
    }

    private static SPIRVInstruction createOpSubgroupBlockWriteINTEL(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupBlockWriteINTEL(operand1, operand2);
    }

    private static SPIRVInstruction createOpSubgroupImageBlockReadINTEL(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupImageBlockReadINTEL(operand2, operand1, operand3, operand4);
    }

    private static SPIRVInstruction createOpSubgroupImageBlockWriteINTEL(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(tokens, scope);

        return new SPIRVOpSubgroupImageBlockWriteINTEL(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpDecorateStringGOOGLE(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVDecoration operand2 = SPIRVOperandMapper.mapDecoration(tokens, scope);

        return new SPIRVOpDecorateStringGOOGLE(operand1, operand2);
    }

    private static SPIRVInstruction createOpMemberDecorateStringGOOGLE(Iterator<SPIRVToken> tokens, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(tokens, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(tokens, scope);
        SPIRVDecoration operand3 = SPIRVOperandMapper.mapDecoration(tokens, scope);

        return new SPIRVOpMemberDecorateStringGOOGLE(operand1, operand2, operand3);
    }

}

