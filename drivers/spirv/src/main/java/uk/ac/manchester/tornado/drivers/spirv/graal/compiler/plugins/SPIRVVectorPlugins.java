package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins;

import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import uk.ac.manchester.tornado.api.type.annotations.Vector;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVStampFactory;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;

public class SPIRVVectorPlugins {

    /**
     * If the parameter passed is a vector, we attach vector information (SPIRVKind)
     * to the parameter node.
     * 
     * @param plugins
     *            {@link Plugins}
     */
    public static void registerParameterPlugins(Plugins plugins) {
        plugins.appendParameterPlugin((GraphBuilderTool tool, int index, StampPair stampPair) -> {
            if (stampPair.getTrustedStamp() instanceof ObjectStamp) {
                ObjectStamp objectStamp = (ObjectStamp) stampPair.getTrustedStamp();
                if (objectStamp.type().getAnnotation(Vector.class) != null) {
                    SPIRVKind kind = SPIRVKind.fromResolvedJavaType(objectStamp.type());
                    return new ParameterNode(index, StampPair.createSingle(SPIRVStampFactory.getStampFor(kind)));
                }
            }
            return null;
        });
    }
}
