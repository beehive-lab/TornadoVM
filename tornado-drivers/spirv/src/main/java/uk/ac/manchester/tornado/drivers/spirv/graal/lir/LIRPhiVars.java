/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2022, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2009-2022, Oracle and/or its affiliates. All rights reserved.
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
 */
package uk.ac.manchester.tornado.drivers.spirv.graal.lir;

import java.util.ArrayList;
import java.util.List;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * Class to keep a list of Phi Variables using the result new value and the
 * variable that is carried from the SSA representation.
 */
public class LIRPhiVars {

    private List<PhiMeta> phiVars;

    public LIRPhiVars() {
        this.phiVars = new ArrayList<>();
    }

    public void insertPhiValue(AllocatableValue resultPhi, Value value) {
        phiVars.add(new PhiMeta(resultPhi, value));
    }

    public List<PhiMeta> getPhiVars() {
        return phiVars;
    }

    public static class PhiMeta {
        AllocatableValue resultPhi;
        Value value;

        public PhiMeta(AllocatableValue resultPhi, Value value) {
            this.resultPhi = resultPhi;
            this.value = value;
        }

        public Value getValue() {
            return value;
        }

        public AllocatableValue getResultPhi() {
            return resultPhi;
        }
    }

}
