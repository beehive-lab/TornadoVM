/*
 * Copyright (c) 2018, 2020-2022, 2024, 2025, APT Group, Department of Computer Science,
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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package uk.ac.manchester.tornado.drivers.cuda.graal.nodes;

import uk.ac.manchester.tornado.api.tile.DType;

/**
 * Marks every node of the CUDA Tile path, so the capability phase can find them all with a
 * single test instead of an ever growing instanceof chain like the MMA gate has to use.
 */
public interface CUDATileNode {

    /**
     * @return a short description of the operation, used in capability diagnostics
     */
    String tileOperationName();

    /**
     * Element type of the tile this node produces, or null when it produces no tile (a store,
     * a block index). Together with {@link #tileShape()} this is what lets the plugins resolve
     * the type of an accumulator that reaches an mma as a loop phi rather than as a fresh tile.
     *
     * @return the element type, or null
     */
    default DType tileDType() {
        return null;
    }

    /**
     * Shape of the tile this node produces, or null when it produces no tile.
     *
     * @return the shape, or null
     */
    default int[] tileShape() {
        return null;
    }
}
