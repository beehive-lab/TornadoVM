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
 *
 */
package uk.ac.manchester.tornado.runtime.domain;

public class DomainTree {

    private final Domain[] domains;

    public DomainTree(final int depth) {
        this.domains = new Domain[depth];
    }

    public void set(int index, final Domain domain) {
        domains[index] = domain;
    }

    public Domain get(int index) {
        return domains[index];
    }

    public int getDepth() {
        return domains.length;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("num domains=%d :", domains.length));
        sb.append("{ ");
        for (Domain dom : domains) {
            sb.append(String.format("%s, ", dom.toString()));
        }
        sb.setLength(sb.length() - 1);
        sb.append(" }");

        return sb.toString().trim();
    }

}
