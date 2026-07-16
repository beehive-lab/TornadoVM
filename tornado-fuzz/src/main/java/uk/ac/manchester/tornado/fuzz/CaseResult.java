/*
 * Copyright (c) 2024, APT Group, Department of Computer Science,
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
package uk.ac.manchester.tornado.fuzz;

import uk.ac.manchester.tornado.fuzz.oracle.Diff;

/**
 * Outcome of running one fuzz case: the classification plus everything the
 * report layer needs to write a debug bundle.
 */
public final class CaseResult {

    public enum Status {
        PASS,
        MISMATCH,
        EXCEPTION
    }

    public final Status status;
    public final Diff diff;
    public final Throwable error;
    /** Human-readable kernel description / source snippet. */
    public final String kernelText;
    /** Fully-formed standalone JUnit reproducer source, or null when not emittable. */
    public final ReproSpec repro;

    private CaseResult(Status status, Diff diff, Throwable error, String kernelText, ReproSpec repro) {
        this.status = status;
        this.diff = diff;
        this.error = error;
        this.kernelText = kernelText;
        this.repro = repro;
    }

    public static CaseResult pass(String kernelText) {
        return new CaseResult(Status.PASS, null, null, kernelText, null);
    }

    public static CaseResult mismatch(Diff diff, String kernelText, ReproSpec repro) {
        return new CaseResult(Status.MISMATCH, diff, null, kernelText, repro);
    }

    public static CaseResult exception(Throwable error, String kernelText, ReproSpec repro) {
        return new CaseResult(Status.EXCEPTION, null, error, kernelText, repro);
    }
}
