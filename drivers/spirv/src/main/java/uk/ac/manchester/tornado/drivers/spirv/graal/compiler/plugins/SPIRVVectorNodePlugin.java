package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins;

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.type.annotations.Vector;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.vector.SPIRVVectorValueNode;
import uk.ac.manchester.tornado.runtime.common.Tornado;

public class SPIRVVectorNodePlugin implements NodePlugin {

    @Override
    public boolean handleNewInstance(GraphBuilderContext builderContext, ResolvedJavaType type) {
        // FIXME <REFACTOR> I think vectors should be mandatory for TornadoVM
        if (!Tornado.ENABLE_VECTORS) {
            return false;
        }
        if (type.getAnnotation(Vector.class) != null) {
            return createVectorInstance(builderContext, type);
        }
        return false;
    }

    private SPIRVKind resolveSPIRVKind(ResolvedJavaType type) {
        if (type instanceof HotSpotResolvedJavaType) {
            return SPIRVKind.fromResolvedJavaTypeToVectorKind(type);
        }

        return SPIRVKind.ILLEGAL;
    }

    private boolean createVectorInstance(GraphBuilderContext builderContext, ResolvedJavaType type) {
        SPIRVKind spirvVectorKind = resolveSPIRVKind(type);
        if (spirvVectorKind != SPIRVKind.ILLEGAL && spirvVectorKind.isVector()) {
            builderContext.push(JavaKind.Object, builderContext.append(new SPIRVVectorValueNode(spirvVectorKind)));
            return true;
        }
        return false;
    }
}
