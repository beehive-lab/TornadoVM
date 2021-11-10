package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntBinaryIntrinsicNode.SPIRVIntOperation;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntTernaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntUnaryIntrinsicNode;

public class SPIRVMathPlugins {

    public static void registerTornadoMathPlugins(final InvocationPlugins plugins) {

        InvocationPlugins.Registration registration = new InvocationPlugins.Registration(plugins, TornadoMath.class);

        registerFloatMath1Plugins(registration, float.class, JavaKind.Float);
        registerTrigonometric1Plugins(registration, float.class, JavaKind.Float);
        registerFloatMath2Plugins(registration, float.class, JavaKind.Float);

        registerFloatMath1Plugins(registration, double.class, JavaKind.Double);
        registerFloatMath2Plugins(registration, double.class, JavaKind.Double);

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

    private static void registerFloatMath1Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {

        r.register1("sqrt", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.SQRT, kind)));
                return true;
            }
        });

        r.register1("exp", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.EXP, kind)));
                return true;
            }
        });

        r.register1("abs", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.FABS, kind)));
                return true;
            }
        });

        r.register1("floor", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.FLOOR, kind)));
                return true;
            }
        });

        r.register1("log", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.LOG, kind)));
                return true;
            }
        });
    }

    private static void registerTrigonometric1Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {

        r.register1("floatSin", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.SIN, kind)));
                return true;
            }
        });

        r.register1("floatCos", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.COS, kind)));
                return true;
            }
        });

        r.register1("floatSqrt", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.SQRT, kind)));
                return true;
            }
        });

        r.register1("floatAtan", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.ATAN, kind)));
                return true;
            }
        });

        r.register1("floatTan", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.TAN, kind)));
                return true;
            }
        });

        r.register1("floatTanh", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.TANH, kind)));
                return true;
            }
        });
    }

    private static void registerFloatMath2Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {

        r.register2("min", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, SPIRVFPBinaryIntrinsicNode.SPIRVOperation.FMIN, kind)));
                return true;
            }
        });

        r.register2("max", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, SPIRVFPBinaryIntrinsicNode.SPIRVOperation.FMAX, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath1Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {
        r.register1("abs", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVIntUnaryIntrinsicNode.create(value, SPIRVIntUnaryIntrinsicNode.SPIRVIntOperation.ABS, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath2Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {
        r.register2("min", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(SPIRVIntBinaryIntrinsicNode.create(x, y, SPIRVIntOperation.MIN, kind)));
                return true;
            }
        });

        r.register2("max", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(SPIRVIntBinaryIntrinsicNode.create(x, y, SPIRVIntOperation.MAX, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath3Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {
        r.register3("clamp", type, type, type, new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y, ValueNode z) {
                b.push(kind, b.append(SPIRVIntTernaryIntrinsicNode.create(x, y, z, SPIRVIntTernaryIntrinsicNode.Operation.CLAMP, kind)));
                return true;
            }

        });
    }

}
