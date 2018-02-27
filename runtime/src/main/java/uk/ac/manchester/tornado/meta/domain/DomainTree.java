/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018 APT Group, School of Computer Science, 
 * The University of Manchester
 *
 * This work is partially supported by EPSRC grants:
 * Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.meta.domain;

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
