package tornado.drivers.opencl.graal.compiler.plugins;

import tornado.common.Tornado;
import tornado.drivers.opencl.graal.nodes.OCLIntrinsicNode;
import tornado.drivers.opencl.graal.nodes.OCLIntrinsicNode.BinaryGeometricOp;
import tornado.drivers.opencl.graal.nodes.vector.NewVectorNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorLoadElementProxyNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorLoadNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorStoreElementProxyNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorStoreNode;
import tornado.drivers.opencl.graal.nodes.vector.VectorValueNode;
import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.graphbuilderconf.MethodIdMap.Receiver;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.ValueAnchorNode;

public final class VectorPlugins {

	public static final void registerFloat3Plugins(final InvocationPlugins plugins) {
		if (Tornado.ENABLE_VECTORS) {
			registerVectorPlugins(plugins, VectorKind.FLOAT2, float[].class, float.class);
			registerVectorPlugins(plugins, VectorKind.FLOAT3, float[].class, float.class);
			registerVectorPlugins(plugins, VectorKind.FLOAT4, float[].class, float.class);
			registerVectorPlugins(plugins, VectorKind.FLOAT8, float[].class, float.class);

			registerVectorPlugins(plugins, VectorKind.INT2, int[].class, int.class);
			registerVectorPlugins(plugins, VectorKind.INT3, int[].class, int.class);

			registerVectorPlugins(plugins, VectorKind.SHORT2, short[].class, short.class);
			
			registerVectorPlugins(plugins, VectorKind.BYTE3, byte[].class, byte.class);
			registerVectorPlugins(plugins, VectorKind.BYTE4, byte[].class, byte.class);

			/*
			 * Geometric BIFS for floating point vectors
			 */
			if (Tornado.TORNADO_ENABLE_BIFS) {
				registerGeometricBIFS(plugins, VectorKind.FLOAT3, float[].class, float.class);
				registerGeometricBIFS(plugins, VectorKind.FLOAT4, float[].class, float.class);
			}
		}

	}

	private static final VectorValueNode resolveReceiver(GraphBuilderContext b,
			VectorKind vectorKind, Receiver receiver) {
		ValueNode thisObject = receiver.get();
		VectorValueNode vector = null;
		
		if (thisObject instanceof PiNode) {
			thisObject = ((PiNode) thisObject).getOriginalNode();
		}

		if (thisObject instanceof VectorValueNode) {
			vector = (VectorValueNode) thisObject;
		}
		return vector;
	}

	private static final void registerVectorPlugins(final InvocationPlugins plugins,
			final VectorKind vectorKind, final Class<?> storageType, final Class<?> elementType) {

		final Class<?> declaringClass = vectorKind.getJavaClass();

		final Registration r = new Registration(plugins, declaringClass);

		final Class<?>[] argumentTypes = new Class<?>[vectorKind.getVectorLength()];
		for (int i = 0; i < vectorKind.getVectorLength(); i++)
			argumentTypes[i] = elementType;

		final InvocationPlugin initialiser = new InvocationPlugin() {

			@Override
			public boolean defaultHandler(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode... args) {
				final VectorValueNode vector = resolveReceiver(b, vectorKind, receiver);
				final NewVectorNode newVectorNode = new NewVectorNode(vectorKind);
				vector.setOrigin(newVectorNode);
				b.append(newVectorNode);
	
				
				if (args.length > 0) {
					int offset = (vector == args[0])? 1 : 0;
					
					for (int i = offset; i < args.length; i++){
						vector.setElement(i-offset, args[i]);
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
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode laneId) {
				final VectorLoadElementProxyNode loadElement = new VectorLoadElementProxyNode(
						vectorKind, receiver.get(), laneId);
				b.push(vectorKind.getElementKind(), b.recursiveAppend(loadElement));
				return true;
			}
		});

		r.register3("set", Receiver.class, int.class, elementType, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode laneId, ValueNode value) {

				final VectorStoreElementProxyNode store = new VectorStoreElementProxyNode(
						vectorKind, receiver.get(), laneId, value);
				b.append(b.recursiveAppend(store));

				return true;
			}
		});

		r.register2("loadFromArray", storageType, int.class, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver reciever, ValueNode array, ValueNode index) {

				final VectorLoadNode load = new VectorLoadNode(vectorKind, array, index);
				final VectorValueNode vector = new VectorValueNode(vectorKind);
				vector.setOrigin(load);
				b.add(load);
				b.push(vector.getKind(), b.recursiveAppend(vector));
				return true;
			}
		});

		r.register3("storeToArray", Receiver.class, storageType, int.class, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver reciever, ValueNode array, ValueNode index) {

				final VectorStoreNode store = new VectorStoreNode(vectorKind, array, index,
						reciever.get());
				b.append(b.recursiveAppend(store));
				return true;
			}
		});
	}

	private static final void registerGeometricBIFS(final InvocationPlugins plugins,
			final VectorKind vectorKind, final Class<?> storageType, final Class<?> elementType) {
		final Class<?> declaringClass = vectorKind.getJavaClass();

		final Registration r = new Registration(plugins, declaringClass);

		r.register2("dot", declaringClass, declaringClass, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode input1, ValueNode input2) {
				final BinaryGeometricOp op = new BinaryGeometricOp(vectorKind,
						OCLIntrinsicNode.GeometricOp.DOT, input1, input2);
				b.push(vectorKind.getElementKind(), b.recursiveAppend(op));
				b.append(b.recursiveAppend(new ValueAnchorNode(op)));

				return true;
			}
		});
		
	}
}
