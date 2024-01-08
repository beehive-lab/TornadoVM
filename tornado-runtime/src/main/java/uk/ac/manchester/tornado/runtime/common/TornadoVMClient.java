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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package uk.ac.manchester.tornado.runtime.common;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;

public class TornadoVMClient {
    private String host;
    private int port;

    public TornadoVMClient() {
        if (validArgument()) {
            this.host = TornadoOptions.SOCKET_PORT.split(":")[0];
            this.port = Integer.parseInt(TornadoOptions.SOCKET_PORT.split(":")[1]);
        }

        if (!isValidInet4Address(host) || TornadoOptions.SOCKET_PORT.isEmpty() || !validArgument()) {
            throw new TornadoRuntimeException("Invalid IP address. \nCheck the argument passed with -Dtornado.send.logs=IP:PORT");
        }
    }

    private boolean validArgument() {
        return TornadoOptions.SOCKET_PORT.split(":").length == 2;
    }

    public void sentLogOverSocket(String outputFile) throws IOException {
        Socket socket = openSocket();

        if (socket.isConnected()) {
            try (OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)) {
                out.write(outputFile);
            } catch (IOException e) {
                System.out.println(e.toString());
            }
        }
        socket.close();
    }

    private Socket openSocket() {
        Socket socket = null;

        try {
            socket = new Socket(host, port);
        } catch (UnknownHostException u) {
            System.out.println(u);
        } catch (IOException i) {
            System.out.println(i);
        }

        return socket;
    }

    public static boolean isValidInet4Address(String ip) {
        try {
            if (ip == null || ip.isEmpty()) {
                return false;
            }

            String[] parts = ip.split("\\.");
            if (parts.length != 4) {
                return false;
            }

            for (String s : parts) {
                int i = Integer.parseInt(s);
                if ((i < 0) || (i > 255)) {
                    return false;
                }
            }
            if (ip.endsWith(".")) {
                return false;
            }

            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

}
