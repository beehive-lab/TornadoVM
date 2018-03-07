/*
 * This file is part of Tornado: A heterogeneous programming framework: 
 * https://github.com/beehive-lab/tornado
 *
 * Copyright (c) 2013-2018, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
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
 * Authors: James Clarkson
 *
 */
package uk.ac.manchester.tornado.drivers.opencl.runtime;

import uk.ac.manchester.tornado.api.meta.ScheduleMetaData;
import uk.ac.manchester.tornado.common.enums.Access;
import uk.ac.manchester.tornado.drivers.opencl.graal.OCLInstalledCode;
import uk.ac.manchester.tornado.drivers.opencl.graal.backend.OCLBackend;
import uk.ac.manchester.tornado.meta.domain.DomainTree;
import uk.ac.manchester.tornado.runtime.api.PrebuiltTask;

public class OCLPrebuiltTask extends PrebuiltTask {

    private OCLInstalledCode code;
    private final OCLBackend backend;

    protected OCLPrebuiltTask(ScheduleMetaData meta, String id, String entryPoint, String filename, Object[] args,
            Access[] access, OCLTornadoDevice device, DomainTree domain) {
        super(meta, id, entryPoint, filename, args, access, device, domain);

        backend = device.getBackend();
    }

    public void dumpCode() {
        for (byte b : code.getCode()) {
            System.out.printf("%c", b);
        }

    }

//    public void compile() {
//        final Path path = Paths.get(filename);
//        guarantee(path.toFile().exists(), "file does not exist: %s", filename);
//        try {
//            final byte[] source = Files.readAllBytes(path);
//            code = backend.getCodeCache().addMethod(null, entryPoint,
//                    source);
//        } catch (IOException e) {
//            shouldNotReachHere();
//        }
//
//    }
    public OCLInstalledCode getCode() {
        return code;
    }

}
