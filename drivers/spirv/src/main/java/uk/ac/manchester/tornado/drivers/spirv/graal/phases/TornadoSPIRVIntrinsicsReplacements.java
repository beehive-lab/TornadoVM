package uk.ac.manchester.tornado.drivers.spirv.graal.phases;

import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.unimplemented;

import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.BasePhase;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GlobalThreadSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.GroupIdNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalGroupSizeNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalThreadIdFixedNode;
import uk.ac.manchester.tornado.runtime.graal.phases.TornadoHighTierContext;

public class TornadoSPIRVIntrinsicsReplacements extends BasePhase<TornadoHighTierContext> {

    private MetaAccessProvider metaAccessProvider;

    public TornadoSPIRVIntrinsicsReplacements(MetaAccessProvider metaAccessProvider) {
        this.metaAccessProvider = metaAccessProvider;
    }

    private ConstantNode getConstantNodeFromArguments(InvokeNode invoke, int index) {
        NodeInputList<ValueNode> arguments = invoke.callTarget().arguments();
        return (ConstantNode) arguments.get(index);
    }

    @Override
    protected void run(StructuredGraph graph, TornadoHighTierContext context) {
        NodeIterable<InvokeNode> invokeNodes = graph.getNodes().filter(InvokeNode.class);

        for (InvokeNode invoke : invokeNodes) {
            String methodName = invoke.callTarget().targetName();

            switch (methodName) {
                case "Direct#NewArrayNode.newArray":
                    throw new RuntimeException("Unimplemented");
                case "Direct#OpenCLIntrinsics.localBarrier":
                    throw new RuntimeException("Unimplemented");
                case "Direct#OpenCLIntrinsics.globalBarrier":
                    throw new RuntimeException("Unimplemented");
                case "Direct#OpenCLIntrinsics.get_local_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalThreadIdFixedNode localIdNode = graph.addOrUnique(new LocalThreadIdFixedNode(dimension));
                    graph.replaceFixed(invoke, localIdNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_local_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    LocalGroupSizeNode groupSize = graph.addOrUnique(new LocalGroupSizeNode(dimension));
                    graph.replaceFixed(invoke, groupSize);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_global_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadIdNode globalThreadIdNode = graph.addOrUnique(new GlobalThreadIdNode(dimension));
                    graph.replaceFixed(invoke, globalThreadIdNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_global_size": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GlobalThreadSizeNode globalThreadSizeNode = graph.addOrUnique(new GlobalThreadSizeNode(dimension));
                    graph.replaceFixed(invoke, globalThreadSizeNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.get_group_id": {
                    ConstantNode dimension = getConstantNodeFromArguments(invoke, 0);
                    GroupIdNode groupIdNode = graph.addOrUnique(new GroupIdNode(dimension));
                    graph.replaceFixed(invoke, groupIdNode);
                    break;
                }
                case "Direct#OpenCLIntrinsics.printEmpty": {
                    throw new RuntimeException("Unimplemented");
                }
            }
        }
    }

    private JavaKind getJavaKindFromConstantNode(ConstantNode signatureNode) {
        switch (signatureNode.getValue().toValueString()) {
            case "Class:int":
                return JavaKind.Int;
            case "Class:long":
                return JavaKind.Long;
            case "Class:float":
                return JavaKind.Float;
            case "Class:double":
                return JavaKind.Double;
            default:
                unimplemented("Other types not supported yet: " + signatureNode.getValue().toValueString());
        }
        return null;
    }
}
