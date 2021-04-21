package uk.ac.manchester.spirvproto.lib.instructions.operands;

import javax.annotation.Generated;
import java.util.List;
import java.util.ArrayList;
import java.nio.ByteBuffer;

@Generated("beehive-lab.spirv-proto.generator")
public class SPIRVBuiltIn extends SPIRVEnum {

    protected SPIRVBuiltIn(int value, String name, List<SPIRVOperand> parameters, SPIRVCapability... capabilities) {
        super(value, name, parameters, capabilities);
    }

    @Override
    public void write(ByteBuffer output) {
        super.write(output);
        parameters.forEach(param -> param.write(output));
    }


    public static SPIRVBuiltIn Position() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(0, "Position", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn PointSize() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(1, "PointSize", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn ClipDistance() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(3, "ClipDistance", params, SPIRVCapability.ClipDistance());
    }
    public static SPIRVBuiltIn CullDistance() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4, "CullDistance", params, SPIRVCapability.CullDistance());
    }
    public static SPIRVBuiltIn VertexId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(5, "VertexId", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn InstanceId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(6, "InstanceId", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn PrimitiveId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(7, "PrimitiveId", params, SPIRVCapability.Geometry(), SPIRVCapability.Tessellation());
    }
    public static SPIRVBuiltIn InvocationId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(8, "InvocationId", params, SPIRVCapability.Geometry(), SPIRVCapability.Tessellation());
    }
    public static SPIRVBuiltIn Layer() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(9, "Layer", params, SPIRVCapability.Geometry());
    }
    public static SPIRVBuiltIn ViewportIndex() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(10, "ViewportIndex", params, SPIRVCapability.MultiViewport());
    }
    public static SPIRVBuiltIn TessLevelOuter() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(11, "TessLevelOuter", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVBuiltIn TessLevelInner() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(12, "TessLevelInner", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVBuiltIn TessCoord() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(13, "TessCoord", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVBuiltIn PatchVertices() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(14, "PatchVertices", params, SPIRVCapability.Tessellation());
    }
    public static SPIRVBuiltIn FragCoord() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(15, "FragCoord", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn PointCoord() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(16, "PointCoord", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn FrontFacing() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(17, "FrontFacing", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn SampleId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(18, "SampleId", params, SPIRVCapability.SampleRateShading());
    }
    public static SPIRVBuiltIn SamplePosition() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(19, "SamplePosition", params, SPIRVCapability.SampleRateShading());
    }
    public static SPIRVBuiltIn SampleMask() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(20, "SampleMask", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn FragDepth() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(22, "FragDepth", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn HelperInvocation() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(23, "HelperInvocation", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn NumWorkgroups() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(24, "NumWorkgroups", params);
    }
    public static SPIRVBuiltIn WorkgroupSize() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(25, "WorkgroupSize", params);
    }
    public static SPIRVBuiltIn WorkgroupId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(26, "WorkgroupId", params);
    }
    public static SPIRVBuiltIn LocalInvocationId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(27, "LocalInvocationId", params);
    }
    public static SPIRVBuiltIn GlobalInvocationId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(28, "GlobalInvocationId", params);
    }
    public static SPIRVBuiltIn LocalInvocationIndex() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(29, "LocalInvocationIndex", params);
    }
    public static SPIRVBuiltIn WorkDim() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(30, "WorkDim", params, SPIRVCapability.Kernel());
    }
    public static SPIRVBuiltIn GlobalSize() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(31, "GlobalSize", params, SPIRVCapability.Kernel());
    }
    public static SPIRVBuiltIn EnqueuedWorkgroupSize() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(32, "EnqueuedWorkgroupSize", params, SPIRVCapability.Kernel());
    }
    public static SPIRVBuiltIn GlobalOffset() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(33, "GlobalOffset", params, SPIRVCapability.Kernel());
    }
    public static SPIRVBuiltIn GlobalLinearId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(34, "GlobalLinearId", params, SPIRVCapability.Kernel());
    }
    public static SPIRVBuiltIn SubgroupSize() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(36, "SubgroupSize", params, SPIRVCapability.Kernel(), SPIRVCapability.SubgroupBallotKHR());
    }
    public static SPIRVBuiltIn SubgroupMaxSize() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(37, "SubgroupMaxSize", params, SPIRVCapability.Kernel());
    }
    public static SPIRVBuiltIn NumSubgroups() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(38, "NumSubgroups", params, SPIRVCapability.Kernel());
    }
    public static SPIRVBuiltIn NumEnqueuedSubgroups() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(39, "NumEnqueuedSubgroups", params, SPIRVCapability.Kernel());
    }
    public static SPIRVBuiltIn SubgroupId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(40, "SubgroupId", params, SPIRVCapability.Kernel());
    }
    public static SPIRVBuiltIn SubgroupLocalInvocationId() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(41, "SubgroupLocalInvocationId", params, SPIRVCapability.Kernel(), SPIRVCapability.SubgroupBallotKHR());
    }
    public static SPIRVBuiltIn VertexIndex() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(42, "VertexIndex", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn InstanceIndex() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(43, "InstanceIndex", params, SPIRVCapability.Shader());
    }
    public static SPIRVBuiltIn SubgroupEqMaskKHR() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4416, "SubgroupEqMaskKHR", params, SPIRVCapability.SubgroupBallotKHR());
    }
    public static SPIRVBuiltIn SubgroupGeMaskKHR() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4417, "SubgroupGeMaskKHR", params, SPIRVCapability.SubgroupBallotKHR());
    }
    public static SPIRVBuiltIn SubgroupGtMaskKHR() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4418, "SubgroupGtMaskKHR", params, SPIRVCapability.SubgroupBallotKHR());
    }
    public static SPIRVBuiltIn SubgroupLeMaskKHR() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4419, "SubgroupLeMaskKHR", params, SPIRVCapability.SubgroupBallotKHR());
    }
    public static SPIRVBuiltIn SubgroupLtMaskKHR() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4420, "SubgroupLtMaskKHR", params, SPIRVCapability.SubgroupBallotKHR());
    }
    public static SPIRVBuiltIn BaseVertex() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4424, "BaseVertex", params, SPIRVCapability.DrawParameters());
    }
    public static SPIRVBuiltIn BaseInstance() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4425, "BaseInstance", params, SPIRVCapability.DrawParameters());
    }
    public static SPIRVBuiltIn DrawIndex() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4426, "DrawIndex", params, SPIRVCapability.DrawParameters());
    }
    public static SPIRVBuiltIn DeviceIndex() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4438, "DeviceIndex", params, SPIRVCapability.DeviceGroup());
    }
    public static SPIRVBuiltIn ViewIndex() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4440, "ViewIndex", params, SPIRVCapability.MultiView());
    }
    public static SPIRVBuiltIn BaryCoordNoPerspAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4992, "BaryCoordNoPerspAMD", params);
    }
    public static SPIRVBuiltIn BaryCoordNoPerspCentroidAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4993, "BaryCoordNoPerspCentroidAMD", params);
    }
    public static SPIRVBuiltIn BaryCoordNoPerspSampleAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4994, "BaryCoordNoPerspSampleAMD", params);
    }
    public static SPIRVBuiltIn BaryCoordSmoothAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4995, "BaryCoordSmoothAMD", params);
    }
    public static SPIRVBuiltIn BaryCoordSmoothCentroidAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4996, "BaryCoordSmoothCentroidAMD", params);
    }
    public static SPIRVBuiltIn BaryCoordSmoothSampleAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4997, "BaryCoordSmoothSampleAMD", params);
    }
    public static SPIRVBuiltIn BaryCoordPullModelAMD() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(4998, "BaryCoordPullModelAMD", params);
    }
    public static SPIRVBuiltIn FragStencilRefEXT() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(5014, "FragStencilRefEXT", params, SPIRVCapability.StencilExportEXT());
    }
    public static SPIRVBuiltIn ViewportMaskNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(5253, "ViewportMaskNV", params, SPIRVCapability.ShaderViewportMaskNV());
    }
    public static SPIRVBuiltIn SecondaryPositionNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(5257, "SecondaryPositionNV", params, SPIRVCapability.ShaderStereoViewNV());
    }
    public static SPIRVBuiltIn SecondaryViewportMaskNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(5258, "SecondaryViewportMaskNV", params, SPIRVCapability.ShaderStereoViewNV());
    }
    public static SPIRVBuiltIn PositionPerViewNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(5261, "PositionPerViewNV", params, SPIRVCapability.PerViewAttributesNV());
    }
    public static SPIRVBuiltIn ViewportMaskPerViewNV() {
        List<SPIRVOperand> params = new ArrayList<>(0);
        return new SPIRVBuiltIn(5262, "ViewportMaskPerViewNV", params, SPIRVCapability.PerViewAttributesNV());
    }
}
