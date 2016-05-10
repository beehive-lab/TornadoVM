/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package tornado.drivers.opencl.graal.lir;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import java.util.*;

import tornado.drivers.opencl.graal.lir.OCLAddressOps.OCLAddress;
import tornado.graal.nodes.vector.VectorKind;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.*;

@Deprecated
public final class OCLAddressValue extends CompositeValue {

    @Component({REG, OperandFlag.ILLEGAL}) protected Value base;
    @Component({REG, OperandFlag.ILLEGAL}) protected Value index;
    protected final int scale;
    protected final long displacement;
    private Kind scalarKind;
    private static final EnumSet<OperandFlag> flags = EnumSet.of(OperandFlag.REG, OperandFlag.ILLEGAL);

    public OCLAddressValue(VectorKind kind, AllocatableValue base, int displacement) {
        this(LIRKind.value(kind.getElementKind()), base, Value.ILLEGAL, 1, displacement);
        setAccessKind(kind);
    }
    
    public OCLAddressValue(Kind kind, AllocatableValue base, int displacement) {
        this(LIRKind.value(kind), base, Value.ILLEGAL, 1, displacement);
        setAccessKind(kind);
    }
    
    public OCLAddressValue(LIRKind kind, AllocatableValue base, int displacement) {
        this(kind, base, Value.ILLEGAL, 1, displacement);
    }

    public OCLAddressValue(LIRKind kind, Value base, Value index, int scale, long displacement) {
        super(kind);
        this.base = base;
        this.index = index;
        this.scale = scale;
        this.displacement = displacement;

    }
    
    public void setAccessKind(Kind value){
    	scalarKind = value;
    }
    

    @Override
    public CompositeValue forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueProcedure proc) {
        AllocatableValue newBase = (AllocatableValue) proc.doValue(inst, base, mode, flags);
        AllocatableValue newIndex = (AllocatableValue) proc.doValue(inst, index, mode, flags);
        if (!base.identityEquals(newBase) || !index.identityEquals(newIndex)) {
            return new OCLAddressValue(getLIRKind(), newBase, newIndex, scale, displacement);
        }
        return this;
    }

    @Override
    protected void forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueConsumer proc) {
        proc.visitValue(inst, base, mode, flags);
        proc.visitValue(inst, index, mode, flags);
    }

    private static Register toRegister(AllocatableValue value) {
        if (value.equals(Value.ILLEGAL)) {
            return Register.None;
        } else {
            RegisterValue reg = (RegisterValue) value;
            return reg.getRegister();
        }
    }

    public OCLAddress toAddress(PlatformKind kind) {
    	return new OCLAddressOps.OCLAddress(kind,base,index,displacement,scale);
    }
    
    public OCLAddress toAddress(){
    	return toAddress(scalarKind);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("[");
        String sep = "";
        if (isLegal(base)) {
            s.append(base);
            sep = " + ";
        }
        if (isLegal(index)) {
            s.append(sep).append(index).append(" * ").append(scale);
            sep = " + ";
        }
        if (displacement < 0) {
            s.append(" - ").append(-displacement);
        } else if (displacement > 0) {
            s.append(sep).append(displacement);
        }
        s.append("]");
        return s.toString();
    }

    public boolean isValidImplicitNullCheckFor(Value value, int implicitNullCheckLimit) {
        return value.equals(base) && index.equals(Value.ILLEGAL) && displacement >= 0 && displacement < implicitNullCheckLimit;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OCLAddressValue) {
            OCLAddressValue addr = (OCLAddressValue) obj;
            return getLIRKind().equals(addr.getLIRKind()) && displacement == addr.displacement && base.equals(addr.base) && scale == addr.scale && index.equals(addr.index);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (int) (base.hashCode() ^ index.hashCode() ^ (displacement << 4) ^ (scale << 8) ^ getLIRKind().hashCode());
    }

	public void setAccessKind(VectorKind value) {
		scalarKind = value.getElementKind();
	}
}
