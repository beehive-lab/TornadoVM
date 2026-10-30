/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2026, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.runtime.autotune;

import java.util.Arrays;
import java.util.Objects;

import uk.ac.manchester.tornado.api.common.TornadoDevice;

/**
 * The outcome of tuning one task: the winning local work size ({@code null} if the backend's default
 * scheduler won), the median timing of that candidate in nanoseconds, and the context it was measured in
 * (task, device, kernel signature hash and global work size). Instances are immutable; the two array
 * fields are copied on construction and on access.
 */
public final class AutoTuneResult {

    private final String taskId;
    private final TornadoDevice device;
    private final long kernelSignatureHash;
    private final long[] globalWorkSize;
    private final int[] winningLocalWork;
    private final long medianTimeNanos;

     /**
     * @throws NullPointerException
     *     if {@code taskId}, {@code device} or {@code globalWorkSize} is {@code null}.
     * @throws IllegalArgumentException
     *     if {@code medianTimeNanos} is negative.
     */
    public AutoTuneResult(String taskId, TornadoDevice device, long kernelSignatureHash, long[] globalWorkSize, int[] winningLocalWork, long medianTimeNanos) {
        if (taskId == null) {
            throw new NullPointerException("taskId must not be null");
        }
        if (device == null) {
            throw new NullPointerException("device must not be null");
        }
        if (globalWorkSize == null) {
            throw new NullPointerException("globalWorkSize must not be null");
        }
        if (medianTimeNanos < 0) {
            throw new IllegalArgumentException("medianTimeNanos must be >= 0");
        }
        this.taskId = taskId;
        this.device = device;
        this.kernelSignatureHash = kernelSignatureHash;
        this.globalWorkSize = globalWorkSize.clone();
        this.winningLocalWork = (winningLocalWork == null) ? null : winningLocalWork.clone();
        this.medianTimeNanos = medianTimeNanos;
    }

    public String getTaskId() {
        return this.taskId;
    }

    public TornadoDevice getDevice() {
        return this.device;
    }

    public long getKernelSignatureHash() {
        return this.kernelSignatureHash;
    }

    public long[] getGlobalWorkSize() {
        return this.globalWorkSize.clone();
    }

    public int[] getWinningLocalWork() {
        return (this.winningLocalWork == null) ? null : this.winningLocalWork.clone();
    }

    public long getMedianTimeNanos() {
        return this.medianTimeNanos;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AutoTuneResult other)) {
            return false;
        }
        return kernelSignatureHash == other.kernelSignatureHash
                && medianTimeNanos == other.medianTimeNanos
                && taskId.equals(other.taskId)
                && device.equals(other.device)
                && Arrays.equals(globalWorkSize, other.globalWorkSize)
                && Arrays.equals(winningLocalWork, other.winningLocalWork);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(taskId, device, kernelSignatureHash, medianTimeNanos);
        result = 31 * result + Arrays.hashCode(globalWorkSize);
        result = 31 * result + Arrays.hashCode(winningLocalWork);
        return result;
    }

    @Override
    public String toString() {
        return "AutoTuneResult{" + "taskId='" + taskId + '\'' + ", device=" + device + ", kernelSignatureHash=" + kernelSignatureHash + ", globalWorkSize=" + Arrays.toString(globalWorkSize)
                + ", winningLocalWork=" + Arrays.toString(winningLocalWork) + ", medianTimeNanos=" + medianTimeNanos + '}';
    }
}