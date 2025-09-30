/*
 * Copyright (c) 2018, 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 * Copyright (c) 2009, 2022, Oracle and/or its affiliates. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.metal.graal;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.lir.framemap.FrameMap;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import uk.ac.manchester.tornado.drivers.metal.graal.lir.MetalKind;

// @formatter:off
/**
 * AMD64 specific frame map.
 *
 * This is the format of an AMD64 stack frame:
 *
 * <pre>
 *   Base       Contents
 *
 *            :                                :  -----
 *   caller   | incoming overflow argument n   |    ^
 *   frame    :     ...                        :    | positive
 *            | incoming overflow argument 0   |    | offsets
 *   ---------+--------------------------------+---------------------
 *            | return address                 |    |            ^
 *   current  +--------------------------------+    |            |    -----
 *   frame    |                                |    |            |      ^
 *            : callee save area               :    |            |      |
 *            |                                |    |            |      |
 *            +--------------------------------+    |            |      |
 *            | spill slot 0                   |    | negative   |      |
 *            :     ...                        :    v offsets    |      |
 *            | spill slot n                   |  -----        total  frame
 *            +--------------------------------+               frame  size
 *            | alignment padding              |               size     |
 *            +--------------------------------+  -----          |      |
 *            | outgoing overflow argument n   |    ^            |      |
 *            :     ...                        :    | positive   |      |
 *            | outgoing overflow argument 0   |    | offsets    v      v
 *            +--------------------------------+---------------------------
 *
 * </pre>
 *
 * The spill slot area also includes stack allocated memory blocks (ALLOCA
 * blocks). The size of such a block may be greater than the size of a normal
 * spill slot or the word size.
 * <p>
 * A runtime can reserve space at the beginning of the overflow argument area.
 * The calling convention can specify that the first overflow stack argument is
 * not at offset 0, but at a specified offset. Use
 * {@link CodeCacheProvider#getMinimumOutgoingSize()} to make sure that
 * call-free methods also have this space reserved. Then the VM can use the
 * memory at offset 0 relative to the stack pointer.
 */
// @formatter:on
public class MetalFrameMap extends FrameMap {

    public MetalFrameMap(CodeCacheProvider codeCache, RegisterConfig registerConfig, ReferenceMapBuilderFactory referenceMapFactory) {
        super(codeCache, registerConfig, referenceMapFactory);

        // (negative) offset relative to sp + total frame size
        initialSpillSize = returnAddressSize();
        spillSize = initialSpillSize;
    }

    @Override
    public int totalFrameSize() {
        return frameSize() + returnAddressSize();
    }

    @Override
    public int currentFrameSize() {
        return alignFrameSize(outgoingSize + spillSize - returnAddressSize());
    }

    @Override
    protected int alignFrameSize(int size) {
        return NumUtil.roundUp(size + returnAddressSize(), getTarget().stackAlignment) - returnAddressSize();
    }

    public StackSlot allocateDeoptimizationRescueSlot() {
        assert spillSize == initialSpillSize || spillSize == initialSpillSize + spillSlotSize(LIRKind.value(
                MetalKind.ULONG)) : "Deoptimization rescue slot must be the first or second (if there is an RBP spill slot) stack slot";
        return allocateSpillSlot(LIRKind.value(MetalKind.ULONG));
    }
}
