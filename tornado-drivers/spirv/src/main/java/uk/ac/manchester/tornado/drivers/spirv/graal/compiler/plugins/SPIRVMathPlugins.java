/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.compiler.plugins;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.math.TornadoMath;
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
        registerFloatMath2Plugins(registration, float.class, JavaKind.Float);
        registerTrigonometric1Plugins(registration, float.class, JavaKind.Float);

        registerFloatMath1Plugins(registration, double.class, JavaKind.Double);
        registerFloatMath2Plugins(registration, double.class, JavaKind.Double);
        registerTrigonometric1Plugins(registration, double.class, JavaKind.Double);

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

        r.register(new InvocationPlugin("acosh", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.ACOSH, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("asinh", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.ASINH, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sqrt", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.SQRT, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("exp", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.EXP, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.FABS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("floor", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.FLOOR, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("log", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.LOG, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("ceil", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.CEIL, kind)));
                return true;
            }
        });
    }

    private static void registerTrigonometric1Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {

        r.register(new InvocationPlugin("sin", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.SIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cos", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.COS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.ATAN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan2", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, SPIRVFPBinaryIntrinsicNode.SPIRVOperation.ATAN2, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("acos", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.ACOS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("asin", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.ASIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tan", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.TAN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tanh", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.TANH, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("toRadians", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.RADIANS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sinpi", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.SINPI, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cospi", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SPIRVUnaryOperation.COSPI, kind)));
                return true;
            }
        });
    }

    private static void registerFloatMath2Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {

        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, SPIRVFPBinaryIntrinsicNode.SPIRVOperation.FMIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, SPIRVFPBinaryIntrinsicNode.SPIRVOperation.FMAX, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("pow", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, SPIRVFPBinaryIntrinsicNode.SPIRVOperation.POW, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath1Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(SPIRVIntUnaryIntrinsicNode.create(value, SPIRVIntUnaryIntrinsicNode.SPIRVIntOperation.ABS, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath2Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(SPIRVIntBinaryIntrinsicNode.create(x, y, SPIRVIntOperation.MIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(SPIRVIntBinaryIntrinsicNode.create(x, y, SPIRVIntOperation.MAX, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath3Plugins(InvocationPlugins.Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("clamp", type, type, type) {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y, ValueNode z) {
                b.push(kind, b.append(SPIRVIntTernaryIntrinsicNode.create(x, y, z, SPIRVIntTernaryIntrinsicNode.Operation.CLAMP, kind)));
                return true;
            }

        });
    }
}
