package tornado.drivers.opencl.graal.compiler.plugins;

import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import tornado.collections.math.TornadoMath;
import tornado.drivers.opencl.graal.nodes.*;

import static tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.FMAX;
import static tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.FMIN;
import static tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.*;
import static tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.MAX;
import static tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.MIN;
import static tornado.drivers.opencl.graal.nodes.OCLIntTernaryIntrinsicNode.Operation.CLAMP;
import static tornado.drivers.opencl.graal.nodes.OCLIntUnaryIntrinsicNode.Operation.ABS;

public class TornadoMathPlugins {

    public static final void registerTornadoMathPlugins(final InvocationPlugins plugins) {
        Registration registration = new Registration(plugins, TornadoMath.class);

        registerFloatMath1Plugins(registration, float.class, JavaKind.Float);
        registerFloatMath2Plugins(registration, float.class, JavaKind.Float);
        registerFloatMath3Plugins(registration, float.class, JavaKind.Float);

        registerFloatMath1Plugins(registration, double.class, JavaKind.Double);
        registerFloatMath2Plugins(registration, double.class, JavaKind.Double);
        registerFloatMath3Plugins(registration, double.class, JavaKind.Double);

        registerIntMath1Plugins(registration, int.class, JavaKind.Int);
        registerIntMath2Plugins(registration, int.class, JavaKind.Int);
        registerIntMath3Plugins(registration, int.class, JavaKind.Int);

        registerIntMath1Plugins(registration, long.class, JavaKind.Long);
        registerIntMath2Plugins(registration, long.class, JavaKind.Long);
        registerIntMath3Plugins(registration, long.class, JavaKind.Long);

        registerIntMath1Plugins(registration, short.class, JavaKind.Short);
        registerIntMath2Plugins(registration, short.class, JavaKind.Short);
        registerIntMath3Plugins(registration, short.class, JavaKind.Short);

        registerIntMath1Plugins(registration, byte.class, JavaKind.Byte);
        registerIntMath2Plugins(registration, byte.class, JavaKind.Byte);
        registerIntMath3Plugins(registration, byte.class, JavaKind.Byte);
    }

    private static void registerFloatMath1Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register1("sqrt", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode value) {
                b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, SQRT, kind)));
                return true;
            }
        });

        r.register1("exp", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode value) {
                b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, EXP, kind)));
                return true;
            }
        });

        r.register1("abs", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode value) {
                b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, FABS, kind)));
                return true;
            }
        });

        r.register1("floor", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode value) {
                b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, FLOOR, kind)));
                return true;
            }
        });

        r.register1("log", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode value) {
                b.push(kind, b.recursiveAppend(OCLFPUnaryIntrinsicNode.create(value, LOG, kind)));
                return true;
            }
        });
    }

    private static void registerFloatMath2Plugins(Registration r, Class<?> type, JavaKind kind) {

        r.register2("min", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.recursiveAppend(OCLFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                return true;
            }
        });

        r.register2("max", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.recursiveAppend(OCLFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                return true;
            }
        });

    }

    private static void registerFloatMath3Plugins(Registration r, Class<?> type, JavaKind kind) {

    }

    private static void registerIntMath1Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register1("abs", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode value) {
                b.push(kind, b.recursiveAppend(OCLIntUnaryIntrinsicNode.create(value, ABS, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath2Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register2("min", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.recursiveAppend(OCLIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                return true;
            }
        });

        r.register2("max", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod,
                    Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.recursiveAppend(OCLIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath3Plugins(Registration r, Class<?> type, JavaKind kind) {
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
