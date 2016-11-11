package tornado.drivers.opencl.graal.compiler.plugins;

import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.collections.types.FloatOps;
import tornado.drivers.opencl.graal.nodes.AtomicAddNode;

public class AtomicPlugins {

    public static void registerPlugins(InvocationPlugins plugins) {

        registerAtomicPlugins(plugins);

    }

    private static void registerAtomicPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, FloatOps.class);

        r.register3("atomicAdd", float[].class, int.class, float.class, new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b,
                    ResolvedJavaMethod targetMethod, Receiver receiver,
                    ValueNode array, ValueNode index, ValueNode value) {

                final AtomicAddNode atomicAddNode = new AtomicAddNode(array, index, JavaKind.Float, value);
                b.append(atomicAddNode);
                return true;
            }

        });
    }
}
