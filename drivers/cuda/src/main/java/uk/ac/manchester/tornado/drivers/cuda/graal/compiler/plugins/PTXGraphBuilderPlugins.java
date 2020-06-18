/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.cuda.graal.compiler.plugins;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXIntBinaryIntrinsicNode;
//import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PrintfNode;
//import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.SlotsBaseAddressNode;
//import uk.ac.manchester.tornado.drivers.cuda.graal.nodes.TPrintfNode;

import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.FMAX;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.FMIN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPBinaryIntrinsicNode.Operation.POW;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.COS;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.EXP;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.FABS;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.LOG;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXFPUnaryIntrinsicNode.Operation.SIN;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXIntBinaryIntrinsicNode.Operation.MAX;
import static uk.ac.manchester.tornado.drivers.cuda.graal.nodes.PTXIntBinaryIntrinsicNode.Operation.MIN;

public class PTXGraphBuilderPlugins {

    public static void registerInvocationPlugins(final Plugins ps, final InvocationPlugins plugins) {
        registerPTXBuiltinPlugins(plugins);

        PTXMathPlugins.registerTornadoMathPlugins(plugins);
        PTXVectorPlugins.registerPlugins(ps, plugins);
    }

    private static void registerPTXBuiltinPlugins(InvocationPlugins plugins) {

        Registration r = new Registration(plugins, Math.class);
        r.setAllowOverwrite(true);
        registerPTXOverridesForType(r, Float.TYPE, JavaKind.Float);
        registerPTXOverridesForType(r, Double.TYPE, JavaKind.Double);
        registerPTXOverridesForType(r, Integer.TYPE, JavaKind.Int);
        registerPTXOverridesForType(r, Long.TYPE, JavaKind.Long);
        registerFPIntrinsics(r, Float.TYPE, JavaKind.Float);
        registerFPIntrinsics(r, Double.TYPE, JavaKind.Double);
    }

    private static void registerFPIntrinsics(Registration r, Class<?> type, JavaKind kind) {
        r.register2("pow", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(kind, b.append(PTXFPBinaryIntrinsicNode.create(x, y, POW, kind)));
                return true;
            }
        });

        r.register1("sin", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, SIN, kind)));
                return true;
            }
        });

        r.register1("cos", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, COS, kind)));
                return true;
            }
        });

        r.register1("log", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, LOG, kind)));
                return true;
            }
        });

        r.register1("exp", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, EXP, kind)));
                return true;
            }
        });
    }

    private static void registerPTXOverridesForType(Registration r, Class<?> type, JavaKind kind) {
        r.register2("min", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(PTXFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                } else {
                    b.push(kind, b.append(PTXIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                }
                return true;
            }
        });

        r.register2("max", type, type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(PTXFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                } else {
                    b.push(kind, b.append(PTXIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                }
                return true;
            }
        });

        r.register1("abs", type, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(PTXFPUnaryIntrinsicNode.create(value, FABS, kind)));
                }
                return true;
            }
        });

    }

    public static void registerClassInitializationPlugins(Plugins plugins) {

    }

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.appendNodePlugin(new PTXVectorNodePlugin());
    }

    public static void registerParameterPlugins(Plugins plugins) {
        PTXVectorPlugins.registerParameterPlugins(plugins);
    }
}
