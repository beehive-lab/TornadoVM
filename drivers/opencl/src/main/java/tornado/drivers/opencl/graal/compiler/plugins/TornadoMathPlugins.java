package tornado.drivers.opencl.graal.compiler.plugins;

import static tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.*;
import static tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.*;
import static tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.*;
import static tornado.drivers.opencl.graal.nodes.OCLIntTernaryIntrinsicNode.Operation.*;
import static tornado.drivers.opencl.graal.nodes.OCLIntUnaryIntrinsicNode.Operation.*;
import tornado.collections.math.TornadoMath;
import tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode;
import tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode;
import tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import tornado.drivers.opencl.graal.nodes.OCLIntTernaryIntrinsicNode;
import tornado.drivers.opencl.graal.nodes.OCLIntUnaryIntrinsicNode;

import com.oracle.graal.api.meta.Kind;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.graphbuilderconf.MethodIdMap.Receiver;
import com.oracle.graal.nodes.ValueNode;

public class TornadoMathPlugins {

	public static final void registerTornadoMathPlugins(final InvocationPlugins plugins) {
		Registration registration = new Registration(plugins, TornadoMath.class);

		registerFloatMath1Plugins(registration, float.class, Kind.Float);
		registerFloatMath2Plugins(registration, float.class, Kind.Float);
		registerFloatMath3Plugins(registration, float.class, Kind.Float);

		registerFloatMath1Plugins(registration, double.class, Kind.Double);
		registerFloatMath2Plugins(registration, double.class, Kind.Double);
		registerFloatMath3Plugins(registration, double.class, Kind.Double);

		registerIntMath1Plugins(registration, int.class, Kind.Int);
		registerIntMath2Plugins(registration, int.class, Kind.Int);
		registerIntMath3Plugins(registration, int.class, Kind.Int);

		registerIntMath1Plugins(registration, long.class, Kind.Long);
		registerIntMath2Plugins(registration, long.class, Kind.Long);
		registerIntMath3Plugins(registration, long.class, Kind.Long);

		registerIntMath1Plugins(registration, short.class, Kind.Short);
		registerIntMath2Plugins(registration, short.class, Kind.Short);
		registerIntMath3Plugins(registration, short.class, Kind.Short);

		registerIntMath1Plugins(registration, byte.class, Kind.Byte);
		registerIntMath2Plugins(registration, byte.class, Kind.Byte);
		registerIntMath3Plugins(registration, byte.class, Kind.Byte);
	}

	private static final void registerFloatMath1Plugins(Registration r, Class<?> type, Kind kind) {
		r.register1("sqrt", type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode value) {
				b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, SQRT, kind)));
				return true;
			}
		});

		r.register1("exp", type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode value) {
				b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, EXP, kind)));
				return true;
			}
		});

		r.register1("abs", type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode value) {
				b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, FABS, kind)));
				return true;
			}
		});

		r.register1("floor", type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode value) {
				b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, FLOOR, kind)));
				return true;
			}
		});
		
		r.register1("log", type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode value) {
				b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, LOG, kind)));
				return true;
			}
		});
	}

	private static final void registerFloatMath2Plugins(Registration r, Class<?> type, Kind kind) {

		r.register2("min", type, type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode x, ValueNode y) {
				b.push(kind, b.recursiveAppend(OCLFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
				return true;
			}
		});

		r.register2("max", type, type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode x, ValueNode y) {
				b.push(kind, b.recursiveAppend(OCLFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
				return true;
			}
		});

	}

	private static final void registerFloatMath3Plugins(Registration r, Class<?> type, Kind kind) {

	}

	private static final void registerIntMath1Plugins(Registration r, Class<?> type, Kind kind) {
		r.register1("abs", type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode value) {
				b.push(kind, b.recursiveAppend(OCLIntUnaryIntrinsicNode.create(value, ABS, kind)));
				return true;
			}
		});
	}

	private static final void registerIntMath2Plugins(Registration r, Class<?> type, Kind kind) {
		r.register2("min", type, type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode x, ValueNode y) {
				b.push(kind, b.recursiveAppend(OCLIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
				return true;
			}
		});

		r.register2("max", type, type, new InvocationPlugin() {
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode x, ValueNode y) {
				b.push(kind, b.recursiveAppend(OCLIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
				return true;
			}
		});
	}

	private static final void registerIntMath3Plugins(Registration r, Class<?> type, Kind kind) {
		r.register3("clamp", type, type, type, new InvocationPlugin() {

			@Override
			public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
					Receiver receiver, ValueNode x, ValueNode y, ValueNode z) {
				b.push(kind,
						b.recursiveAppend(OCLIntTernaryIntrinsicNode.create(x, y, z, CLAMP, kind)));
				return true;
			}

		});
	}
}
