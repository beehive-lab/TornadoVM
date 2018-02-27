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
