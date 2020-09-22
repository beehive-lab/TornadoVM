package uk.ac.manchester.tornado.drivers.opencl.graal.compiler.plugins;

import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;
import uk.ac.manchester.tornado.api.type.annotations.Vector;
import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLKind;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.TornadoAtomicIntegerNode;

public class OCLAtomicIntegerPlugin implements NodePlugin {

    @Override
    public boolean handleNewInstance(GraphBuilderContext b, ResolvedJavaType type) {
        if (type.getAnnotation(Vector.class) != null) {
            return false;
        }
        return createAtomicIntegerInstance(b, type);
    }

    private boolean createAtomicIntegerInstance(GraphBuilderContext b, ResolvedJavaType type) {
        OCLKind kind = resolveOCLKind(type);
        if (kind != OCLKind.ILLEGAL) {
            if (kind == OCLKind.INTEGER_ATOMIC || kind == OCLKind.INTEGER_ATOMIC_JAVA) {
                b.push(JavaKind.Object, b.append(new TornadoAtomicIntegerNode(kind)));
                return true;
            }
        }
        return false;
    }

    private OCLKind resolveOCLKind(ResolvedJavaType type) {
        if (type instanceof HotSpotResolvedJavaType) {
            return OCLKind.fromResolvedJavaType(type);
        }
        return OCLKind.ILLEGAL;
    }
}
