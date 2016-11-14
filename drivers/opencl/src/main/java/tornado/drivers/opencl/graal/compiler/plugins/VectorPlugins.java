package tornado.drivers.opencl.graal.compiler.plugins;

import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.compiler.common.type.StampPair;
import com.oracle.graal.nodes.ParameterNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderTool;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.java.StoreIndexedNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import tornado.api.Vector;
import tornado.common.exceptions.TornadoInternalError;
import tornado.drivers.opencl.graal.OCLStampFactory;
import tornado.drivers.opencl.graal.lir.OCLKind;
import tornado.drivers.opencl.graal.nodes.vector.*;

import static com.oracle.graal.compiler.common.util.Util.guarantee;
import static tornado.common.Tornado.ENABLE_VECTORS;
import static tornado.common.Tornado.TORNADO_ENABLE_BIFS;

public final class VectorPlugins {

    public static final void registerPlugins(final InvocationPlugins plugins) {
        if (ENABLE_VECTORS) {
            registerVectorPlugins(plugins, OCLKind.FLOAT2, float[].class, float.class);
            registerVectorPlugins(plugins, OCLKind.FLOAT3, float[].class, float.class);
            registerVectorPlugins(plugins, OCLKind.FLOAT4, float[].class, float.class);
            registerVectorPlugins(plugins, OCLKind.FLOAT8, float[].class, float.class);

            registerVectorPlugins(plugins, OCLKind.INT2, int[].class, int.class);
            registerVectorPlugins(plugins, OCLKind.INT3, int[].class, int.class);

            registerVectorPlugins(plugins, OCLKind.SHORT2, short[].class, short.class);

            registerVectorPlugins(plugins, OCLKind.CHAR3, byte[].class, byte.class);
            registerVectorPlugins(plugins, OCLKind.CHAR4, byte[].class, byte.class);

            /*
             * Geometric BIFS for floating point vectors
             */
            if (TORNADO_ENABLE_BIFS) {
                registerGeometricBIFS(plugins, OCLKind.FLOAT3, float[].class, float.class);
                registerGeometricBIFS(plugins, OCLKind.FLOAT4, float[].class, float.class);
            }
        }

    }

    private static VectorValueNode resolveReceiver(GraphBuilderContext b,
            OCLKind vectorKind, Receiver receiver) {
        ValueNode thisObject = receiver.get();
        VectorValueNode vector = null;

        if (thisObject instanceof PiNode) {
            thisObject = ((PiNode) thisObject).getOriginalNode();
        }

        if (thisObject instanceof VectorValueNode) {
            vector = (VectorValueNode) thisObject;
        }

        guarantee(vector != null, "unable to resolve vector");
        return vector;
    }

    private static void registerVectorPlugins(final InvocationPlugins plugins,
            final OCLKind vectorKind, final Class<?> storageType, final Class<?> elementType) {

        final Class<?> declaringClass = vectorKind.getJavaClass();
        final JavaKind javaElementKind = vectorKind.getElementKind().asJavaKind();

        final Registration r = new Registration(plugins, declaringClass);

        final Class<?>[] argumentTypes = new Class<?>[vectorKind.getVectorLength()];
        for (int i = 0; i < vectorKind.getVectorLength(); i++) {
            argumentTypes[i] = elementType;
        }

        final InvocationPlugin initialiser = new InvocationPlugin() {

            @Override
            public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode... args) {
                final VectorValueNode vector = resolveReceiver(b, vectorKind, receiver);
                if (args.length > 0) {
                    int offset = (vector == args[0]) ? 1 : 0;

                    for (int i = offset; i < args.length; i++) {
                        vector.setElement(i - offset, args[i]);
                    }
                } else {
                    vector.initialiseToDefaultValues(vector.graph());
                }

                return true;
            }
        };

        plugins.register(initialiser, declaringClass, "<init>");
        plugins.register(initialiser, declaringClass, "<init>", argumentTypes);

        r.register2("get", Receiver.class, int.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode laneId) {
                final VectorLoadElementNode loadElement = new VectorLoadElementNode(receiver.get(), laneId);
                b.push(javaElementKind, b.recursiveAppend(loadElement));
                return true;
            }
        });

        r.register3("set", Receiver.class, int.class, elementType, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode laneId, ValueNode value) {
                final VectorStoreElementProxyNode store = new VectorStoreElementProxyNode(
                        vectorKind.getElementKind(), receiver.get(), laneId, value);
                b.add(b.recursiveAppend(store));

                return true;
            }
        });

        r.register3("add", Receiver.class, declaringClass, declaringClass, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver reciever, ValueNode input1, ValueNode input2) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);

                OCLKind kind = OCLKind.fromResolvedJavaType(resolvedType);
                VectorAddNode addNode = new VectorAddNode(kind, input1, input2);
                b.push(JavaKind.Illegal, b.recursiveAppend(addNode));
                return true;
            }
        });

        r.register2("loadFromArray", storageType, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver reciever, ValueNode array, ValueNode index) {

                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);

                OCLKind kind = OCLKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                // node needed to enforce the value of the nodes stamp
                LoadIndexedVectorNode indexedLoad = new LoadIndexedVectorNode(kind, array, index, elementKind);
                b.push(JavaKind.Object, b.recursiveAppend(indexedLoad));
                return true;
            }
        });

        r.register3("storeToArray", Receiver.class, storageType, int.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver reciever, ValueNode array, ValueNode index) {
                final ResolvedJavaType resolvedType = b.getMetaAccess().lookupJavaType(declaringClass);

                ValueNode value = reciever.get();
                OCLKind kind = OCLKind.fromResolvedJavaType(resolvedType);
                JavaKind elementKind = kind.getElementKind().asJavaKind();
                // No need to set stamp as it is inferred from the stamp of the incoming value
                StoreIndexedNode indexedStore = new StoreIndexedNode(array, index, elementKind, value);
                b.append(b.recursiveAppend(indexedStore));
                return true;
            }
        });
    }

    private static final void registerGeometricBIFS(final InvocationPlugins plugins,
            final OCLKind vectorKind, final Class<?> storageType, final Class<?> elementType) {
        final Class<?> declaringClass = vectorKind.getJavaClass();

        final Registration r = new Registration(plugins, declaringClass);

        r.register2("dot", declaringClass, declaringClass, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode input1, ValueNode input2) {
                TornadoInternalError.unimplemented();
//                final BinaryGeometricOp op = new BinaryGeometricOp(vectorKind,
//                        OCLIntrinsicNode.GeometricOp.DOT, input1, input2);
//                b.push(vectorKind.getElementKind(), b.recursiveAppend(op));
//                b.append(b.recursiveAppend(new ValueAnchorNode(op)));

                return true;
            }
        });

    }

    static void registerParameterPlugins(Plugins plugins) {
        plugins.appendParameterPlugin((GraphBuilderTool tool, int index, StampPair stampPair) -> {
            //          System.out.printf("param: index=%d, stamp=%s\n",index,stamp);
            if (stampPair.getTrustedStamp() instanceof ObjectStamp) {
                ObjectStamp objStamp = (ObjectStamp) stampPair.getTrustedStamp();
                if (objStamp.type().getAnnotation(Vector.class) != null) {
                    OCLKind kind = OCLKind.fromResolvedJavaType(objStamp.type());
                    return new ParameterNode(index, StampPair.createSingle(OCLStampFactory.getStampFor(kind)));

                }
            }
            return null;
        });
    }
}
