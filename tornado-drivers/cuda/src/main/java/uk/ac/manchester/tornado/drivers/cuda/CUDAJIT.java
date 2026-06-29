/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2022, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.cuda;

import static uk.ac.manchester.tornado.runtime.TornadoCoreRuntime.getTornadoRuntime;

import java.lang.reflect.Method;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import uk.ac.manchester.tornado.api.common.TornadoDevice;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAInstalledCode;
import uk.ac.manchester.tornado.drivers.cuda.graal.CUDAProviders;
import uk.ac.manchester.tornado.drivers.cuda.graal.backend.CUDABackend;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompilationResult;
import uk.ac.manchester.tornado.drivers.cuda.graal.compiler.CUDACompiler;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.profiler.EmptyProfiler;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class CUDAJIT {

    public static void main(String[] args) {
        String className = args[0];
        String methodName = args[1];

        try {
            Class<?> declaringClass = Class.forName(className);

            Class<?>[] parameterTypes = null;
            if (args.length > 2) {
                parameterTypes = new Class<?>[args.length - 2];
                for (int i = 2; i < args.length; i++) {
                    if (args[i].equals("int")) {
                        parameterTypes[i - 2] = int.class;
                    } else if (args[i].equals("float")) {
                        parameterTypes[i - 2] = float.class;
                    } else {
                        parameterTypes[i - 2] = Class.forName(args[i]);
                    }
                }
            }

            Method method = declaringClass.getDeclaredMethod(methodName, parameterTypes);

            ResolvedJavaMethod resolvedMethod = getTornadoRuntime().resolveMethod(method);

            System.out.printf("method: name=%s, signature=%s\n", resolvedMethod.getName(), resolvedMethod.getSignature());

            final CUDABackend backend = getTornadoRuntime().getBackend(CUDABackendImpl.class).getDefaultBackend();

            TaskDataContext meta = TaskDataContext.create(new ScheduleContext("s0"), methodName, method);

            CUDACompilationResult result = CUDACompiler.compileCodeForDevice(resolvedMethod, new Object[] {}, meta, (CUDAProviders) backend.getProviders(), backend, new EmptyProfiler());

            final long executionPlanId = 0;

            TornadoDevice device = TornadoCoreRuntime.getTornadoRuntime().getBackend(CUDABackendImpl.class).getDefaultDevice();
            CUDADeviceContext deviceContext = (CUDADeviceContext) device.getDeviceContext();
            CUDAInstalledCode code = deviceContext.installCode(executionPlanId, result);

            for (byte b : code.getCode()) {
                System.out.printf("%c", b);
            }

        } catch (NoSuchMethodException | SecurityException | ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

}
