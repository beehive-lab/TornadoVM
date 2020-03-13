#!/usr/bin/env bash

#
# This file is part of Tornado: A heterogeneous programming framework:
# https://github.com/beehive-lab/tornadovm
#
# Copyright (c) 2020, APT Group, Department of Computer Science,
# Department of Engineering, The University of Manchester. All rights reserved.
# Copyright (c) 2013-2019, APT Group, Department of Computer Science,
# The University of Manchester. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Authors: James Clarkson
#

# New modules and module exports have to be added here
PACKAGE_LIST=(
--add-modules ALL-SYSTEM,,tornado.runtime,tornado.annotation,tornado.drivers.opencl
--add-exports jdk.internal.vm.ci/jdk.vm.ci.common=tornado.drivers.opencl,jdk.internal.vm.compiler
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.hotspot.meta=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.replacements.classfile=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.common.alloc=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.common.util=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.common.cfg=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.lir=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.lir.framemap=tornado.drivers.opencl
--add-exports jdk.internal.vm.ci/jdk.vm.ci.meta=tornado.drivers.opencl,tornado.runtime,tornado.annotation
--add-exports jdk.internal.vm.ci/jdk.vm.ci.code=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.graph=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.graph.spi=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.lir.gen=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodeinfo=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.calc=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.spi=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.api.runtime=tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.code=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.common=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.target=tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.debug=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.hotspot=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.java=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.lir.asm=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.lir.phases=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.graphbuilderconf=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.options=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.phases=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.phases.tiers=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.phases.util=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.printer=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.runtime=tornado.runtime
--add-exports jdk.internal.vm.ci/jdk.vm.ci.runtime=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.graph.iterators=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.java=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.bytecode=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.phases.common=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.common.spi=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.api.replacements=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.replacements=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.phases.common.inlining=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.phases=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.common.type=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.extended=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.loop=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.phases.common.inlining.info=tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.phases.common.inlining.policy=tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.phases.common.inlining.walker=tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.loop.phases=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.debug=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.memory=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.util=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.virtual=tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.lir.constopt=tornado.runtime
--add-opens jdk.internal.vm.ci/jdk.vm.ci.hotspot=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.asm=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.gc=tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.cfg=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.phases.schedule=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.virtual.phases.ea=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.lir.ssa=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.common.calc=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.gen=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.core.match=tornado.drivers.opencl
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.memory.address=tornado.drivers.opencl,tornado.runtime
--add-exports jdk.internal.vm.compiler/org.graalvm.compiler.nodes.type=tornado.drivers.opencl
)
