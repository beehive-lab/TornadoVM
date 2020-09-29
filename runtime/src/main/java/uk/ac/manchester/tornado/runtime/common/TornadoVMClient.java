/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
 *
 */
package uk.ac.manchester.tornado.runtime.common;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

public class TornadoVMClient {
    private String host;
    private int port;

    public TornadoVMClient() {
        this.host = TornadoOptions.PROF_PORT.split(":")[0];
        this.port = Integer.parseInt(TornadoOptions.PROF_PORT.split(":")[1]);

        if (!isValidInet4Address(host) || !TornadoOptions.PROF_PORT.isEmpty()) {
            throw new TornadoRuntimeException("Invalid IP address. \n Check the argument passed with -Dtornado.send.logs=IP:PORT");
        }
    }

    public void sentLogOverSocket(String outputFile) throws IOException {
        Socket socket;

        socket = new Socket(host, port);

        try (OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
            out.write(outputFile);
        } catch (IOException e) {
            System.out.println(e.toString());
        }
    }

    public static boolean isValidInet4Address(String ip) {
        String[] groups = ip.split("\\.");

        if (groups.length != 4)
            return false;

        try {
            return Arrays.stream(groups).filter(s -> s.length() > 1 && s.startsWith("0")).map(Integer::parseInt).filter(i -> (i >= 0 && i <= 255)).count() == 4;

        } catch (NumberFormatException e) {
            return false;
        }
    }

}
