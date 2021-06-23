package uk.ac.manchester.tornado.drivers.spirv;

import org.graalvm.compiler.graph.Node;

import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadIdFixedNode;

public enum SPIRVOCLBuiltIn {

    // @formatter:off
    GLOBAL_THREAD_ID("spirv_BuiltInGlobalInvocationId", SPIRVBuiltIn.GlobalInvocationId(), GlobalThreadIdNode.class), 
    GLOBAL_SIZE("spirv_BuiltInGlobalSize", SPIRVBuiltIn.GlobalSize(), GlobalThreadSizeNode.class),
    LOCAL_THREAD_ID("spirv_BuiltInLocalInvocationId", SPIRVBuiltIn.LocalInvocationId(), LocalThreadIdFixedNode.class),
    WORKGROUP_SIZE("spirv_BuiltInWorkgroupSize", SPIRVBuiltIn.WorkgroupSize(), LocalGroupSizeNode.class),
    GROUP_ID("spirv_BuiltInWorkgroupId", SPIRVBuiltIn.WorkgroupId(), GroupIdNode.class);
    // @formatter:on

    String name;
    SPIRVBuiltIn builtIn;
    Class<? extends Node> nodeClass;

    SPIRVOCLBuiltIn(String idName, SPIRVBuiltIn builtIn, Class<? extends Node> nodeClass) {
        this.name = idName;
        this.builtIn = builtIn;
        this.nodeClass = nodeClass;
    }

    public String getName() {
        return name;
    }

    public SPIRVBuiltIn getBuiltIn() {
        return builtIn;
    }

    public Class<? extends Node> getNodeClass() {
        return nodeClass;
    }

}
