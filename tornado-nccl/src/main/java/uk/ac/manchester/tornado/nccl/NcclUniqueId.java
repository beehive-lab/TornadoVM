/*
 * Copyright (c) 2026, APT Group, Department of Computer Science,
 * The University of Manchester.
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
package uk.ac.manchester.tornado.nccl;

import java.util.Arrays;
import java.util.Base64;

import uk.ac.manchester.tornado.api.exceptions.TornadoRuntimeException;
import uk.ac.manchester.tornado.nccl.provider.NcclNativeLib;

/**
 * The NCCL unique id that ties together the processes of one communicator. One process creates it
 * with {@link #create()}, the application hands its bytes to every other process (over a socket,
 * MPI, a shared file, an environment variable...), and every process passes it to
 * {@link NcclCommunicator#create(NcclUniqueId, int, int, uk.ac.manchester.tornado.api.common.TornadoDevice...)}.
 *
 * <p>
 * Creating the id starts NCCL's bootstrap listener in the creating process, and the id carries its
 * address: that process must keep running until every rank has joined the communicator, which is
 * natural when it is one of the ranks.
 * </p>
 *
 * <pre>{@code
 * // process of rank 0
 * NcclUniqueId id = NcclUniqueId.create();
 * send(id.toBase64());                       // however the application reaches the other processes
 *
 * // every process
 * NcclUniqueId id = NcclUniqueId.fromBase64(received);
 * NcclCommunicator comm = NcclCommunicator.create(id, worldSize, myRank, myGpu);
 * }</pre>
 */
public final class NcclUniqueId {

    private final byte[] bytes;

    private NcclUniqueId(byte[] bytes) {
        this.bytes = bytes;
    }

    /** A new unique id ({@code ncclGetUniqueId}); create it in one process only. */
    public static NcclUniqueId create() {
        return new NcclUniqueId(NcclNativeLib.getUniqueId());
    }

    /** The id from the bytes another process obtained with {@link #toBytes()}. */
    public static NcclUniqueId fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != NcclNativeLib.UNIQUE_ID_BYTES) {
            throw new TornadoRuntimeException("[ERROR] An NCCL unique id has " + NcclNativeLib.UNIQUE_ID_BYTES + " bytes, got " + (bytes == null ? "none" : bytes.length));
        }
        return new NcclUniqueId(bytes.clone());
    }

    /** The id from the text another process obtained with {@link #toBase64()}. */
    public static NcclUniqueId fromBase64(String text) {
        return fromBytes(Base64.getDecoder().decode(text));
    }

    /** The {@value NcclNativeLib#UNIQUE_ID_BYTES} bytes of the id, to send to the other processes. */
    public byte[] toBytes() {
        return bytes.clone();
    }

    /** The id as Base64 text, convenient for command lines and environment variables. */
    public String toBase64() {
        return Base64.getEncoder().encodeToString(bytes);
    }

    byte[] bytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NcclUniqueId id && Arrays.equals(bytes, id.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "NcclUniqueId[" + toBase64().substring(0, 12) + "...]";
    }
}
