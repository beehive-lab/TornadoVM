/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2021-2022, 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2021, Oracle and/or its affiliates. All rights reserved.
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

import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.getJavaKindFromValueLayoutClass;
import static uk.ac.manchester.tornado.drivers.common.code.CodeUtil.getValueLayoutClass;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode.SPIRVOperation.ATAN2;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode.SPIRVOperation.FMAX;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode.SPIRVOperation.FMIN;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode.SPIRVOperation.POW;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.ACOS;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.ASIN;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.ATAN;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.COS;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.EXP;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.FABS;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.LOG;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.SIGN;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.SIN;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.SQRT;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.TAN;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode.SPIRVUnaryOperation.TANH;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntBinaryIntrinsicNode.SPIRVIntOperation.MAX;
import static uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntBinaryIntrinsicNode.SPIRVIntOperation.MIN;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.extended.JavaReadNode;
import org.graalvm.compiler.nodes.extended.JavaWriteNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.replacements.InlineDuringParsingPlugin;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.KernelContext;
import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.drivers.common.logging.Logger;
import uk.ac.manchester.tornado.drivers.spirv.graal.SPIRVArchitecture;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVKind;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.LocalArrayNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVBarrierNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVConvertHalfToFloat;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVFPUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntBinaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SPIRVIntUnaryIntrinsicNode;
import uk.ac.manchester.tornado.drivers.spirv.graal.nodes.SlotsBaseAddressNode;
import uk.ac.manchester.tornado.runtime.common.TornadoOptions;
import uk.ac.manchester.tornado.runtime.directives.CompilerInternals;

public class SPIRVGraphBuilderPlugins {

    public static void registerParametersPlugins(Plugins plugins) {
        SPIRVVectorPlugins.registerParameterPlugins(plugins);
    }

    public static void registerNewInstancePlugins(Plugins plugins) {
        plugins.appendNodePlugin(new SPIRVVectorNodePlugin());
        // FIXME: Atomics for SPIRV Backend not implemented.
    }

    public static void registerInvocationPlugins(Plugins plugins, final InvocationPlugins invocationPlugins) {

        if (TornadoOptions.INLINE_DURING_BYTECODE_PARSING) {
            plugins.appendInlineInvokePlugin(new InlineDuringParsingPlugin());
        }

        registerCompilerIntrinsicsPlugins(invocationPlugins);
        registerFP16ConversionPlugins(invocationPlugins);
        registerTornadoVMIntrinsicsPlugins(plugins);
        registerOpenCLBuiltinPlugins(invocationPlugins);

        // Register plugins for the new API
        registerKernelContextPlugins(invocationPlugins);

        SPIRVMathPlugins.registerTornadoMathPlugins(invocationPlugins);
        SPIRVVectorPlugins.registerPlugins(plugins, invocationPlugins);

        SPIRVHalfFloatPlugins.registerPlugins(plugins, invocationPlugins);
        // Register plugins for Off-Heap Arrays with Panama
        registerMemoryAccessPlugins(plugins);
    }

    private static void registerOpenCLBuiltinPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, java.lang.Math.class);
        // We have to overwrite some standard math plugins
        r.setAllowOverwrite(true);
        registerOpenCLOverridesForType(r, Float.TYPE, JavaKind.Float);
        registerOpenCLOverridesForType(r, Double.TYPE, JavaKind.Double);
        registerOpenCLOverridesForType(r, Integer.TYPE, JavaKind.Int);
        registerOpenCLOverridesForType(r, Long.TYPE, JavaKind.Long);
        registerFPIntrinsics(r);

        Registration longRegistration = new Registration(plugins, Long.class);
        longRegistration.register(new InvocationPlugin("bitCount", Long.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(SPIRVIntUnaryIntrinsicNode.create(value, SPIRVIntUnaryIntrinsicNode.SPIRVIntOperation.POPCOUNT, JavaKind.Long)));
                return true;
            }
        });

        Registration intRegistration = new Registration(plugins, Integer.class);
        intRegistration.register(new InvocationPlugin("bitCount", Integer.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Int, b.append(SPIRVIntUnaryIntrinsicNode.create(value, SPIRVIntUnaryIntrinsicNode.SPIRVIntOperation.POPCOUNT, JavaKind.Int)));
                return true;
            }
        });

    }

    private static void registerKernelContextPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, KernelContext.class);
        registerLocalBarrier(r);
        registerGlobalBarrier(r);
        localArraysPlugins(r);
    }

    private static void registerLocalBarrier(Registration r) {
        r.register(new InvocationPlugin("localBarrier", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                SPIRVBarrierNode localBarrierNode = new SPIRVBarrierNode(SPIRVBarrierNode.SPIRVMemFenceFlags.LOCAL);
                b.append(localBarrierNode);
                return true;
            }
        });
    }

    private static void registerGlobalBarrier(Registration r) {
        r.register(new InvocationPlugin("globalBarrier", InvocationPlugin.Receiver.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                SPIRVBarrierNode barrierNode = new SPIRVBarrierNode(SPIRVBarrierNode.SPIRVMemFenceFlags.GLOBAL);
                b.append(barrierNode);
                return true;
            }
        });
    }

    private static void localArraysPlugins(Registration r) {
        JavaKind returnedJavaKind = JavaKind.Object;

        registerLocalArray(r, "allocateIntLocalArray", returnedJavaKind, SPIRVKind.OP_TYPE_INT_32.asJavaKind());
        registerLocalArray(r, "allocateLongLocalArray", returnedJavaKind, SPIRVKind.OP_TYPE_INT_64.asJavaKind());
        registerLocalArray(r, "allocateFloatLocalArray", returnedJavaKind, SPIRVKind.OP_TYPE_FLOAT_32.asJavaKind());
        registerLocalArray(r, "allocateDoubleLocalArray", returnedJavaKind, SPIRVKind.OP_TYPE_FLOAT_64.asJavaKind());
    }

    private static void registerLocalArray(Registration r, final String method, JavaKind returnedJavaKind, JavaKind elementType) {
        r.register(new InvocationPlugin(method, InvocationPlugin.Receiver.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode size) {
                ConstantNode constantNode = new ConstantNode(size.asConstant(), StampFactory.forKind(JavaKind.Int));
                LocalArrayNode localArrayNode = new LocalArrayNode(SPIRVArchitecture.localSpace, elementType, constantNode);
                b.push(returnedJavaKind, localArrayNode);
                return true;
            }
        });
    }

    private static void registerOpenCLOverridesForType(Registration r, Class<?> type, JavaKind kind) {
        r.register(new InvocationPlugin("min", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, FMIN, kind)));
                } else {
                    b.push(kind, b.append(SPIRVIntBinaryIntrinsicNode.create(x, y, MIN, kind)));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("max", type, type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, FMAX, kind)));
                } else {
                    b.push(kind, b.append(SPIRVIntBinaryIntrinsicNode.create(x, y, MAX, kind)));
                }
                return true;
            }
        });

        r.register(new InvocationPlugin("abs", type) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                if (kind.isNumericFloat()) {
                    b.push(kind, b.append(SPIRVFPUnaryIntrinsicNode.create(value, FABS, kind)));
                }
                return true;
            }
        });
    }

    private static void registerFPIntrinsics(Registration r) {
        r.register(new InvocationPlugin("pow", Double.TYPE, Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, POW, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("signum", Float.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Float, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SIGN, JavaKind.Float)));
                return true;
            }
        });

        r.register(new InvocationPlugin("signum", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SIGN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sin", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SIN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("cos", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(value, COS, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tan", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(value, TAN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("tanh", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(value, TANH, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(value, ATAN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("atan2", Double.TYPE, Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                b.push(JavaKind.Double, b.append(SPIRVFPBinaryIntrinsicNode.create(x, y, ATAN2, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("asin", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(x, ASIN, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("acos", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(x, ACOS, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("log", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(value, LOG, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("sqrt", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(value, SQRT, JavaKind.Double)));
                return true;
            }
        });

        r.register(new InvocationPlugin("exp", Double.TYPE) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode value) {
                b.push(JavaKind.Double, b.append(SPIRVFPUnaryIntrinsicNode.create(value, EXP, JavaKind.Double)));
                return true;
            }
        });
    }

    /**
     * The current implementation of the SPIR-V backend provides a prebuilt kernel for the LookupBuffer Address. We keep this method as a reference, in the case we want to update how to lookup buffer
     * address works.
     *
     * @param plugins
     *     {@link InvocationPlugins}
     */
    private static void registerCompilerIntrinsicsPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, CompilerInternals.class);
        r.register(new InvocationPlugin("getSlotsAddress") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.addPush(JavaKind.Object, new SlotsBaseAddressNode());
                return true;
            }
        });
    }

    private static void registerFP16ConversionPlugins(InvocationPlugins plugins) {
        Registration r = new Registration(plugins, Float.class);
        r.register(new InvocationPlugin("float16ToFloat", short.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode halfValue) {
                SPIRVConvertHalfToFloat convertHalfToFloat = new SPIRVConvertHalfToFloat(halfValue);
                b.addPush(JavaKind.Float, convertHalfToFloat);
                return true;
            }
        });
    }

    private static void registerTornadoVMIntrinsicsPlugins(Plugins plugins) {
        if (TornadoOptions.DEBUG) {
            Logger.traceRuntime(Logger.BACKEND.SPIRV, "SPIRV Registering VM Intrinsics Plugins - pending");
        }
    }

    private static void registerMemoryAccessPlugins(final Plugins ps) {
        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                // "MemorySegment.getAtIndex(ValueLayout, long)"
                if (!MemorySegment.class.getName().equals(method.getDeclaringClass().toJavaName())) {
                    return false;
                }
                if (!"getAtIndex".equals(method.getName())) {
                    return false;
                }
                if (args.length != 3) {
                    throw new TornadoRuntimeException("Expecting 3 arguments for getAtIndex but got " + args.length);
                }
                ValueNode receiver = args[0];
                ValueNode layout = args[1];
                ValueNode index = args[2];

                Class valueLayoutClass = getValueLayoutClass(layout);
                JavaKind kind = getJavaKindFromValueLayoutClass(valueLayoutClass);

                MulNode mulNode = b.append(new MulNode(index, ConstantNode.forInt(kind.getByteCount())));
                AddressNode addressNode = b.append(new OffsetAddressNode(receiver, mulNode));
                JavaReadNode readNode = new JavaReadNode(kind, addressNode, LocationIdentity.any(), BarrierType.NONE, MemoryOrderMode.PLAIN, false);
                b.addPush(kind, readNode);
                return true;
            }
        });
        ps.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                // "MemorySegment.setAtIndex(ValueLayout, long, kind)"
                if (!MemorySegment.class.getName().equals(method.getDeclaringClass().toJavaName())) {
                    return false;
                }
                if (!"setAtIndex".equals(method.getName())) {
                    return false;
                }
                if (args.length != 4) {
                    throw new TornadoRuntimeException("Expecting 4 arguments for setAtIndex but got " + args.length);
                }
                ValueNode receiver = args[0];
                ValueNode layout = args[1];
                ValueNode index = args[2];
                ValueNode value = args[3];

                Class valueLayoutClass = getValueLayoutClass(layout);
                JavaKind kind = getJavaKindFromValueLayoutClass(valueLayoutClass);

                MulNode mulNode = b.append(new MulNode(index, ConstantNode.forInt(kind.getByteCount())));
                AddressNode addressNode = b.append(new OffsetAddressNode(receiver, mulNode));
                JavaWriteNode writeNode = new JavaWriteNode(kind, addressNode, LocationIdentity.any(), value, BarrierType.NONE, false);
                b.add(writeNode);
                return true;
            }
        });
    }

}
