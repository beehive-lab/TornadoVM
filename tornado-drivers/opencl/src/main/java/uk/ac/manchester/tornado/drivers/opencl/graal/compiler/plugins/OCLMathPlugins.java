/*
 * Copyright (c) 2022-2023, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.opencl.graal.compiler.plugins;

import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.ATAN2;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.FMAX;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.FMIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode.Operation.POW;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.ACOS;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.ACOSH;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.ASIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.ASINH;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.ATAN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.CEIL;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.COS;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.COSPI;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.EXP;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.FABS;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.FLOOR;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.LOG;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.RADIANS;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.SIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.SINPI;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.SQRT;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.TAN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode.Operation.TANH;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.MAX;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode.Operation.MIN;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntTernaryIntrinsicNode.Operation.CLAMP;
import static uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntUnaryIntrinsicNode.Operation.ABS;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntTernaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.opencl.graal.nodes.OCLIntUnaryIntrinsicNode;

public class OCLMathPlugins {

    public static void registerTornadoMathPlugins(final InvocationPlugins plugins) {
        Registration registration = new Registration(plugins, TornadoMath.class);

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

    private static void registerFloatMath1Plugins(Registration r, Class<?> type, JavaKind kind) {

        r.register(new InvocationPlugin("sqrt", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, SQRT, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("exp", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, EXP, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, FABS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("floor", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, FLOOR, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("log", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, LOG, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("ceil", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, CEIL, kind)));
                return true;
            }
        });

    }

    private static void registerTrigonometric1Plugins(Registration r, Class<?> type, JavaKind kind) {

        r.register(new InvocationPlugin("acosh", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, ACOSH, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("asinh", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, ASINH, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan2", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, ATAN2, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, ATAN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sin", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, SIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cos", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, COS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("acos", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, ACOS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tan", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, TAN, kind)));
                return true;
            }
        });
        r.register(new InvocationPlugin("asin", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, ASIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tanh", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, TANH, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("toRadians", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, RADIANS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sinpi", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, SINPI, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cospi", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLFPUnaryIntrinsicNode.create(value, COSPI, kind)));
                return true;
            }
        });
    }

    private static void registerFloatMath2Plugins(Registration r, Class<?> type, JavaKind kind) {

        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("pow", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(OCLFPBinaryIntrinsicNode.create(x, y, POW, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath1Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(OCLIntUnaryIntrinsicNode.create(value, ABS, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath2Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(OCLIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(OCLIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath3Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("clamp", type, type, type) {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y, ValueNode z) {
                b.push(kind, b.append(OCLIntTernaryIntrinsicNode.create(x, y, z, CLAMP, kind)));
                return true;
            }

        });
    }
}
