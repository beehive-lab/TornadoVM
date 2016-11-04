package tornado.drivers.opencl.graal.compiler.plugins;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.graphbuilderconf.MethodIdMap.Receiver;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.extended.BoxNode;
import com.oracle.graal.nodes.java.NewArrayNode;
import com.oracle.graal.nodes.java.StoreIndexedNode;
import com.oracle.graal.nodes.util.GraphUtil;
import tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode;
import static tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.*;
import tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode;
import static tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.*;
import tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import static tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.*;
import tornado.drivers.opencl.graal.nodes.PrintfNode;
import tornado.drivers.opencl.graal.nodes.SlotsBaseAddressNode;
import tornado.drivers.opencl.graal.nodes.TPrintfNode;
import tornado.lang.CompilerInternals;
import tornado.lang.Debug;

public class OCLGraphBuilderPlugins {

    public static void registerInvocationPlugins(final InvocationPlugins plugins) {
        registerCompilerInstrinsicsPlugins(plugins);
        registerTornadoInstrinsicsPlugins(plugins);
        registerOpenCLBuiltinPlugins(plugins);

        TornadoMathPlugins.registerTornadoMathPlugins(plugins);
        VectorPlugins.registerPlugins(plugins);

//		AtomicPlugins.registerPlugins(plugins);
    }

    private static void registerCompilerInstrinsicsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CompilerInternals.class);

        r.register0("getSlotsAddress", new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver) {
                b.addPush(Kind.Object, new SlotsBaseAddressNode());
                return true;
            }
        });

    }

    private static void registerTornadoInstrinsicsPlugins(InvocationPlugins plugins) {

        final InvocationPlugin tprintfPlugin = new InvocationPlugin() {

            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode... args) {

                int idCount = 0;
                int index = 0;
                for (; index < 3; index++) {
                    ValueNode arg = args[index];
                    if (arg instanceof ConstantNode && ((ConstantNode) arg).getKind().isObject()) {
                        break;
                    }
                    idCount++;
                }

                NewArrayNode newArrayNode = (NewArrayNode) args[index + 1];
                ConstantNode lengthNode = (ConstantNode) newArrayNode.dimension(0);
                int length = ((JavaConstant) lengthNode.getValue()).asInt();

                ValueNode[] actualArgs = new ValueNode[4 + length];
                for (int i = 0; i < idCount; i++) {
                    actualArgs[i] = args[i];
                }

                for (int i = idCount; i < 3; i++) {
                    actualArgs[i] = ConstantNode.forInt(0);
                }

                actualArgs[3] = args[index];

                int argIndex = 0;
                for (int i = 0; i < newArrayNode.getUsageCount(); i++) {
                    Node n = newArrayNode.getUsageAt(i);
                    if (n instanceof StoreIndexedNode) {
                        StoreIndexedNode storeNode = (StoreIndexedNode) n;
                        ValueNode value = storeNode.value();
                        if (value instanceof BoxNode) {
                            BoxNode box = (BoxNode) value;
                            box.safeDelete();
                            value = box.getValue();
                        }
                        actualArgs[4 + argIndex] = value;
                        argIndex++;
                    }

                }

                TPrintfNode printfNode = new TPrintfNode(actualArgs);
                b.append(b.recursiveAppend(printfNode));
                while (newArrayNode.hasUsages()) {
                    Node n = newArrayNode.getUsageAt(0);
                    // need to remove all nodes from the graph that operate on the
                    // new array, however, we cannot remove all inputs as they may be
                    // used by the currently unbuilt part of the graph.
                    // We also need to ensure that we do not leave any gaps inbetween
                    // fixed nodes
                    if (n instanceof FixedWithNextNode) {
                        GraphUtil.unlinkFixedNode((FixedWithNextNode) n);
                    }
                    n.clearInputs();
                    n.safeDelete();
                }

                GraphUtil.unlinkFixedNode(newArrayNode);
                newArrayNode.clearInputs();
                newArrayNode.safeDelete();

                return true;
            }
        };

        plugins.register(tprintfPlugin, Debug.class, "tprintf", String.class, Object[].class);
        plugins.register(tprintfPlugin, Debug.class, "tprintf", int.class, String.class, Object[].class);
        plugins.register(tprintfPlugin, Debug.class, "tprintf", int.class, int.class, String.class, Object[].class);
        plugins.register(tprintfPlugin, Debug.class, "tprintf", int.class, int.class, int.class, String.class, Object[].class);

        final InvocationPlugin printfPlugin = new InvocationPlugin() {

            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode... args) {

                NewArrayNode newArrayNode = (NewArrayNode) args[1];
                ConstantNode lengthNode = (ConstantNode) newArrayNode.dimension(0);
                int length = ((JavaConstant) lengthNode.getValue()).asInt();

                ValueNode[] actualArgs = new ValueNode[length + 1];
                actualArgs[0] = args[0];

                int argIndex = 0;
                for (int i = 0; i < newArrayNode.getUsageCount(); i++) {
                    Node n = newArrayNode.getUsageAt(i);
                    if (n instanceof StoreIndexedNode) {
                        StoreIndexedNode storeNode = (StoreIndexedNode) n;
                        ValueNode value = storeNode.value();
                        if (value instanceof BoxNode) {
                            BoxNode box = (BoxNode) value;
                            box.safeDelete();
                            value = box.getValue();
                        }
                        actualArgs[argIndex + 1] = value;
                        argIndex++;
                    }

                }

                PrintfNode printfNode = new PrintfNode(actualArgs);
                b.append(b.recursiveAppend(printfNode));

                while (newArrayNode.hasUsages()) {
                    Node n = newArrayNode.getUsageAt(0);
                    // need to remove all nodes from the graph that operate on the
                    // new array, however, we cannot remove all inputs as they may be
                    // used by the currently unbuilt part of the graph.
                    // We also need to ensure that we do not leave any gaps inbetween
                    // fixed nodes
                    if (n instanceof FixedWithNextNode) {
                        GraphUtil.unlinkFixedNode((FixedWithNextNode) n);
                    }
                    n.clearInputs();
                    n.safeDelete();
                }

                GraphUtil.unlinkFixedNode(newArrayNode);
                newArrayNode.clearInputs();
                newArrayNode.safeDelete();

                return true;
            }
        };

        plugins.register(printfPlugin, Debug.class, "printf", String.class, Object[].class);

    }

    private static void registerOpenCLBuiltinPlugins(InvocationPlugins plugins) {

        Registration r = new Registration(plugins, java.lang.Math.class);
        registerOpenCLOverridesForType(r, Float.TYPE, Kind.Float);
        registerOpenCLOverridesForType(r, Double.TYPE, Kind.Double);
        registerOpenCLOverridesForType(r, Integer.TYPE, Kind.Int);
        registerOpenCLOverridesForType(r, Long.TYPE, Kind.Long);

    }

    private static void registerOpenCLOverridesForType(Registration r, Class<?> type, Kind kind) {
        r.register2("min", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.recursiveAppend(OCLFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                } else {
                    b.push(kind, b.recursiveAppend(OCLIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                }
                return true;
            }
        });

        r.register2("max", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.recursiveAppend(OCLFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                } else {
                    b.push(kind, b.recursiveAppend(OCLIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                }
                return true;
            }
        });

        r.register1("abs", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode value) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, FABS, kind)));
                }
                //else
                //	b.push(kind, b.recursiveAppend(OCLIntUnaryIntrinsicNode.create(value, ABS , kind)));
                return true;
            }
        });

    }

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.setNewInstancePlugin(new TornadoNewInstancePlugin());

    }

    public static void registerParameterPlugins(Plugins plugins) {
        VectorPlugins.registerParameterPlugins(plugins);
    }
}
