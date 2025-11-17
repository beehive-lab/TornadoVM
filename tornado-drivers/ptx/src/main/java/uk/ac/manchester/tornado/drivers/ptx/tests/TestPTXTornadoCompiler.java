/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2013-2020, APT Group, Department of Computer Science,
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
 */
package uk.ac.manchester.tornado.drivers.ptx.tests;

import uk.ac.manchester.tornado.drivers.ptx.PTX;
import uk.ac.manchester.tornado.drivers.ptx.PTXBackendImpl;
import uk.ac.manchester.tornado.drivers.ptx.PTXCodeCache;
import uk.ac.manchester.tornado.drivers.ptx.PTXPlatform;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXCodeUtil;
import uk.ac.manchester.tornado.drivers.ptx.graal.PTXInstalledCode;
import uk.ac.manchester.tornado.drivers.ptx.graal.backend.PTXBackend;
import uk.ac.manchester.tornado.drivers.ptx.graal.compiler.PTXCompilationResult;
import uk.ac.manchester.tornado.runtime.TornadoCoreRuntime;
import uk.ac.manchester.tornado.runtime.tasks.meta.ScheduleContext;
import uk.ac.manchester.tornado.runtime.tasks.meta.TaskDataContext;

public class TestPTXTornadoCompiler {

    // @formatter:off
    private static final String PTX_KERNEL =
            ".visible .entry add( \n" +
            "	.param .u64 add_param_0, \n" +
            "	.param .u64 add_param_1, \n" +
            "	.param .u64 add_param_2 \n" +
            ") \n" +
            "{ \n" +
            "	.reg .f32 	%f<3>; \n" +
            "	.reg .b32 	%r<5>; \n" +
            "	.reg .f64 	%fd<4>; \n" +
            "	.reg .b64 	%rd<12>; \n" +
            "	ld.param.u64 	%rd1, [add_param_0]; \n" +
            "	ld.param.u64 	%rd2, [add_param_1]; \n" +
            "	ld.param.u64 	%rd3, [add_param_2]; \n" +
            "	cvta.to.global.u64 	%rd4, %rd1; \n" +
            "	cvta.to.global.u64 	%rd5, %rd3; \n" +
            "	cvta.to.global.u64 	%rd6, %rd2; \n" +
            "	mov.u32 	%r1, %ctaid.x; \n" +
            "	mov.u32 	%r2, %ntid.x; \n" +
            "	mov.u32 	%r3, %tid.x; \n" +
            "	mad.lo.s32 	%r4, %r2, %r1, %r3; \n" +
            "	mul.wide.s32 	%rd7, %r4, 4; \n" +
            "	add.s64 	%rd8, %rd6, %rd7; \n" +
            "	ld.global.f32 	%f1, [%rd8]; \n" +
            "	cvt.f64.f32	%fd1, %f1; \n" +
            "	mul.wide.s32 	%rd9, %r4, 8; \n" +
            "	add.s64 	%rd10, %rd5, %rd9; \n" +
            "	ld.global.f64 	%fd2, [%rd10]; \n" +
            "	add.f64 	%fd3, %fd1, %fd2; \n" +
            "	cvt.rn.f32.f64	%f2, %fd3; \n" +
            "	add.s64 	%rd11, %rd4, %rd7; \n" +
            "	st.global.f32 	[%rd11], %f2; \n" +
            "	ret; \n" +
            "} \n" ;


    // @formatter:on

    public static void main(String[] args) {

        final long executionPlanId = 0;
        PTXPlatform platform = PTX.getPlatform();
        PTXCodeCache codeCache = platform.getDevice(0).getPTXContext().getDeviceContext().getCodeCache(executionPlanId);

        TornadoCoreRuntime tornadoRuntime = TornadoCoreRuntime.getTornadoRuntime();
        PTXBackend backend = tornadoRuntime.getBackend(PTXBackendImpl.class).getDefaultBackend();
        TaskDataContext meta = new TaskDataContext(new ScheduleContext("s0"), "add", 0);
        new PTXCompilationResult("add", meta);

        byte[] source = PTX_KERNEL.getBytes();
        source = PTXCodeUtil.getCodeWithAttachedPTXHeader(source, backend);
        PTXInstalledCode code = codeCache.installSource("add", source, "add", meta.isPrintKernelEnabled());

        String generatedSourceCode = code.getGeneratedSourceCode();
        if (meta.isPrintKernelEnabled()) {
            System.out.println("Compiled code: " + generatedSourceCode);
        }
    }
}
