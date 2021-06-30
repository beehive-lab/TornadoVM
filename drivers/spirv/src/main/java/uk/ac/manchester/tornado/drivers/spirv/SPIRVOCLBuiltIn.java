package uk.ac.manchester.tornado.drivers.spirv;

import org.graalvm.compiler.graph.Node;

import uk.ac.manchester.spirvproto.lib.instructions.operands.SPIRVBuiltIn;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadIdFixedNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadIdNode;

public enum SPIRVOCLBuiltIn {

    // @formatter:off
    GLOBAL_THREAD_ID("spirv_BuiltInGlobalInvocationId", SPIRVBuiltIn.GlobalInvocationId(), GlobalThreadIdNode.class, null), 
    GLOBAL_SIZE("spirv_BuiltInGlobalSize", SPIRVBuiltIn.GlobalSize(), GlobalThreadSizeNode.class, null),
    LOCAL_THREAD_ID("spirv_BuiltInLocalInvocationId", SPIRVBuiltIn.LocalInvocationId(), LocalThreadIdFixedNode.class, LocalThreadIdNode.class),
    WORKGROUP_SIZE("spirv_BuiltInWorkgroupSize", SPIRVBuiltIn.WorkgroupSize(), LocalGroupSizeNode.class, null),
    GROUP_ID("spirv_BuiltInWorkgroupId", SPIRVBuiltIn.WorkgroupId(), GroupIdNode.class, null);
    // @formatter:on

    String name;
    SPIRVBuiltIn builtIn;
    Class<? extends Node> nodeClass;
    Class<? extends Node> optionalNodeClass;

    SPIRVOCLBuiltIn(String idName, SPIRVBuiltIn builtIn, Class<? extends Node> nodeClass, Class<? extends Node> optional) {
        this.name = idName;
        this.builtIn = builtIn;
        this.nodeClass = nodeClass;
        this.optionalNodeClass = optional;
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

    public Class<? extends Node> getOptionalNodeClass() {
        return optionalNodeClass;
    }

}
