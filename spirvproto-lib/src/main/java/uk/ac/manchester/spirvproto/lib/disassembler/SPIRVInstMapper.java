package uk.ac.manchester.spirvproto.lib.disassembler;

import uk.ac.manchester.spirvproto.lib.SPIRVInstScope;
import uk.ac.manchester.spirvproto.lib.instructions.*;
import uk.ac.manchester.spirvproto.lib.instructions.operands.*;

import java.util.Arrays;
import java.util.Iterator;
import javax.annotation.Generated;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVInstMapper {
    public static SPIRVInstruction createInst(SPIRVLine line, SPIRVInstScope scope) throws InvalidSPIRVOpcodeException {
        SPIRVInstruction instruction;
        int opCode = line.next();
        switch (opCode) {
            case 0: instruction = createOpNop(line, scope); break;
            case 1: instruction = createOpUndef(line, scope); break;
            case 2: instruction = createOpSourceContinued(line, scope); break;
            case 3: instruction = createOpSource(line, scope); break;
            case 4: instruction = createOpSourceExtension(line, scope); break;
            case 5: instruction = createOpName(line, scope); break;
            case 6: instruction = createOpMemberName(line, scope); break;
            case 7: instruction = createOpString(line, scope); break;
            case 8: instruction = createOpLine(line, scope); break;
            case 10: instruction = createOpExtension(line, scope); break;
            case 11: instruction = createOpExtInstImport(line, scope); break;
            case 12: instruction = createOpExtInst(line, scope); break;
            case 14: instruction = createOpMemoryModel(line, scope); break;
            case 15: instruction = createOpEntryPoint(line, scope); break;
            case 16: instruction = createOpExecutionMode(line, scope); break;
            case 17: instruction = createOpCapability(line, scope); break;
            case 19: instruction = createOpTypeVoid(line, scope); break;
            case 20: instruction = createOpTypeBool(line, scope); break;
            case 21: instruction = createOpTypeInt(line, scope); break;
            case 22: instruction = createOpTypeFloat(line, scope); break;
            case 23: instruction = createOpTypeVector(line, scope); break;
            case 24: instruction = createOpTypeMatrix(line, scope); break;
            case 25: instruction = createOpTypeImage(line, scope); break;
            case 26: instruction = createOpTypeSampler(line, scope); break;
            case 27: instruction = createOpTypeSampledImage(line, scope); break;
            case 28: instruction = createOpTypeArray(line, scope); break;
            case 29: instruction = createOpTypeRuntimeArray(line, scope); break;
            case 30: instruction = createOpTypeStruct(line, scope); break;
            case 31: instruction = createOpTypeOpaque(line, scope); break;
            case 32: instruction = createOpTypePointer(line, scope); break;
            case 33: instruction = createOpTypeFunction(line, scope); break;
            case 34: instruction = createOpTypeEvent(line, scope); break;
            case 35: instruction = createOpTypeDeviceEvent(line, scope); break;
            case 36: instruction = createOpTypeReserveId(line, scope); break;
            case 37: instruction = createOpTypeQueue(line, scope); break;
            case 38: instruction = createOpTypePipe(line, scope); break;
            case 39: instruction = createOpTypeForwardPointer(line, scope); break;
            case 41: instruction = createOpConstantTrue(line, scope); break;
            case 42: instruction = createOpConstantFalse(line, scope); break;
            case 43: instruction = createOpConstant(line, scope); break;
            case 44: instruction = createOpConstantComposite(line, scope); break;
            case 45: instruction = createOpConstantSampler(line, scope); break;
            case 46: instruction = createOpConstantNull(line, scope); break;
            case 48: instruction = createOpSpecConstantTrue(line, scope); break;
            case 49: instruction = createOpSpecConstantFalse(line, scope); break;
            case 50: instruction = createOpSpecConstant(line, scope); break;
            case 51: instruction = createOpSpecConstantComposite(line, scope); break;
            case 52: instruction = createOpSpecConstantOp(line, scope); break;
            case 54: instruction = createOpFunction(line, scope); break;
            case 55: instruction = createOpFunctionParameter(line, scope); break;
            case 56: instruction = createOpFunctionEnd(line, scope); break;
            case 57: instruction = createOpFunctionCall(line, scope); break;
            case 59: instruction = createOpVariable(line, scope); break;
            case 60: instruction = createOpImageTexelPointer(line, scope); break;
            case 61: instruction = createOpLoad(line, scope); break;
            case 62: instruction = createOpStore(line, scope); break;
            case 63: instruction = createOpCopyMemory(line, scope); break;
            case 64: instruction = createOpCopyMemorySized(line, scope); break;
            case 65: instruction = createOpAccessChain(line, scope); break;
            case 66: instruction = createOpInBoundsAccessChain(line, scope); break;
            case 67: instruction = createOpPtrAccessChain(line, scope); break;
            case 68: instruction = createOpArrayLength(line, scope); break;
            case 69: instruction = createOpGenericPtrMemSemantics(line, scope); break;
            case 70: instruction = createOpInBoundsPtrAccessChain(line, scope); break;
            case 71: instruction = createOpDecorate(line, scope); break;
            case 72: instruction = createOpMemberDecorate(line, scope); break;
            case 73: instruction = createOpDecorationGroup(line, scope); break;
            case 74: instruction = createOpGroupDecorate(line, scope); break;
            case 75: instruction = createOpGroupMemberDecorate(line, scope); break;
            case 77: instruction = createOpVectorExtractDynamic(line, scope); break;
            case 78: instruction = createOpVectorInsertDynamic(line, scope); break;
            case 79: instruction = createOpVectorShuffle(line, scope); break;
            case 80: instruction = createOpCompositeConstruct(line, scope); break;
            case 81: instruction = createOpCompositeExtract(line, scope); break;
            case 82: instruction = createOpCompositeInsert(line, scope); break;
            case 83: instruction = createOpCopyObject(line, scope); break;
            case 84: instruction = createOpTranspose(line, scope); break;
            case 86: instruction = createOpSampledImage(line, scope); break;
            case 87: instruction = createOpImageSampleImplicitLod(line, scope); break;
            case 88: instruction = createOpImageSampleExplicitLod(line, scope); break;
            case 89: instruction = createOpImageSampleDrefImplicitLod(line, scope); break;
            case 90: instruction = createOpImageSampleDrefExplicitLod(line, scope); break;
            case 91: instruction = createOpImageSampleProjImplicitLod(line, scope); break;
            case 92: instruction = createOpImageSampleProjExplicitLod(line, scope); break;
            case 93: instruction = createOpImageSampleProjDrefImplicitLod(line, scope); break;
            case 94: instruction = createOpImageSampleProjDrefExplicitLod(line, scope); break;
            case 95: instruction = createOpImageFetch(line, scope); break;
            case 96: instruction = createOpImageGather(line, scope); break;
            case 97: instruction = createOpImageDrefGather(line, scope); break;
            case 98: instruction = createOpImageRead(line, scope); break;
            case 99: instruction = createOpImageWrite(line, scope); break;
            case 100: instruction = createOpImage(line, scope); break;
            case 101: instruction = createOpImageQueryFormat(line, scope); break;
            case 102: instruction = createOpImageQueryOrder(line, scope); break;
            case 103: instruction = createOpImageQuerySizeLod(line, scope); break;
            case 104: instruction = createOpImageQuerySize(line, scope); break;
            case 105: instruction = createOpImageQueryLod(line, scope); break;
            case 106: instruction = createOpImageQueryLevels(line, scope); break;
            case 107: instruction = createOpImageQuerySamples(line, scope); break;
            case 109: instruction = createOpConvertFToU(line, scope); break;
            case 110: instruction = createOpConvertFToS(line, scope); break;
            case 111: instruction = createOpConvertSToF(line, scope); break;
            case 112: instruction = createOpConvertUToF(line, scope); break;
            case 113: instruction = createOpUConvert(line, scope); break;
            case 114: instruction = createOpSConvert(line, scope); break;
            case 115: instruction = createOpFConvert(line, scope); break;
            case 116: instruction = createOpQuantizeToF16(line, scope); break;
            case 117: instruction = createOpConvertPtrToU(line, scope); break;
            case 118: instruction = createOpSatConvertSToU(line, scope); break;
            case 119: instruction = createOpSatConvertUToS(line, scope); break;
            case 120: instruction = createOpConvertUToPtr(line, scope); break;
            case 121: instruction = createOpPtrCastToGeneric(line, scope); break;
            case 122: instruction = createOpGenericCastToPtr(line, scope); break;
            case 123: instruction = createOpGenericCastToPtrExplicit(line, scope); break;
            case 124: instruction = createOpBitcast(line, scope); break;
            case 126: instruction = createOpSNegate(line, scope); break;
            case 127: instruction = createOpFNegate(line, scope); break;
            case 128: instruction = createOpIAdd(line, scope); break;
            case 129: instruction = createOpFAdd(line, scope); break;
            case 130: instruction = createOpISub(line, scope); break;
            case 131: instruction = createOpFSub(line, scope); break;
            case 132: instruction = createOpIMul(line, scope); break;
            case 133: instruction = createOpFMul(line, scope); break;
            case 134: instruction = createOpUDiv(line, scope); break;
            case 135: instruction = createOpSDiv(line, scope); break;
            case 136: instruction = createOpFDiv(line, scope); break;
            case 137: instruction = createOpUMod(line, scope); break;
            case 138: instruction = createOpSRem(line, scope); break;
            case 139: instruction = createOpSMod(line, scope); break;
            case 140: instruction = createOpFRem(line, scope); break;
            case 141: instruction = createOpFMod(line, scope); break;
            case 142: instruction = createOpVectorTimesScalar(line, scope); break;
            case 143: instruction = createOpMatrixTimesScalar(line, scope); break;
            case 144: instruction = createOpVectorTimesMatrix(line, scope); break;
            case 145: instruction = createOpMatrixTimesVector(line, scope); break;
            case 146: instruction = createOpMatrixTimesMatrix(line, scope); break;
            case 147: instruction = createOpOuterProduct(line, scope); break;
            case 148: instruction = createOpDot(line, scope); break;
            case 149: instruction = createOpIAddCarry(line, scope); break;
            case 150: instruction = createOpISubBorrow(line, scope); break;
            case 151: instruction = createOpUMulExtended(line, scope); break;
            case 152: instruction = createOpSMulExtended(line, scope); break;
            case 154: instruction = createOpAny(line, scope); break;
            case 155: instruction = createOpAll(line, scope); break;
            case 156: instruction = createOpIsNan(line, scope); break;
            case 157: instruction = createOpIsInf(line, scope); break;
            case 158: instruction = createOpIsFinite(line, scope); break;
            case 159: instruction = createOpIsNormal(line, scope); break;
            case 160: instruction = createOpSignBitSet(line, scope); break;
            case 161: instruction = createOpLessOrGreater(line, scope); break;
            case 162: instruction = createOpOrdered(line, scope); break;
            case 163: instruction = createOpUnordered(line, scope); break;
            case 164: instruction = createOpLogicalEqual(line, scope); break;
            case 165: instruction = createOpLogicalNotEqual(line, scope); break;
            case 166: instruction = createOpLogicalOr(line, scope); break;
            case 167: instruction = createOpLogicalAnd(line, scope); break;
            case 168: instruction = createOpLogicalNot(line, scope); break;
            case 169: instruction = createOpSelect(line, scope); break;
            case 170: instruction = createOpIEqual(line, scope); break;
            case 171: instruction = createOpINotEqual(line, scope); break;
            case 172: instruction = createOpUGreaterThan(line, scope); break;
            case 173: instruction = createOpSGreaterThan(line, scope); break;
            case 174: instruction = createOpUGreaterThanEqual(line, scope); break;
            case 175: instruction = createOpSGreaterThanEqual(line, scope); break;
            case 176: instruction = createOpULessThan(line, scope); break;
            case 177: instruction = createOpSLessThan(line, scope); break;
            case 178: instruction = createOpULessThanEqual(line, scope); break;
            case 179: instruction = createOpSLessThanEqual(line, scope); break;
            case 180: instruction = createOpFOrdEqual(line, scope); break;
            case 181: instruction = createOpFUnordEqual(line, scope); break;
            case 182: instruction = createOpFOrdNotEqual(line, scope); break;
            case 183: instruction = createOpFUnordNotEqual(line, scope); break;
            case 184: instruction = createOpFOrdLessThan(line, scope); break;
            case 185: instruction = createOpFUnordLessThan(line, scope); break;
            case 186: instruction = createOpFOrdGreaterThan(line, scope); break;
            case 187: instruction = createOpFUnordGreaterThan(line, scope); break;
            case 188: instruction = createOpFOrdLessThanEqual(line, scope); break;
            case 189: instruction = createOpFUnordLessThanEqual(line, scope); break;
            case 190: instruction = createOpFOrdGreaterThanEqual(line, scope); break;
            case 191: instruction = createOpFUnordGreaterThanEqual(line, scope); break;
            case 194: instruction = createOpShiftRightLogical(line, scope); break;
            case 195: instruction = createOpShiftRightArithmetic(line, scope); break;
            case 196: instruction = createOpShiftLeftLogical(line, scope); break;
            case 197: instruction = createOpBitwiseOr(line, scope); break;
            case 198: instruction = createOpBitwiseXor(line, scope); break;
            case 199: instruction = createOpBitwiseAnd(line, scope); break;
            case 200: instruction = createOpNot(line, scope); break;
            case 201: instruction = createOpBitFieldInsert(line, scope); break;
            case 202: instruction = createOpBitFieldSExtract(line, scope); break;
            case 203: instruction = createOpBitFieldUExtract(line, scope); break;
            case 204: instruction = createOpBitReverse(line, scope); break;
            case 205: instruction = createOpBitCount(line, scope); break;
            case 207: instruction = createOpDPdx(line, scope); break;
            case 208: instruction = createOpDPdy(line, scope); break;
            case 209: instruction = createOpFwidth(line, scope); break;
            case 210: instruction = createOpDPdxFine(line, scope); break;
            case 211: instruction = createOpDPdyFine(line, scope); break;
            case 212: instruction = createOpFwidthFine(line, scope); break;
            case 213: instruction = createOpDPdxCoarse(line, scope); break;
            case 214: instruction = createOpDPdyCoarse(line, scope); break;
            case 215: instruction = createOpFwidthCoarse(line, scope); break;
            case 218: instruction = createOpEmitVertex(line, scope); break;
            case 219: instruction = createOpEndPrimitive(line, scope); break;
            case 220: instruction = createOpEmitStreamVertex(line, scope); break;
            case 221: instruction = createOpEndStreamPrimitive(line, scope); break;
            case 224: instruction = createOpControlBarrier(line, scope); break;
            case 225: instruction = createOpMemoryBarrier(line, scope); break;
            case 227: instruction = createOpAtomicLoad(line, scope); break;
            case 228: instruction = createOpAtomicStore(line, scope); break;
            case 229: instruction = createOpAtomicExchange(line, scope); break;
            case 230: instruction = createOpAtomicCompareExchange(line, scope); break;
            case 231: instruction = createOpAtomicCompareExchangeWeak(line, scope); break;
            case 232: instruction = createOpAtomicIIncrement(line, scope); break;
            case 233: instruction = createOpAtomicIDecrement(line, scope); break;
            case 234: instruction = createOpAtomicIAdd(line, scope); break;
            case 235: instruction = createOpAtomicISub(line, scope); break;
            case 236: instruction = createOpAtomicSMin(line, scope); break;
            case 237: instruction = createOpAtomicUMin(line, scope); break;
            case 238: instruction = createOpAtomicSMax(line, scope); break;
            case 239: instruction = createOpAtomicUMax(line, scope); break;
            case 240: instruction = createOpAtomicAnd(line, scope); break;
            case 241: instruction = createOpAtomicOr(line, scope); break;
            case 242: instruction = createOpAtomicXor(line, scope); break;
            case 245: instruction = createOpPhi(line, scope); break;
            case 246: instruction = createOpLoopMerge(line, scope); break;
            case 247: instruction = createOpSelectionMerge(line, scope); break;
            case 248: instruction = createOpLabel(line, scope); break;
            case 249: instruction = createOpBranch(line, scope); break;
            case 250: instruction = createOpBranchConditional(line, scope); break;
            case 251: instruction = createOpSwitch(line, scope); break;
            case 252: instruction = createOpKill(line, scope); break;
            case 253: instruction = createOpReturn(line, scope); break;
            case 254: instruction = createOpReturnValue(line, scope); break;
            case 255: instruction = createOpUnreachable(line, scope); break;
            case 256: instruction = createOpLifetimeStart(line, scope); break;
            case 257: instruction = createOpLifetimeStop(line, scope); break;
            case 259: instruction = createOpGroupAsyncCopy(line, scope); break;
            case 260: instruction = createOpGroupWaitEvents(line, scope); break;
            case 261: instruction = createOpGroupAll(line, scope); break;
            case 262: instruction = createOpGroupAny(line, scope); break;
            case 263: instruction = createOpGroupBroadcast(line, scope); break;
            case 264: instruction = createOpGroupIAdd(line, scope); break;
            case 265: instruction = createOpGroupFAdd(line, scope); break;
            case 266: instruction = createOpGroupFMin(line, scope); break;
            case 267: instruction = createOpGroupUMin(line, scope); break;
            case 268: instruction = createOpGroupSMin(line, scope); break;
            case 269: instruction = createOpGroupFMax(line, scope); break;
            case 270: instruction = createOpGroupUMax(line, scope); break;
            case 271: instruction = createOpGroupSMax(line, scope); break;
            case 274: instruction = createOpReadPipe(line, scope); break;
            case 275: instruction = createOpWritePipe(line, scope); break;
            case 276: instruction = createOpReservedReadPipe(line, scope); break;
            case 277: instruction = createOpReservedWritePipe(line, scope); break;
            case 278: instruction = createOpReserveReadPipePackets(line, scope); break;
            case 279: instruction = createOpReserveWritePipePackets(line, scope); break;
            case 280: instruction = createOpCommitReadPipe(line, scope); break;
            case 281: instruction = createOpCommitWritePipe(line, scope); break;
            case 282: instruction = createOpIsValidReserveId(line, scope); break;
            case 283: instruction = createOpGetNumPipePackets(line, scope); break;
            case 284: instruction = createOpGetMaxPipePackets(line, scope); break;
            case 285: instruction = createOpGroupReserveReadPipePackets(line, scope); break;
            case 286: instruction = createOpGroupReserveWritePipePackets(line, scope); break;
            case 287: instruction = createOpGroupCommitReadPipe(line, scope); break;
            case 288: instruction = createOpGroupCommitWritePipe(line, scope); break;
            case 291: instruction = createOpEnqueueMarker(line, scope); break;
            case 292: instruction = createOpEnqueueKernel(line, scope); break;
            case 293: instruction = createOpGetKernelNDrangeSubGroupCount(line, scope); break;
            case 294: instruction = createOpGetKernelNDrangeMaxSubGroupSize(line, scope); break;
            case 295: instruction = createOpGetKernelWorkGroupSize(line, scope); break;
            case 296: instruction = createOpGetKernelPreferredWorkGroupSizeMultiple(line, scope); break;
            case 297: instruction = createOpRetainEvent(line, scope); break;
            case 298: instruction = createOpReleaseEvent(line, scope); break;
            case 299: instruction = createOpCreateUserEvent(line, scope); break;
            case 300: instruction = createOpIsValidEvent(line, scope); break;
            case 301: instruction = createOpSetUserEventStatus(line, scope); break;
            case 302: instruction = createOpCaptureEventProfilingInfo(line, scope); break;
            case 303: instruction = createOpGetDefaultQueue(line, scope); break;
            case 304: instruction = createOpBuildNDRange(line, scope); break;
            case 305: instruction = createOpImageSparseSampleImplicitLod(line, scope); break;
            case 306: instruction = createOpImageSparseSampleExplicitLod(line, scope); break;
            case 307: instruction = createOpImageSparseSampleDrefImplicitLod(line, scope); break;
            case 308: instruction = createOpImageSparseSampleDrefExplicitLod(line, scope); break;
            case 309: instruction = createOpImageSparseSampleProjImplicitLod(line, scope); break;
            case 310: instruction = createOpImageSparseSampleProjExplicitLod(line, scope); break;
            case 311: instruction = createOpImageSparseSampleProjDrefImplicitLod(line, scope); break;
            case 312: instruction = createOpImageSparseSampleProjDrefExplicitLod(line, scope); break;
            case 313: instruction = createOpImageSparseFetch(line, scope); break;
            case 314: instruction = createOpImageSparseGather(line, scope); break;
            case 315: instruction = createOpImageSparseDrefGather(line, scope); break;
            case 316: instruction = createOpImageSparseTexelsResident(line, scope); break;
            case 317: instruction = createOpNoLine(line, scope); break;
            case 318: instruction = createOpAtomicFlagTestAndSet(line, scope); break;
            case 319: instruction = createOpAtomicFlagClear(line, scope); break;
            case 320: instruction = createOpImageSparseRead(line, scope); break;
            case 321: instruction = createOpSizeOf(line, scope); break;
            case 322: instruction = createOpTypePipeStorage(line, scope); break;
            case 323: instruction = createOpConstantPipeStorage(line, scope); break;
            case 324: instruction = createOpCreatePipeFromPipeStorage(line, scope); break;
            case 325: instruction = createOpGetKernelLocalSizeForSubgroupCount(line, scope); break;
            case 326: instruction = createOpGetKernelMaxNumSubgroups(line, scope); break;
            case 327: instruction = createOpTypeNamedBarrier(line, scope); break;
            case 328: instruction = createOpNamedBarrierInitialize(line, scope); break;
            case 329: instruction = createOpMemoryNamedBarrier(line, scope); break;
            case 330: instruction = createOpModuleProcessed(line, scope); break;
            case 331: instruction = createOpExecutionModeId(line, scope); break;
            case 332: instruction = createOpDecorateId(line, scope); break;
            case 4421: instruction = createOpSubgroupBallotKHR(line, scope); break;
            case 4422: instruction = createOpSubgroupFirstInvocationKHR(line, scope); break;
            case 4428: instruction = createOpSubgroupAllKHR(line, scope); break;
            case 4429: instruction = createOpSubgroupAnyKHR(line, scope); break;
            case 4430: instruction = createOpSubgroupAllEqualKHR(line, scope); break;
            case 4432: instruction = createOpSubgroupReadInvocationKHR(line, scope); break;
            case 5000: instruction = createOpGroupIAddNonUniformAMD(line, scope); break;
            case 5001: instruction = createOpGroupFAddNonUniformAMD(line, scope); break;
            case 5002: instruction = createOpGroupFMinNonUniformAMD(line, scope); break;
            case 5003: instruction = createOpGroupUMinNonUniformAMD(line, scope); break;
            case 5004: instruction = createOpGroupSMinNonUniformAMD(line, scope); break;
            case 5005: instruction = createOpGroupFMaxNonUniformAMD(line, scope); break;
            case 5006: instruction = createOpGroupUMaxNonUniformAMD(line, scope); break;
            case 5007: instruction = createOpGroupSMaxNonUniformAMD(line, scope); break;
            case 5011: instruction = createOpFragmentMaskFetchAMD(line, scope); break;
            case 5012: instruction = createOpFragmentFetchAMD(line, scope); break;
            case 5571: instruction = createOpSubgroupShuffleINTEL(line, scope); break;
            case 5572: instruction = createOpSubgroupShuffleDownINTEL(line, scope); break;
            case 5573: instruction = createOpSubgroupShuffleUpINTEL(line, scope); break;
            case 5574: instruction = createOpSubgroupShuffleXorINTEL(line, scope); break;
            case 5575: instruction = createOpSubgroupBlockReadINTEL(line, scope); break;
            case 5576: instruction = createOpSubgroupBlockWriteINTEL(line, scope); break;
            case 5577: instruction = createOpSubgroupImageBlockReadINTEL(line, scope); break;
            case 5578: instruction = createOpSubgroupImageBlockWriteINTEL(line, scope); break;
            case 5632: instruction = createOpDecorateStringGOOGLE(line, scope); break;
            case 5633: instruction = createOpMemberDecorateStringGOOGLE(line, scope); break;
            default: throw new InvalidSPIRVOpcodeException(opCode);
        }

        return instruction;
    }

    private static SPIRVInstruction createOpNop(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVOpNop();
    }

    private static SPIRVInstruction createOpUndef(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpUndef(operand1, operand2);
    }

    private static SPIRVInstruction createOpSourceContinued(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVLiteralString operand1 = SPIRVOperandMapper.mapLiteralString(operands, scope);
        return new SPIRVOpSourceContinued(operand1);
    }

    private static SPIRVInstruction createOpSource(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVSourceLanguage operand1 = SPIRVOperandMapper.mapSourceLanguage(operands, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVOptionalOperand<SPIRVId> operand3 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand3.setValue(SPIRVOperandMapper.mapId(operands, scope));
        SPIRVOptionalOperand<SPIRVLiteralString> operand4 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand4.setValue(SPIRVOperandMapper.mapLiteralString(operands, scope));
        return new SPIRVOpSource(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSourceExtension(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVLiteralString operand1 = SPIRVOperandMapper.mapLiteralString(operands, scope);
        return new SPIRVOpSourceExtension(operand1);
    }

    private static SPIRVInstruction createOpName(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralString operand2 = SPIRVOperandMapper.mapLiteralString(operands, scope);
        return new SPIRVOpName(operand1, operand2);
    }

    private static SPIRVInstruction createOpMemberName(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVLiteralString operand3 = SPIRVOperandMapper.mapLiteralString(operands, scope);
        return new SPIRVOpMemberName(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpString(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralString operand2 = SPIRVOperandMapper.mapLiteralString(operands, scope);
        return new SPIRVOpString(operand1, operand2);
    }

    private static SPIRVInstruction createOpLine(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        return new SPIRVOpLine(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpExtension(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVLiteralString operand1 = SPIRVOperandMapper.mapLiteralString(operands, scope);
        return new SPIRVOpExtension(operand1);
    }

    private static SPIRVInstruction createOpExtInstImport(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralString operand2 = SPIRVOperandMapper.mapLiteralString(operands, scope);
        return new SPIRVOpExtInstImport(operand1, operand2);
    }

    private static SPIRVInstruction createOpExtInst(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralExtInstInteger operand4 = SPIRVOperandMapper.mapLiteralExtInstInteger(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand5 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand5.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpExtInst(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpMemoryModel(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVAddressingModel operand1 = SPIRVOperandMapper.mapAddressingModel(operands, scope);
        SPIRVMemoryModel operand2 = SPIRVOperandMapper.mapMemoryModel(operands, scope);
        return new SPIRVOpMemoryModel(operand1, operand2);
    }

    private static SPIRVInstruction createOpEntryPoint(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVExecutionModel operand1 = SPIRVOperandMapper.mapExecutionModel(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralString operand3 = SPIRVOperandMapper.mapLiteralString(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand4 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand4.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpEntryPoint(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpExecutionMode(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVExecutionMode operand2 = SPIRVOperandMapper.mapExecutionMode(operands, scope);
        return new SPIRVOpExecutionMode(operand1, operand2);
    }

    private static SPIRVInstruction createOpCapability(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVCapability operand1 = SPIRVOperandMapper.mapCapability(operands, scope);
        return new SPIRVOpCapability(operand1);
    }

    private static SPIRVInstruction createOpTypeVoid(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeVoid(operand1);
    }

    private static SPIRVInstruction createOpTypeBool(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeBool(operand1);
    }

    private static SPIRVInstruction createOpTypeInt(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        return new SPIRVOpTypeInt(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeFloat(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        return new SPIRVOpTypeFloat(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypeVector(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        return new SPIRVOpTypeVector(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeMatrix(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        return new SPIRVOpTypeMatrix(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeImage(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVDim operand3 = SPIRVOperandMapper.mapDim(operands, scope);
        SPIRVLiteralInteger operand4 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVLiteralInteger operand5 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVLiteralInteger operand6 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVLiteralInteger operand7 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVImageFormat operand8 = SPIRVOperandMapper.mapImageFormat(operands, scope);
        SPIRVOptionalOperand<SPIRVAccessQualifier> operand9 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand9.setValue(SPIRVOperandMapper.mapAccessQualifier(operands, scope));
        return new SPIRVOpTypeImage(operand1, operand2, operand3, operand4, operand5, operand6, operand7, operand8, operand9);
    }

    private static SPIRVInstruction createOpTypeSampler(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeSampler(operand1);
    }

    private static SPIRVInstruction createOpTypeSampledImage(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeSampledImage(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypeArray(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeArray(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeRuntimeArray(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeRuntimeArray(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypeStruct(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand2 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand2.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpTypeStruct(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypeOpaque(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralString operand2 = SPIRVOperandMapper.mapLiteralString(operands, scope);
        return new SPIRVOpTypeOpaque(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypePointer(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVStorageClass operand2 = SPIRVOperandMapper.mapStorageClass(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypePointer(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeFunction(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand3 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand3.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpTypeFunction(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypeEvent(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeEvent(operand1);
    }

    private static SPIRVInstruction createOpTypeDeviceEvent(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeDeviceEvent(operand1);
    }

    private static SPIRVInstruction createOpTypeReserveId(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeReserveId(operand1);
    }

    private static SPIRVInstruction createOpTypeQueue(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeQueue(operand1);
    }

    private static SPIRVInstruction createOpTypePipe(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVAccessQualifier operand2 = SPIRVOperandMapper.mapAccessQualifier(operands, scope);
        return new SPIRVOpTypePipe(operand1, operand2);
    }

    private static SPIRVInstruction createOpTypeForwardPointer(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVStorageClass operand2 = SPIRVOperandMapper.mapStorageClass(operands, scope);
        return new SPIRVOpTypeForwardPointer(operand1, operand2);
    }

    private static SPIRVInstruction createOpConstantTrue(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpConstantTrue(operand1, operand2);
    }

    private static SPIRVInstruction createOpConstantFalse(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpConstantFalse(operand1, operand2);
    }

    private static SPIRVInstruction createOpConstant(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralContextDependentNumber operand3 = SPIRVOperandMapper.mapLiteralContextDependentNumber(operands, scope, operand1);
        return new SPIRVOpConstant(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpConstantComposite(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand3 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand3.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpConstantComposite(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpConstantSampler(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVSamplerAddressingMode operand3 = SPIRVOperandMapper.mapSamplerAddressingMode(operands, scope);
        SPIRVLiteralInteger operand4 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVSamplerFilterMode operand5 = SPIRVOperandMapper.mapSamplerFilterMode(operands, scope);
        return new SPIRVOpConstantSampler(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpConstantNull(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpConstantNull(operand1, operand2);
    }

    private static SPIRVInstruction createOpSpecConstantTrue(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSpecConstantTrue(operand1, operand2);
    }

    private static SPIRVInstruction createOpSpecConstantFalse(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSpecConstantFalse(operand1, operand2);
    }

    private static SPIRVInstruction createOpSpecConstant(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralContextDependentNumber operand3 = SPIRVOperandMapper.mapLiteralContextDependentNumber(operands, scope, operand1);
        return new SPIRVOpSpecConstant(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSpecConstantComposite(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand3 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand3.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpSpecConstantComposite(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSpecConstantOp(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralSpecConstantOpInteger operand3 = SPIRVOperandMapper.mapLiteralSpecConstantOpInteger(operands, scope);
        return new SPIRVOpSpecConstantOp(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpFunction(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVFunctionControl operand3 = SPIRVOperandMapper.mapFunctionControl(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFunction(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFunctionParameter(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFunctionParameter(operand1, operand2);
    }

    private static SPIRVInstruction createOpFunctionEnd(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVOpFunctionEnd();
    }

    private static SPIRVInstruction createOpFunctionCall(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand4 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand4.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpFunctionCall(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpVariable(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVStorageClass operand3 = SPIRVOperandMapper.mapStorageClass(operands, scope);
        SPIRVOptionalOperand<SPIRVId> operand4 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand4.setValue(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpVariable(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpImageTexelPointer(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpImageTexelPointer(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpLoad(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVMemoryAccess> operand4 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand4.setValue(SPIRVOperandMapper.mapMemoryAccess(operands, scope));
        return new SPIRVOpLoad(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpStore(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVMemoryAccess> operand3 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand3.setValue(SPIRVOperandMapper.mapMemoryAccess(operands, scope));
        return new SPIRVOpStore(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpCopyMemory(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVMemoryAccess> operand3 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand3.setValue(SPIRVOperandMapper.mapMemoryAccess(operands, scope));
        return new SPIRVOpCopyMemory(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpCopyMemorySized(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVMemoryAccess> operand4 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand4.setValue(SPIRVOperandMapper.mapMemoryAccess(operands, scope));
        return new SPIRVOpCopyMemorySized(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpAccessChain(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand4 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand4.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpAccessChain(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpInBoundsAccessChain(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand4 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand4.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpInBoundsAccessChain(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpPtrAccessChain(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand5 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand5.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpPtrAccessChain(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpArrayLength(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand4 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        return new SPIRVOpArrayLength(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpGenericPtrMemSemantics(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGenericPtrMemSemantics(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpInBoundsPtrAccessChain(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand5 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand5.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpInBoundsPtrAccessChain(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpDecorate(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVDecoration operand2 = SPIRVOperandMapper.mapDecoration(operands, scope);
        return new SPIRVOpDecorate(operand1, operand2);
    }

    private static SPIRVInstruction createOpMemberDecorate(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVDecoration operand3 = SPIRVOperandMapper.mapDecoration(operands, scope);
        return new SPIRVOpMemberDecorate(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpDecorationGroup(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpDecorationGroup(operand1);
    }

    private static SPIRVInstruction createOpGroupDecorate(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand2 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand2.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpGroupDecorate(operand1, operand2);
    }

    private static SPIRVInstruction createOpGroupMemberDecorate(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVPairIdRefLiteralInteger> operand2 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand2.add(SPIRVOperandMapper.mapPairIdRefLiteralInteger(operands, scope));
        return new SPIRVOpGroupMemberDecorate(operand1, operand2);
    }

    private static SPIRVInstruction createOpVectorExtractDynamic(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpVectorExtractDynamic(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpVectorInsertDynamic(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpVectorInsertDynamic(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpVectorShuffle(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVLiteralInteger> operand5 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand5.add(SPIRVOperandMapper.mapLiteralInteger(operands, scope));
        return new SPIRVOpVectorShuffle(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpCompositeConstruct(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand3 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand3.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpCompositeConstruct(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpCompositeExtract(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVLiteralInteger> operand4 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand4.add(SPIRVOperandMapper.mapLiteralInteger(operands, scope));
        return new SPIRVOpCompositeExtract(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpCompositeInsert(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVLiteralInteger> operand5 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand5.add(SPIRVOperandMapper.mapLiteralInteger(operands, scope));
        return new SPIRVOpCompositeInsert(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpCopyObject(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpCopyObject(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTranspose(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTranspose(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSampledImage(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSampledImage(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpImageSampleImplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSampleImplicitLod(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSampleExplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVImageOperands operand5 = SPIRVOperandMapper.mapImageOperands(operands, scope);
        return new SPIRVOpImageSampleExplicitLod(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSampleDrefImplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSampleDrefImplicitLod(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSampleDrefExplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVImageOperands operand6 = SPIRVOperandMapper.mapImageOperands(operands, scope);
        return new SPIRVOpImageSampleDrefExplicitLod(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSampleProjImplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSampleProjImplicitLod(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSampleProjExplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVImageOperands operand5 = SPIRVOperandMapper.mapImageOperands(operands, scope);
        return new SPIRVOpImageSampleProjExplicitLod(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSampleProjDrefImplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSampleProjDrefImplicitLod(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSampleProjDrefExplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVImageOperands operand6 = SPIRVOperandMapper.mapImageOperands(operands, scope);
        return new SPIRVOpImageSampleProjDrefExplicitLod(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageFetch(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageFetch(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageGather(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageGather(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageDrefGather(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageDrefGather(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageRead(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageRead(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageWrite(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand4 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand4.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageWrite(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpImage(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpImage(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpImageQueryFormat(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpImageQueryFormat(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpImageQueryOrder(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpImageQueryOrder(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpImageQuerySizeLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpImageQuerySizeLod(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpImageQuerySize(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpImageQuerySize(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpImageQueryLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpImageQueryLod(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpImageQueryLevels(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpImageQueryLevels(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpImageQuerySamples(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpImageQuerySamples(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpConvertFToU(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpConvertFToU(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpConvertFToS(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpConvertFToS(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpConvertSToF(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpConvertSToF(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpConvertUToF(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpConvertUToF(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpUConvert(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpUConvert(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSConvert(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSConvert(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpFConvert(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFConvert(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpQuantizeToF16(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpQuantizeToF16(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpConvertPtrToU(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpConvertPtrToU(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSatConvertSToU(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSatConvertSToU(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSatConvertUToS(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSatConvertUToS(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpConvertUToPtr(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpConvertUToPtr(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpPtrCastToGeneric(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpPtrCastToGeneric(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpGenericCastToPtr(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGenericCastToPtr(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpGenericCastToPtrExplicit(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVStorageClass operand4 = SPIRVOperandMapper.mapStorageClass(operands, scope);
        return new SPIRVOpGenericCastToPtrExplicit(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpBitcast(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBitcast(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSNegate(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSNegate(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpFNegate(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFNegate(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpIAdd(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpIAdd(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFAdd(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFAdd(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpISub(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpISub(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFSub(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFSub(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpIMul(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpIMul(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFMul(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFMul(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpUDiv(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpUDiv(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSDiv(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSDiv(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFDiv(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFDiv(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpUMod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpUMod(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSRem(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSRem(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSMod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSMod(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFRem(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFRem(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFMod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFMod(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpVectorTimesScalar(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpVectorTimesScalar(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpMatrixTimesScalar(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpMatrixTimesScalar(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpVectorTimesMatrix(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpVectorTimesMatrix(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpMatrixTimesVector(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpMatrixTimesVector(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpMatrixTimesMatrix(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpMatrixTimesMatrix(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpOuterProduct(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpOuterProduct(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpDot(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpDot(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpIAddCarry(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpIAddCarry(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpISubBorrow(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpISubBorrow(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpUMulExtended(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpUMulExtended(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSMulExtended(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSMulExtended(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpAny(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAny(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpAll(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAll(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpIsNan(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpIsNan(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpIsInf(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpIsInf(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpIsFinite(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpIsFinite(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpIsNormal(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpIsNormal(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSignBitSet(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSignBitSet(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpLessOrGreater(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpLessOrGreater(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpOrdered(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpOrdered(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpUnordered(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpUnordered(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpLogicalEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpLogicalEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpLogicalNotEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpLogicalNotEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpLogicalOr(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpLogicalOr(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpLogicalAnd(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpLogicalAnd(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpLogicalNot(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpLogicalNot(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSelect(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSelect(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpIEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpIEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpINotEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpINotEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpUGreaterThan(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpUGreaterThan(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSGreaterThan(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSGreaterThan(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpUGreaterThanEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpUGreaterThanEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSGreaterThanEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSGreaterThanEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpULessThan(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpULessThan(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSLessThan(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSLessThan(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpULessThanEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpULessThanEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSLessThanEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSLessThanEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFOrdEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFUnordEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdNotEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFOrdNotEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordNotEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFUnordNotEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdLessThan(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFOrdLessThan(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordLessThan(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFUnordLessThan(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdGreaterThan(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFOrdGreaterThan(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordGreaterThan(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFUnordGreaterThan(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdLessThanEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFOrdLessThanEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordLessThanEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFUnordLessThanEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFOrdGreaterThanEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFOrdGreaterThanEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFUnordGreaterThanEqual(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFUnordGreaterThanEqual(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpShiftRightLogical(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpShiftRightLogical(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpShiftRightArithmetic(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpShiftRightArithmetic(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpShiftLeftLogical(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpShiftLeftLogical(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpBitwiseOr(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBitwiseOr(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpBitwiseXor(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBitwiseXor(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpBitwiseAnd(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBitwiseAnd(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpNot(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpNot(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpBitFieldInsert(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBitFieldInsert(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpBitFieldSExtract(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBitFieldSExtract(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpBitFieldUExtract(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBitFieldUExtract(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpBitReverse(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBitReverse(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpBitCount(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBitCount(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpDPdx(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpDPdx(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpDPdy(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpDPdy(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpFwidth(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFwidth(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpDPdxFine(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpDPdxFine(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpDPdyFine(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpDPdyFine(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpFwidthFine(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFwidthFine(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpDPdxCoarse(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpDPdxCoarse(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpDPdyCoarse(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpDPdyCoarse(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpFwidthCoarse(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFwidthCoarse(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpEmitVertex(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVOpEmitVertex();
    }

    private static SPIRVInstruction createOpEndPrimitive(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVOpEndPrimitive();
    }

    private static SPIRVInstruction createOpEmitStreamVertex(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpEmitStreamVertex(operand1);
    }

    private static SPIRVInstruction createOpEndStreamPrimitive(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpEndStreamPrimitive(operand1);
    }

    private static SPIRVInstruction createOpControlBarrier(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpControlBarrier(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpMemoryBarrier(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpMemoryBarrier(operand1, operand2);
    }

    private static SPIRVInstruction createOpAtomicLoad(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicLoad(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpAtomicStore(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicStore(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpAtomicExchange(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicExchange(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicCompareExchange(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicCompareExchange(operand1, operand2, operand3, operand4, operand5, operand6, operand7, operand8);
    }

    private static SPIRVInstruction createOpAtomicCompareExchangeWeak(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicCompareExchangeWeak(operand1, operand2, operand3, operand4, operand5, operand6, operand7, operand8);
    }

    private static SPIRVInstruction createOpAtomicIIncrement(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicIIncrement(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpAtomicIDecrement(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicIDecrement(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpAtomicIAdd(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicIAdd(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicISub(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicISub(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicSMin(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicSMin(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicUMin(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicUMin(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicSMax(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicSMax(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicUMax(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicUMax(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicAnd(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicAnd(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicOr(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicOr(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpAtomicXor(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicXor(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpPhi(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVPairIdRefIdRef> operand3 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand3.add(SPIRVOperandMapper.mapPairIdRefIdRef(operands, scope));
        return new SPIRVOpPhi(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpLoopMerge(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLoopControl operand3 = SPIRVOperandMapper.mapLoopControl(operands, scope);
        return new SPIRVOpLoopMerge(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSelectionMerge(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVSelectionControl operand2 = SPIRVOperandMapper.mapSelectionControl(operands, scope);
        return new SPIRVOpSelectionMerge(operand1, operand2);
    }

    private static SPIRVInstruction createOpLabel(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpLabel(operand1);
    }

    private static SPIRVInstruction createOpBranch(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBranch(operand1);
    }

    private static SPIRVInstruction createOpBranchConditional(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVLiteralInteger> operand4 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand4.add(SPIRVOperandMapper.mapLiteralInteger(operands, scope));
        return new SPIRVOpBranchConditional(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSwitch(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVPairLiteralIntegerIdRef> operand3 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand3.add(SPIRVOperandMapper.mapPairLiteralIntegerIdRef(operands, scope));
        return new SPIRVOpSwitch(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpKill(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVOpKill();
    }

    private static SPIRVInstruction createOpReturn(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVOpReturn();
    }

    private static SPIRVInstruction createOpReturnValue(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpReturnValue(operand1);
    }

    private static SPIRVInstruction createOpUnreachable(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVOpUnreachable();
    }

    private static SPIRVInstruction createOpLifetimeStart(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        return new SPIRVOpLifetimeStart(operand1, operand2);
    }

    private static SPIRVInstruction createOpLifetimeStop(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        return new SPIRVOpLifetimeStop(operand1, operand2);
    }

    private static SPIRVInstruction createOpGroupAsyncCopy(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupAsyncCopy(operand1, operand2, operand3, operand4, operand5, operand6, operand7, operand8);
    }

    private static SPIRVInstruction createOpGroupWaitEvents(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupWaitEvents(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpGroupAll(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupAll(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpGroupAny(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupAny(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpGroupBroadcast(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupBroadcast(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupIAdd(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupIAdd(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFAdd(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupFAdd(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFMin(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupFMin(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupUMin(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupUMin(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupSMin(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupSMin(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFMax(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupFMax(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupUMax(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupUMax(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupSMax(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupSMax(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpReadPipe(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpReadPipe(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpWritePipe(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpWritePipe(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpReservedReadPipe(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpReservedReadPipe(operand1, operand2, operand3, operand4, operand5, operand6, operand7, operand8);
    }

    private static SPIRVInstruction createOpReservedWritePipe(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpReservedWritePipe(operand1, operand2, operand3, operand4, operand5, operand6, operand7, operand8);
    }

    private static SPIRVInstruction createOpReserveReadPipePackets(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpReserveReadPipePackets(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpReserveWritePipePackets(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpReserveWritePipePackets(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpCommitReadPipe(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpCommitReadPipe(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpCommitWritePipe(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpCommitWritePipe(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpIsValidReserveId(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpIsValidReserveId(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpGetNumPipePackets(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGetNumPipePackets(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGetMaxPipePackets(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGetMaxPipePackets(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupReserveReadPipePackets(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupReserveReadPipePackets(operand1, operand2, operand3, operand4, operand5, operand6, operand7);
    }

    private static SPIRVInstruction createOpGroupReserveWritePipePackets(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupReserveWritePipePackets(operand1, operand2, operand3, operand4, operand5, operand6, operand7);
    }

    private static SPIRVInstruction createOpGroupCommitReadPipe(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupCommitReadPipe(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupCommitWritePipe(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupCommitWritePipe(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpEnqueueMarker(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpEnqueueMarker(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpEnqueueKernel(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand8 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand9 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand10 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand11 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand12 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVMultipleOperands<SPIRVId> operand13 = new SPIRVMultipleOperands<>();
        while (operands.hasNext()) operand13.add(SPIRVOperandMapper.mapId(operands, scope));
        return new SPIRVOpEnqueueKernel(operand1, operand2, operand3, operand4, operand5, operand6, operand7, operand8, operand9, operand10, operand11, operand12, operand13);
    }

    private static SPIRVInstruction createOpGetKernelNDrangeSubGroupCount(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGetKernelNDrangeSubGroupCount(operand1, operand2, operand3, operand4, operand5, operand6, operand7);
    }

    private static SPIRVInstruction createOpGetKernelNDrangeMaxSubGroupSize(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGetKernelNDrangeMaxSubGroupSize(operand1, operand2, operand3, operand4, operand5, operand6, operand7);
    }

    private static SPIRVInstruction createOpGetKernelWorkGroupSize(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGetKernelWorkGroupSize(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpGetKernelPreferredWorkGroupSizeMultiple(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGetKernelPreferredWorkGroupSizeMultiple(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpRetainEvent(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpRetainEvent(operand1);
    }

    private static SPIRVInstruction createOpReleaseEvent(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpReleaseEvent(operand1);
    }

    private static SPIRVInstruction createOpCreateUserEvent(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpCreateUserEvent(operand1, operand2);
    }

    private static SPIRVInstruction createOpIsValidEvent(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpIsValidEvent(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSetUserEventStatus(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSetUserEventStatus(operand1, operand2);
    }

    private static SPIRVInstruction createOpCaptureEventProfilingInfo(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpCaptureEventProfilingInfo(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpGetDefaultQueue(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGetDefaultQueue(operand1, operand2);
    }

    private static SPIRVInstruction createOpBuildNDRange(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpBuildNDRange(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseSampleImplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSparseSampleImplicitLod(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseSampleExplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVImageOperands operand5 = SPIRVOperandMapper.mapImageOperands(operands, scope);
        return new SPIRVOpImageSparseSampleExplicitLod(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseSampleDrefImplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSparseSampleDrefImplicitLod(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseSampleDrefExplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVImageOperands operand6 = SPIRVOperandMapper.mapImageOperands(operands, scope);
        return new SPIRVOpImageSparseSampleDrefExplicitLod(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseSampleProjImplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSparseSampleProjImplicitLod(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseSampleProjExplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVImageOperands operand5 = SPIRVOperandMapper.mapImageOperands(operands, scope);
        return new SPIRVOpImageSparseSampleProjExplicitLod(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseSampleProjDrefImplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSparseSampleProjDrefImplicitLod(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseSampleProjDrefExplicitLod(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVImageOperands operand6 = SPIRVOperandMapper.mapImageOperands(operands, scope);
        return new SPIRVOpImageSparseSampleProjDrefExplicitLod(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseFetch(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSparseFetch(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpImageSparseGather(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSparseGather(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseDrefGather(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand6 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand6.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSparseDrefGather(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpImageSparseTexelsResident(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpImageSparseTexelsResident(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpNoLine(SPIRVLine operands, SPIRVInstScope scope) {
        return new SPIRVOpNoLine();
    }

    private static SPIRVInstruction createOpAtomicFlagTestAndSet(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicFlagTestAndSet(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpAtomicFlagClear(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpAtomicFlagClear(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpImageSparseRead(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVOptionalOperand<SPIRVImageOperands> operand5 = new SPIRVOptionalOperand<>();
        if (operands.hasNext()) operand5.setValue(SPIRVOperandMapper.mapImageOperands(operands, scope));
        return new SPIRVOpImageSparseRead(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpSizeOf(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSizeOf(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpTypePipeStorage(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypePipeStorage(operand1);
    }

    private static SPIRVInstruction createOpConstantPipeStorage(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand3 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVLiteralInteger operand4 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVLiteralInteger operand5 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        return new SPIRVOpConstantPipeStorage(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpCreatePipeFromPipeStorage(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpCreatePipeFromPipeStorage(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpGetKernelLocalSizeForSubgroupCount(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand7 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGetKernelLocalSizeForSubgroupCount(operand1, operand2, operand3, operand4, operand5, operand6, operand7);
    }

    private static SPIRVInstruction createOpGetKernelMaxNumSubgroups(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand6 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGetKernelMaxNumSubgroups(operand1, operand2, operand3, operand4, operand5, operand6);
    }

    private static SPIRVInstruction createOpTypeNamedBarrier(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpTypeNamedBarrier(operand1);
    }

    private static SPIRVInstruction createOpNamedBarrierInitialize(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpNamedBarrierInitialize(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpMemoryNamedBarrier(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpMemoryNamedBarrier(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpModuleProcessed(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVLiteralString operand1 = SPIRVOperandMapper.mapLiteralString(operands, scope);
        return new SPIRVOpModuleProcessed(operand1);
    }

    private static SPIRVInstruction createOpExecutionModeId(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVExecutionMode operand2 = SPIRVOperandMapper.mapExecutionMode(operands, scope);
        return new SPIRVOpExecutionModeId(operand1, operand2);
    }

    private static SPIRVInstruction createOpDecorateId(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVDecoration operand2 = SPIRVOperandMapper.mapDecoration(operands, scope);
        return new SPIRVOpDecorateId(operand1, operand2);
    }

    private static SPIRVInstruction createOpSubgroupBallotKHR(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupBallotKHR(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSubgroupFirstInvocationKHR(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupFirstInvocationKHR(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSubgroupAllKHR(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupAllKHR(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSubgroupAnyKHR(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupAnyKHR(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSubgroupAllEqualKHR(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupAllEqualKHR(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSubgroupReadInvocationKHR(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupReadInvocationKHR(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpGroupIAddNonUniformAMD(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupIAddNonUniformAMD(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFAddNonUniformAMD(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupFAddNonUniformAMD(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFMinNonUniformAMD(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupFMinNonUniformAMD(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupUMinNonUniformAMD(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupUMinNonUniformAMD(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupSMinNonUniformAMD(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupSMinNonUniformAMD(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupFMaxNonUniformAMD(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupFMaxNonUniformAMD(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupUMaxNonUniformAMD(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupUMaxNonUniformAMD(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpGroupSMaxNonUniformAMD(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVGroupOperation operand4 = SPIRVOperandMapper.mapGroupOperation(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpGroupSMaxNonUniformAMD(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpFragmentMaskFetchAMD(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFragmentMaskFetchAMD(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpFragmentFetchAMD(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpFragmentFetchAMD(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpSubgroupShuffleINTEL(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupShuffleINTEL(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSubgroupShuffleDownINTEL(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupShuffleDownINTEL(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpSubgroupShuffleUpINTEL(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand5 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupShuffleUpINTEL(operand1, operand2, operand3, operand4, operand5);
    }

    private static SPIRVInstruction createOpSubgroupShuffleXorINTEL(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupShuffleXorINTEL(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSubgroupBlockReadINTEL(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupBlockReadINTEL(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpSubgroupBlockWriteINTEL(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupBlockWriteINTEL(operand1, operand2);
    }

    private static SPIRVInstruction createOpSubgroupImageBlockReadINTEL(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand4 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupImageBlockReadINTEL(operand1, operand2, operand3, operand4);
    }

    private static SPIRVInstruction createOpSubgroupImageBlockWriteINTEL(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand2 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVId operand3 = SPIRVOperandMapper.mapId(operands, scope);
        return new SPIRVOpSubgroupImageBlockWriteINTEL(operand1, operand2, operand3);
    }

    private static SPIRVInstruction createOpDecorateStringGOOGLE(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVDecoration operand2 = SPIRVOperandMapper.mapDecoration(operands, scope);
        return new SPIRVOpDecorateStringGOOGLE(operand1, operand2);
    }

    private static SPIRVInstruction createOpMemberDecorateStringGOOGLE(SPIRVLine operands, SPIRVInstScope scope) {
        SPIRVId operand1 = SPIRVOperandMapper.mapId(operands, scope);
        SPIRVLiteralInteger operand2 = SPIRVOperandMapper.mapLiteralInteger(operands, scope);
        SPIRVDecoration operand3 = SPIRVOperandMapper.mapDecoration(operands, scope);
        return new SPIRVOpMemberDecorateStringGOOGLE(operand1, operand2, operand3);
    }

}

