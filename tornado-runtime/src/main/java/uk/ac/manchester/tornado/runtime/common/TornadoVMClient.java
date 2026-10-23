/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
