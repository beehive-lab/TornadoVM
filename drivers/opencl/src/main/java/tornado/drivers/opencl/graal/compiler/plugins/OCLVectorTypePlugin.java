package tornado.drivers.opencl.graal.compiler.plugins;

import com.oracle.graal.compiler.common.type.StampPair;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderTool;
import com.oracle.graal.nodes.graphbuilderconf.TypePlugin;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import tornado.api.Vector;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;

import static tornado.common.Tornado.ENABLE_VECTORS;

public class OCLVectorTypePlugin implements TypePlugin {

    @Override
    public StampPair interceptType(GraphBuilderTool b, JavaType declaredType, boolean nonNull) {

        if (!ENABLE_VECTORS) {
            return null;
        }

        if (declaredType instanceof ResolvedJavaType) {
            ResolvedJavaType resolved = (ResolvedJavaType) declaredType;
            if (resolved.getAnnotation(Vector.class) != null) {
                return createVectorInstance(b, resolved);
            }

        }
        return null;
    }

    private StampPair createVectorInstance(GraphBuilderTool b, ResolvedJavaType type) {
        OCLKind vectorKind = resolveOCLKind(type);
        if (vectorKind != OCLKind.ILLEGAL) {
            b.recursiveAppend(new VectorValueNode(vectorKind));
            return StampPair.createSingle(OCLStampFactory.getStampFor(vectorKind));
        }

        return null;
    }

    private OCLKind resolveOCLKind(ResolvedJavaType type) {
        if (type instanceof HotSpotResolvedJavaType) {
            final HotSpotResolvedJavaType resolvedType = (HotSpotResolvedJavaType) type;
            return OCLKind.fromClass(resolvedType.mirror());
        }

        return OCLKind.ILLEGAL;
    }

}
