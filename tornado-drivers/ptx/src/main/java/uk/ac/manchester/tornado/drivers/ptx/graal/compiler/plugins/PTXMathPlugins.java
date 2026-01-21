/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020-2023, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.drivers.ptx.graal.compiler.plugins;

import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.FMAX;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.FMIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.POW;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.ATAN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.CEIL;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.COS;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.COSPI;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.EXP;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.FABS;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.FLOOR;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.LOG;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.RADIANS;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.SIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.SINPI;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.SQRT;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.TAN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.TANH;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntBinaryIntrinsicNode.Operation.MAX;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntBinaryIntrinsicNode.Operation.MIN;
import static uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntUnaryIntrinsicNode.Operation.ABS;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.ptx.graal.nodes.PTXIntUnaryIntrinsicNode;

public class PTXMathPlugins {

    public static void registerTornadoMathPlugins(final InvocationPlugins plugins) {
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

        registerHalfFloatMathPlugins(registration);
    }

    private static void registerHalfFloatMathPlugins(Registration r) {
        r.register(new InvocationPlugin("min", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                // Since HalfFloat is represented as short internally, we use JavaKind.Short
                b.push(JavaKind.Object, b.append(PTXFPBinaryIntrinsicNode.create(x, y, FMIN, JavaKind.Short)));
                return true;
            }
        });

        r.register(new InvocationPlugin("max", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                // Since HalfFloat is represented as short internally, we use JavaKind.Short
                b.push(JavaKind.Object, b.append(PTXFPBinaryIntrinsicNode.create(x, y, FMAX, JavaKind.Short)));
                return true;
            }
        });
    }

    private static void registerFloatMath1Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("atan", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, ATAN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sqrt", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, SQRT, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("exp", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, EXP, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, FABS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("floor", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, FLOOR, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("log", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, LOG, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sin", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, SIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cos", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, COS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tan", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, TAN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tanh", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, TANH, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("toRadians", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, RADIANS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sinpi", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, SINPI, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cospi", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, COSPI, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("ceil", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, CEIL, kind)));
                return true;
            }
        });

    }

    private static void registerFloatMath2Plugins(Registration r, Class<?> type, JavaKind kind) {

        r.register(new InvocationPlugin("pow", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(PTXFPBinaryIntrinsicNode.create(x, y, POW, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(PTXFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(PTXFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                return true;
            }
        });
    }

    private static void registerFloatMath3Plugins(Registration r, Class<?> type, JavaKind kind) {

    }

    private static void registerIntMath1Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXIntUnaryIntrinsicNode.create(value, ABS, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath2Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(PTXIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(PTXIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath3Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("clamp", type, type, type) {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y, ValueNode z) {
                ValueNode minMaxVal = PTXIntBinaryIntrinsicNode.create(z, x, MIN, kind);
                b.push(kind, b.append(PTXIntBinaryIntrinsicNode.create(y, minMaxVal, MAX, kind)));
                return true;
            }

        });
    }
}
