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
package uk.ac.manchester.tornado.drivers.cuda.graal.compiler.plugins;

import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode.Operation.ATAN2;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode.Operation.FMAX;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode.Operation.FMIN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode.Operation.POW;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.ACOS;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.ACOSH;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.ASIN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.ASINH;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.ATAN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.CEIL;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.COS;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.COSPI;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.EXP;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.FABS;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.FLOOR;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.LOG;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.RADIANS;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.SIN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.SINPI;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.SQRT;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.TAN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode.Operation.TANH;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntBinaryIntrinsicNode.Operation.MAX;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntBinaryIntrinsicNode.Operation.MIN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntTernaryIntrinsicNode.Operation.CLAMP;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntUnaryIntrinsicNode.Operation.ABS;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.math.TornadoMath;
import uk.ac.manchester.tornado.api.types.HalfFloat;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntTernaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.CUDAIntUnaryIntrinsicNode;

public class CUDAMathPlugins {

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

        registerHalfFloatMathPlugins(registration);
    }

    private static void registerHalfFloatMathPlugins(Registration r) {
        r.register(new InvocationPlugin("min", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                // Since HalfFloat is represented as short internally, we use JavaKind.Short
                b.push(JavaKind.Object, b.append(CUDAFPBinaryIntrinsicNode.create(x, y, FMIN, JavaKind.Short)));
                return true;
            }
        });

        r.register(new InvocationPlugin("max", HalfFloat.class, HalfFloat.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                // Since HalfFloat is represented as short internally, we use JavaKind.Short
                b.push(JavaKind.Object, b.append(CUDAFPBinaryIntrinsicNode.create(x, y, FMAX, JavaKind.Short)));
                return true;
            }
        });
    }

    private static void registerFloatMath1Plugins(Registration r, Class<?> type, JavaKind kind) {

        r.register(new InvocationPlugin("sqrt", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, SQRT, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("exp", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, EXP, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, FABS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("floor", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, FLOOR, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("log", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, LOG, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("ceil", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, CEIL, kind)));
                return true;
            }
        });

    }

    private static void registerTrigonometric1Plugins(Registration r, Class<?> type, JavaKind kind) {

        r.register(new InvocationPlugin("acosh", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, ACOSH, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("asinh", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, ASINH, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan2", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(CUDAFPBinaryIntrinsicNode.create(x, y, ATAN2, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, ATAN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sin", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, SIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cos", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, COS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("acos", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, ACOS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tan", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, TAN, kind)));
                return true;
            }
        });
        r.register(new InvocationPlugin("asin", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, ASIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tanh", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, TANH, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("toRadians", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, RADIANS, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sinpi", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, SINPI, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cospi", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAFPUnaryIntrinsicNode.create(value, COSPI, kind)));
                return true;
            }
        });
    }

    private static void registerFloatMath2Plugins(Registration r, Class<?> type, JavaKind kind) {

        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(CUDAFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(CUDAFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("pow", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(CUDAFPBinaryIntrinsicNode.create(x, y, POW, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath1Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(CUDAIntUnaryIntrinsicNode.create(value, ABS, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath2Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(CUDAIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(CUDAIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                return true;
            }
        });
    }

    private static void registerIntMath3Plugins(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("clamp", type, type, type) {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y, ValueNode z) {
                b.push(kind, b.append(CUDAIntTernaryIntrinsicNode.create(x, y, z, CLAMP, kind)));
                return true;
            }

        });
    }
}
