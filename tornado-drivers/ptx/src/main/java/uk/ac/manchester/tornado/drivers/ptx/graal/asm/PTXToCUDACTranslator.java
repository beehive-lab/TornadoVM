/*
 * This file is part of Tornado: A heterogeneous programming framework:
 * https://github.com/beehive-lab/tornadovm
 *
 * Copyright (c) 2024, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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
package uk.ac.manchester.tornado.drivers.ptx.graal.asm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates PTX assembly source to CUDA C source for NVRTC compilation.
 *
 * <p>Translation covers: kernel/device function signatures, variable declarations,
 * load/store operations, arithmetic, control flow, math intrinsics, atomics,
 * and built-in registers.
 */
public class PTXToCUDACTranslator {

    // PTX type -> C type
    private static final Map<String, String> PTX_TO_C_TYPE = new HashMap<>();
    static {
        PTX_TO_C_TYPE.put("s8",   "char");
        PTX_TO_C_TYPE.put("s16",  "short");
        PTX_TO_C_TYPE.put("s32",  "int");
        PTX_TO_C_TYPE.put("s64",  "long long");
        PTX_TO_C_TYPE.put("u8",   "unsigned char");
        PTX_TO_C_TYPE.put("u16",  "unsigned short");
        PTX_TO_C_TYPE.put("u32",  "unsigned int");
        PTX_TO_C_TYPE.put("u64",  "unsigned long long");
        PTX_TO_C_TYPE.put("f16",  "half");
        PTX_TO_C_TYPE.put("f32",  "float");
        PTX_TO_C_TYPE.put("f64",  "double");
        PTX_TO_C_TYPE.put("b8",   "unsigned char");
        PTX_TO_C_TYPE.put("b16",  "unsigned short");
        PTX_TO_C_TYPE.put("b32",  "unsigned int");
        PTX_TO_C_TYPE.put("b64",  "unsigned long long");
        PTX_TO_C_TYPE.put("pred", "int");
    }

    // PTX built-in register substitutions
    private static final Map<String, String> BUILTIN_REGS = new HashMap<>();
    static {
        BUILTIN_REGS.put("%tid.x",    "threadIdx.x");
        BUILTIN_REGS.put("%tid.y",    "threadIdx.y");
        BUILTIN_REGS.put("%tid.z",    "threadIdx.z");
        BUILTIN_REGS.put("%ctaid.x",  "blockIdx.x");
        BUILTIN_REGS.put("%ctaid.y",  "blockIdx.y");
        BUILTIN_REGS.put("%ctaid.z",  "blockIdx.z");
        BUILTIN_REGS.put("%ntid.x",   "blockDim.x");
        BUILTIN_REGS.put("%ntid.y",   "blockDim.y");
        BUILTIN_REGS.put("%ntid.z",   "blockDim.z");
        BUILTIN_REGS.put("%nctaid.x", "gridDim.x");
        BUILTIN_REGS.put("%nctaid.y", "gridDim.y");
        BUILTIN_REGS.put("%nctaid.z", "gridDim.z");
        BUILTIN_REGS.put("%laneid",   "(__lane_id())");
        BUILTIN_REGS.put("%warpid",   "(__warp_id())");
        BUILTIN_REGS.put("%nwarpid",  "(blockDim.x / 32)");
    }

    // Shared memory array effective type map: populated by pre-scan in translate().
    // Maps array name -> effective C type based on how load/store instructions access it.
    // When an array declared as .s32 is always accessed as f32, we declare it as float
    // so NVRTC sees properly-typed shared memory and can apply direct-access optimizations.
    private static final ThreadLocal<Map<String, String>> SHARED_EFFECTIVE_TYPE =
            ThreadLocal.withInitial(HashMap::new);

    // Patterns
    private static final Pattern PTX_HEADER_PAT = Pattern.compile("^\\s*\\.(version|target|address_size)\\b.*");
    private static final Pattern VISIBLE_ENTRY_PAT = Pattern.compile("^\\.visible\\s+\\.entry\\s+(\\S+)\\s*\\((.*)");
    private static final Pattern FUNC_PAT = Pattern.compile("^\\.(func)\\s+(.*)");
    private static final Pattern REG_RANGE_PAT = Pattern.compile("^\\s*\\.reg\\s+\\.(\\w+)\\s+(\\w+)<(\\d+)>;\\s*$");
    private static final Pattern REG_SINGLE_PAT = Pattern.compile("^\\s*\\.reg\\s+\\.(\\w+)\\s+(\\S+);\\s*$");
    private static final Pattern LOCAL_ARR_PAT = Pattern.compile("^\\s*\\.local\\s+\\.(\\w+)\\s+(\\w+)\\[(\\d+)\\];\\s*$");
    private static final Pattern SHARED_ARR_PAT = Pattern.compile("^\\s*\\.shared\\s+\\.(\\w+)\\s+(\\w+)(?:\\[(\\d+)\\])?;\\s*$");
    private static final Pattern LABEL_PAT = Pattern.compile("^(\\w+):\\s*$");
    private static final Pattern FLOAT_HEX_PAT = Pattern.compile("0[Ff]([0-9A-Fa-f]{8})");
    private static final Pattern DOUBLE_HEX_PAT = Pattern.compile("0[Dd]([0-9A-Fa-f]{16})");

    /**
     * Translate a PTX source string to CUDA C source.
     *
     * @param ptx The full PTX source (possibly one function or multiple functions
     *            assembled together).
     * @return CUDA C source suitable for NVRTC compilation.
     */
    public static String translate(String ptx) {
        String[] lines = ptx.split("\n", -1);
        StringBuilder out = new StringBuilder();

        // Pre-scan: build a map of shared array name -> effective access type.
        // PTX allows declaring .shared .s32 arr[N] then accessing it with ld/st.shared.f32.
        // If all accesses to an array use the same type (possibly different from the declaration),
        // we declare the CUDA C array with the access type so NVRTC sees direct-typed access.
        prescaneSharedTypes(lines);

        // State for multi-line param list parsing
        boolean inParamList = false;
        boolean isKernelEntry = false;
        String pendingFuncName = null;
        String pendingReturnType = null; // null = void
        List<String> paramLines = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();

            // Skip PTX file header directives
            if (PTX_HEADER_PAT.matcher(line).matches()) {
                continue;
            }
            // Skip blank lines and pure comment lines
            if (line.isEmpty() || line.startsWith("//")) {
                out.append("\n");
                continue;
            }
            // Skip .pragma, .maxnreg, .minnctapersm etc.
            if (line.startsWith(".pragma") || line.startsWith(".maxnreg")
                    || line.startsWith(".minnctapersm") || line.startsWith(".maxntid")) {
                continue;
            }

            // --- Multi-line param list accumulation ---
            if (inParamList) {
                paramLines.add(line);
                if (line.contains(")")) {
                    // End of parameter list
                    String combined = String.join(" ", paramLines);
                    String afterParen = combined.substring(combined.indexOf(')') + 1).trim();
                    boolean openBrace = afterParen.startsWith("{");

                    String cudaParams = translateParamList(combined.substring(0, combined.indexOf(')')), isKernelEntry);
                    emitFunctionSignature(out, isKernelEntry, pendingFuncName, pendingReturnType, cudaParams);
                    if (openBrace) {
                        out.append(" {\n");
                    } else {
                        out.append("\n");
                    }
                    inParamList = false;
                    pendingFuncName = null;
                    pendingReturnType = null;
                    paramLines.clear();
                }
                continue;
            }

            // --- Kernel entry (.visible .entry) ---
            Matcher m = VISIBLE_ENTRY_PAT.matcher(line);
            if (m.matches()) {
                String name = m.group(1);
                String rest = m.group(2).trim();
                if (rest.contains(")")) {
                    String params = rest.substring(0, rest.lastIndexOf(')'));
                    String after = rest.substring(rest.lastIndexOf(')') + 1).trim();
                    String cudaParams = translateParamList(params, true);
                    out.append("extern \"C\" __global__ void ").append(name).append("(").append(cudaParams).append(")");
                    if (after.startsWith("{")) {
                        out.append(" {\n");
                    } else {
                        out.append("\n");
                    }
                } else {
                    // Multi-line parameters
                    inParamList = true;
                    isKernelEntry = true;
                    pendingFuncName = name;
                    pendingReturnType = null;
                    paramLines.clear();
                    paramLines.add(rest);
                }
                continue;
            }

            // --- Device function (.func) ---
            Matcher mf = FUNC_PAT.matcher(line);
            if (mf.matches()) {
                String rest = mf.group(2).trim();
                String returnType = null;
                String funcName;
                String paramsPart;

                // Check for return value: (.reg .TYPE retVar) or (.param .align N .bN retVar[N])
                if (rest.startsWith("(")) {
                    int closeParenIdx = rest.indexOf(')');
                    String retDecl = rest.substring(1, closeParenIdx).trim();
                    returnType = extractFuncReturnType(retDecl);
                    rest = rest.substring(closeParenIdx + 1).trim();
                }

                int parenIdx = rest.indexOf('(');
                if (parenIdx < 0) {
                    // No params yet in this line
                    funcName = rest.trim();
                    inParamList = true;
                    isKernelEntry = false;
                    pendingFuncName = funcName;
                    pendingReturnType = returnType;
                    paramLines.clear();
                    continue;
                }

                funcName = rest.substring(0, parenIdx).trim();
                String afterParen = rest.substring(parenIdx + 1);

                if (afterParen.contains(")")) {
                    int closeIdx = afterParen.lastIndexOf(')');
                    paramsPart = afterParen.substring(0, closeIdx);
                    String after = afterParen.substring(closeIdx + 1).trim();
                    String cudaParams = translateParamList(paramsPart, false);
                    String ret = (returnType != null) ? returnType : "void";
                    out.append("__device__ ").append(ret).append(" ").append(funcName).append("(").append(cudaParams).append(")");
                    if (after.startsWith("{")) {
                        out.append(" {\n");
                    } else {
                        out.append("\n");
                    }
                } else {
                    inParamList = true;
                    isKernelEntry = false;
                    pendingFuncName = funcName;
                    pendingReturnType = returnType;
                    paramLines.clear();
                    paramLines.add(afterParen);
                }
                continue;
            }

            // --- Variable declarations ---
            Matcher rrm = REG_RANGE_PAT.matcher(line);
            if (rrm.matches()) {
                String ptxType = rrm.group(1);
                String prefix = rrm.group(2);
                int count = Integer.parseInt(rrm.group(3));
                String ctype = toCType(ptxType);
                out.append("\t").append(ctype).append(" ");
                for (int j = 0; j < count; j++) {
                    if (j > 0) out.append(", ");
                    out.append(prefix).append(j);
                }
                out.append(";\n");
                continue;
            }

            Matcher rsm = REG_SINGLE_PAT.matcher(line);
            if (rsm.matches()) {
                String ptxType = rsm.group(1);
                String varName = rsm.group(2);
                String ctype = toCType(ptxType);
                out.append("\t").append(ctype).append(" ").append(varName).append(";\n");
                continue;
            }

            Matcher lam = LOCAL_ARR_PAT.matcher(line);
            if (lam.matches()) {
                String ptxType = lam.group(1);
                String arrName = lam.group(2);
                String size = lam.group(3);
                String ctype = toCType(ptxType);
                out.append("\t").append(ctype).append(" ").append(arrName).append("[").append(size).append("];\n");
                continue;
            }

            Matcher sam = SHARED_ARR_PAT.matcher(line);
            if (sam.matches()) {
                String ptxType = sam.group(1);
                String arrName = sam.group(2);
                String size = sam.group(3);
                // Use the effective type inferred from access patterns if available.
                // This lets NVRTC see directly-typed shared memory (e.g. float instead of int)
                // and optimize accesses without pointer-cast overhead.
                String inferredType = SHARED_EFFECTIVE_TYPE.get().get(arrName);
                String ctype = (inferredType != null) ? inferredType : toCType(ptxType);
                if (size != null) {
                    out.append("\t__shared__ ").append(ctype).append(" ").append(arrName).append("[").append(size).append("];\n");
                } else {
                    out.append("\t__shared__ ").append(ctype).append(" ").append(arrName).append(";\n");
                }
                continue;
            }

            // --- Closing brace ---
            if (line.equals("}")) {
                out.append("}\n");
                continue;
            }

            // --- Labels ---
            Matcher lbl = LABEL_PAT.matcher(line);
            if (lbl.matches()) {
                out.append(lbl.group(1)).append(":\n");
                continue;
            }

            // --- Instructions (tab-separated opcode and operands) ---
            String translated = translateInstruction(line);
            if (translated != null) {
                out.append("\t").append(translated).append("\n");
            }
            // else: unrecognized line, skip
        }

        SHARED_EFFECTIVE_TYPE.get().clear();

        String result = out.toString();
        // Convert goto-based loops to while(true) loops so NVRTC can apply
        // loop-level optimisations (unrolling, vectorisation, instruction scheduling).
        result = convertGotoLoops(result);
        // NVRTC does not provide the `half` type by default — include the FP16 header
        // if the output declares any half-precision variables.
        if (result.contains("\thalf ") || result.contains(" half ")) {
            result = "#include <cuda_fp16.h>\n" + result;
        }
        return result;
    }

    /**
     * Pre-scan PTX lines for ld.shared.TYPE and st.shared.TYPE accesses to discover
     * the effective C type that each shared array is accessed with. Stores results in
     * SHARED_EFFECTIVE_TYPE.
     *
     * <p>If an array is accessed with a single consistent type, we declare it with that
     * type in CUDA C so NVRTC can see properly-typed shared memory and optimize direct
     * array element accesses without pointer casts.
     */
    private static void prescaneSharedTypes(String[] lines) {
        Map<String, String> effectiveTypes = SHARED_EFFECTIVE_TYPE.get();
        effectiveTypes.clear();
        // Track arrays with conflicting access types so we revert to pointer-cast strategy
        Map<String, Boolean> hasConflict = new HashMap<>();

        // Pattern: (ld|st).shared[.volatile].TYPE  arrName[...
        // arrName is the identifier before '['
        Pattern sharedAccessPat = Pattern.compile(
                "(?:ld|ldu|st)\\.shared(?:\\.volatile)?\\.(\\w+)\\s+(\\w+)\\[");
        for (String raw : lines) {
            String line = raw.trim();
            Matcher m = sharedAccessPat.matcher(line);
            if (m.find()) {
                String ptxType = m.group(1);
                String arrName = m.group(2);
                String ctype   = toCType(ptxType);
                if (hasConflict.containsKey(arrName)) {
                    continue; // already conflicted, skip
                }
                String existing = effectiveTypes.get(arrName);
                if (existing == null) {
                    effectiveTypes.put(arrName, ctype);
                } else if (!existing.equals(ctype)) {
                    // Conflicting access types — fall back to pointer-cast for this array
                    effectiveTypes.remove(arrName);
                    hasConflict.put(arrName, Boolean.TRUE);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Goto-to-while loop transformation
    // -------------------------------------------------------------------------

    /**
     * Converts goto-based loops in generated CUDA C to structured {@code while(COND)} loops.
     *
     * <p>TornadoVM PTX compilation produces control flow of the form:
     * <pre>
     *   LOOP_HEADER:
     *       rpb = (i &lt; N);
     *       if (!rpb) goto EXIT;
     *       ... body ...
     *       goto LOOP_HEADER;
     *   EXIT:
     * </pre>
     *
     * <p>This method rewrites such patterns to:
     * <pre>
     *   while (i &lt; N) {
     *       ... body ...
     *   }
     *   EXIT:
     * </pre>
     *
     * <p>by lifting the predicate-assignment expression directly into the {@code while()}
     * condition, eliminating both the predicate variable and the inner break.  This matches
     * the structured loop form that OpenCL kernels use natively and gives NVRTC full loop
     * context for unrolling, vectorisation, and instruction scheduling.
     *
     * <p>Falls back to {@code while(true){...if(!p) break;...}} when the loop header has
     * multi-line condition computation, or the predicate variable is used inside the body.
     *
     * <p>The transformation is conservative: only single-entry, single-exit loops where
     * the back-edge is an unconditional {@code goto HEADER;} are rewritten.
     */
    private static String convertGotoLoops(String cudaC) {
        String[] lines = cudaC.split("\n", -1);
        int n = lines.length;

        // Identify label positions: label name -> line index
        Map<String, Integer> labelLine = new HashMap<>();
        Pattern labelPat = Pattern.compile("^(\\w+):\\s*$");
        for (int i = 0; i < n; i++) {
            String t = lines[i].trim();
            Matcher m = labelPat.matcher(t);
            if (m.matches()) {
                labelLine.put(m.group(1), i);
            }
        }

        // For each label, attempt to detect loop pattern:
        //   headerIdx: HEADER_LABEL:
        //   (optionally one line computing condition)
        //   exitCheckIdx: if (!pred) goto EXIT_LABEL;   [or  if (pred) goto EXIT_LABEL;]
        //   ... body ...
        //   backEdgeIdx: goto HEADER_LABEL;
        //   exitIdx: EXIT_LABEL:
        //
        // We scan forward from each label looking for this shape.
        Pattern exitCheckPat = Pattern.compile("^\\s*if\\s*\\((!?)(\\S+)\\)\\s*goto\\s+(\\w+);\\s*$");
        Pattern backEdgePat  = Pattern.compile("^\\s*goto\\s+(\\w+);\\s*$");

        // Collect rewrites as (startLine, endLine) to apply bottom-up so indices stay valid.
        List<int[]> rewrites = new ArrayList<>(); // [headerIdx, exitLabelIdx]
        List<String[]> replacements = new ArrayList<>(); // parallel list of replacement strings

        for (Map.Entry<String, Integer> entry : labelLine.entrySet()) {
            String headerLabel = entry.getKey();
            int headerIdx = entry.getValue();

            // Find the first conditional exit-check after the header
            int exitCheckIdx = -1;
            String exitLabel = null;
            boolean negated = false;
            String condVar = null;
            for (int i = headerIdx + 1; i < n; i++) {
                String t = lines[i].trim();
                if (t.isEmpty()) continue;
                // Stop if we hit another label (we've left the header region)
                if (labelPat.matcher(t).matches()) break;
                Matcher m = exitCheckPat.matcher(t);
                if (m.matches()) {
                    negated      = "!".equals(m.group(1));
                    condVar      = m.group(2);
                    exitLabel    = m.group(3);
                    exitCheckIdx = i;
                    break;
                }
            }
            if (exitCheckIdx < 0 || exitLabel == null) continue;
            // Exit label must exist and come after the header
            Integer exitLineNum = labelLine.get(exitLabel);
            if (exitLineNum == null || exitLineNum <= headerIdx) continue;

            // Find the unconditional back-edge goto HEADER just before the exit label
            int backEdgeIdx = -1;
            for (int i = exitLineNum - 1; i > exitCheckIdx; i--) {
                String t = lines[i].trim();
                if (t.isEmpty()) continue;
                Matcher m = backEdgePat.matcher(t);
                if (m.matches() && headerLabel.equals(m.group(1))) {
                    backEdgeIdx = i;
                    break;
                }
                // Any non-empty, non-label line between back-edge and exit label breaks the pattern
                if (!labelPat.matcher(t).matches()) break;
            }
            if (backEdgeIdx < 0) continue;

            // Check that no line in the body (exitCheckIdx+1 .. backEdgeIdx-1) gotos OUTSIDE the loop.
            // Gotos to the header itself are continue-equivalents and are fine.
            boolean bodyClean = true;
            for (int i = exitCheckIdx + 1; i < backEdgeIdx; i++) {
                String t = lines[i].trim();
                Matcher bm = backEdgePat.matcher(t);
                if (bm.matches()) {
                    String target = bm.group(1);
                    Integer tLine = labelLine.get(target);
                    if (tLine != null && tLine < headerIdx) {
                        bodyClean = false; break; // escapes upward
                    }
                    if (tLine != null && tLine > exitLineNum) {
                        bodyClean = false; break; // jumps past exit
                    }
                }
                Matcher ecm = exitCheckPat.matcher(t);
                if (ecm.matches()) {
                    String target = ecm.group(3);
                    Integer tLine = labelLine.get(target);
                    if (tLine != null && (tLine < headerIdx || tLine > exitLineNum)) {
                        bodyClean = false; break;
                    }
                    if (!exitLabel.equals(target)) {
                        bodyClean = false; break; // multiple distinct exits
                    }
                }
            }
            if (!bodyClean) continue;

            // Check for overlapping rewrites (conservative: skip if any existing rewrite overlaps)
            boolean overlaps = false;
            for (int[] rw : rewrites) {
                if (rw[0] < exitLineNum && rw[1] > headerIdx) {
                    overlaps = true; break;
                }
            }
            if (overlaps) continue;

            // --- Try to extract a direct while condition ---
            //
            // Look for a SINGLE assignment to condVar in the header block
            // (lines headerIdx+1 .. exitCheckIdx-1):
            //   condVar = (expr);   or   condVar = expr;
            // If exactly one such line exists and condVar is not read in the loop body,
            // lift the expression into while(EXPR) and drop the predicate variable entirely.
            String directCond = null;
            int condAssignIdx = -1;

            Pattern condAssignPat = Pattern.compile(
                    "^\\s*" + Pattern.quote(condVar) + "\\s*=\\s*(.*?)\\s*;\\s*$");
            int nonEmptyHeaderLines = 0;
            for (int i = headerIdx + 1; i < exitCheckIdx; i++) {
                String t = lines[i].trim();
                if (t.isEmpty()) continue;
                nonEmptyHeaderLines++;
                Matcher m = condAssignPat.matcher(lines[i]);
                if (m.matches()) {
                    if (condAssignIdx >= 0) {
                        // Multiple assignments to condVar — can't lift
                        condAssignIdx = -1;
                        break;
                    }
                    condAssignIdx = i;
                    String rhs = m.group(1).trim();
                    // Strip outer parentheses if they are properly matched
                    if (rhs.startsWith("(") && rhs.endsWith(")") && hasMatchedOuterParens(rhs)) {
                        rhs = rhs.substring(1, rhs.length() - 1).trim();
                    }
                    // If the exit check negates the pred (if (!p) break), the while runs while
                    // the expression is true. If not negated (if (p) break), we need !(expr).
                    directCond = negated ? rhs : "!(" + rhs + ")";
                }
            }

            // Only use direct condition when the header block has exactly this one assignment
            // (no other side-effecting lines that must be preserved).
            if (condAssignIdx >= 0 && nonEmptyHeaderLines > 1) {
                directCond = null; // other header lines need to stay — fall back
            }

            // Verify condVar is not read in the loop body (between exitCheck and backEdge)
            if (directCond != null) {
                Pattern usePat = Pattern.compile("\\b" + Pattern.quote(condVar) + "\\b");
                for (int i = exitCheckIdx + 1; i < backEdgeIdx; i++) {
                    if (usePat.matcher(lines[i]).find()) {
                        directCond = null;
                        break;
                    }
                }
            }

            // --- Build replacement ---
            rewrites.add(new int[]{headerIdx, exitLineNum});
            StringBuilder sb = new StringBuilder();
            // Indentation from header line
            String indent = leadingWhitespace(lines[headerIdx]);

            if (directCond != null) {
                // Structured while(COND) — matches OpenCL output style
                sb.append(indent).append("while (").append(directCond).append(") {\n");
                // Skip condition setup (condAssignIdx) and exit-check; emit body directly
                for (int i = exitCheckIdx + 1; i < backEdgeIdx; i++) {
                    String t = lines[i];
                    Matcher bm = backEdgePat.matcher(t.trim());
                    if (bm.matches() && headerLabel.equals(bm.group(1))) {
                        sb.append(indent).append("\tcontinue;\n");
                    } else {
                        sb.append(t).append("\n");
                    }
                }
            } else {
                // Fall back: while(true) with explicit break
                sb.append(indent).append("while (true) { // ").append(headerLabel).append("\n");
                // Keep condition setup lines
                for (int i = headerIdx + 1; i < exitCheckIdx; i++) {
                    sb.append(lines[i]).append("\n");
                }
                // Replace exit-check with break (preserving the same condition)
                sb.append(indent).append("\tif (").append(negated ? "!" : "").append(condVar)
                  .append(") break;\n");
                // Body lines
                for (int i = exitCheckIdx + 1; i < backEdgeIdx; i++) {
                    String t = lines[i];
                    Matcher bm = backEdgePat.matcher(t.trim());
                    if (bm.matches() && headerLabel.equals(bm.group(1))) {
                        sb.append(indent).append("\tcontinue;\n");
                    } else {
                        sb.append(t).append("\n");
                    }
                }
            }
            // Close while loop
            sb.append(indent).append("}\n");
            // Keep the exit label (other code may reference it)
            sb.append(lines[exitLineNum]).append("\n");

            replacements.add(new String[]{sb.toString()});
        }

        if (rewrites.isEmpty()) return cudaC;

        // Apply rewrites bottom-up (largest start index first) to preserve line indices
        // Sort by start index descending
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < rewrites.size(); i++) order.add(i);
        order.sort((a, b) -> rewrites.get(b)[0] - rewrites.get(a)[0]);

        List<String> resultLines = new ArrayList<>(Arrays.asList(lines));
        for (int idx : order) {
            int[] rw = rewrites.get(idx);
            int startLine = rw[0];
            int endLine   = rw[1]; // inclusive
            String replacement = replacements.get(idx)[0];
            // Remove lines [startLine .. endLine] and insert replacement
            for (int i = endLine; i >= startLine; i--) {
                resultLines.remove(i);
            }
            // Split replacement into lines and insert at startLine
            String[] replLines = replacement.split("\n", -1);
            for (int i = replLines.length - 1; i >= 0; i--) {
                resultLines.add(startLine, replLines[i]);
            }
        }

        StringBuilder finalOut = new StringBuilder();
        for (String l : resultLines) {
            finalOut.append(l).append("\n");
        }
        return finalOut.toString();
    }

    /** Returns true iff {@code s} is wrapped in a single matching pair of outer parentheses. */
    private static boolean hasMatchedOuterParens(String s) {
        if (s.length() < 2 || s.charAt(0) != '(' || s.charAt(s.length() - 1) != ')') return false;
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0 && i < s.length() - 1) return false; // outer paren closed early
            }
        }
        return depth == 0;
    }

    /** Returns the leading whitespace prefix of {@code line}. */
    private static String leadingWhitespace(String line) {
        StringBuilder ws = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') ws.append(c);
            else break;
        }
        return ws.toString();
    }

    // -------------------------------------------------------------------------
    // Instruction translation
    // -------------------------------------------------------------------------

    private static String translateInstruction(String line) {
        // Strip leading/trailing whitespace
        line = line.trim();
        if (line.isEmpty()) return null;

        // Conditional branch prefix: @[!]pred bra ...
        if (line.startsWith("@")) {
            return translateConditionalBranch(line);
        }

        // Split on first tab to get opcode and operands
        int tabIdx = line.indexOf('\t');
        String opcode;
        String operandsStr;
        if (tabIdx >= 0) {
            opcode = line.substring(0, tabIdx).trim();
            operandsStr = line.substring(tabIdx + 1).trim();
        } else {
            // Try space split
            int spaceIdx = line.indexOf(' ');
            if (spaceIdx < 0) {
                opcode = line;
                operandsStr = "";
            } else {
                opcode = line.substring(0, spaceIdx).trim();
                operandsStr = line.substring(spaceIdx + 1).trim();
            }
        }

        // Remove trailing semicolon from opcode (e.g. bare "ret;" has no operands)
        if (opcode.endsWith(";")) {
            opcode = opcode.substring(0, opcode.length() - 1).trim();
        }

        // Remove trailing semicolon from operands
        if (operandsStr.endsWith(";")) {
            operandsStr = operandsStr.substring(0, operandsStr.length() - 1).trim();
        }

        // Split opcode by dots to get base + type modifiers
        String[] opParts = opcode.split("\\.");
        String base = opParts[0];

        // Get all type/modifier components after the base
        // e.g. "add.s32" -> base="add", types=["s32"]
        // e.g. "ld.global.u64" -> base="ld", types=["global", "u64"]
        // e.g. "cvt.rni.s32.f32" -> base="cvt", types=["rni", "s32", "f32"]
        // e.g. "setp.lt.s32" -> base="setp", types=["lt", "s32"]

        String[] operands = splitOperands(operandsStr);

        switch (base) {
            case "ret":   return "return;";
            case "exit":  return "return;";
            case "bra":   return translateBranch(opParts, operands);
            case "mov":   return translateMov(opParts, operands);
            case "add":   return translateBinaryOp(opParts, operands, "+");
            case "sub":   return translateBinaryOp(opParts, operands, "-");
            case "mul":   return translateMul(opParts, operands);
            case "mad":   return translateMad(opParts, operands);
            case "fma":   return translateFma(opParts, operands);
            case "div":   return translateBinaryOp(opParts, operands, "/");
            case "rem":   return translateBinaryOp(opParts, operands, "%");
            case "neg":   return translateUnaryOp(opParts, operands, "-");
            case "abs":   return translateAbs(opParts, operands);
            case "not":   return translateNot(opParts, operands);
            case "and":   return translateAnd(opParts, operands);
            case "or":    return translateOr(opParts, operands);
            case "xor":   return translateBinaryOp(opParts, operands, "^");
            case "shl":   return translateBinaryOp(opParts, operands, "<<");
            case "shr":   return translateShr(opParts, operands);
            case "min":   return translateMinMax(opParts, operands, "min");
            case "max":   return translateMinMax(opParts, operands, "max");
            case "setp":  return translateSetp(opParts, operands);
            case "selp":  return translateSelp(opParts, operands);
            case "cvt":   return translateCvt(opParts, operands);
            case "ld":    return translateLoad(opParts, operands);
            case "st":    return translateStore(opParts, operands);
            case "ldu":   return translateLoad(opParts, operands);
            case "atom":  return translateAtom(opParts, operands, operandsStr);
            case "red":   return translateRed(opParts, operands, operandsStr);
            case "sqrt":  return translateSqrt(opParts, operands);
            case "rsqrt": return translateRsqrt(opParts, operands);
            case "sin":   return translateTrig(opParts, operands, "sinf", "sin");
            case "cos":   return translateTrig(opParts, operands, "cosf", "cos");
            case "ex2":   return translateEx2(opParts, operands);
            case "lg2":   return translateLg2(opParts, operands);
            case "tanh":  return translateTanh(opParts, operands);
            case "rcp":   return translateRcp(opParts, operands);
            case "call":  return translateCall(opParts, operands, operandsStr);
            case "bar":     return "__syncthreads();";
            case "barrier": return "__syncthreads();";
            case "membar":  return "__threadfence();";
            case "vote":  return translateVote(opParts, operands);
            case "shfl":  return translateShfl(opParts, operands, operandsStr);
            case "popc":  return translatePopc(opParts, operands);
            case "clz":   return translateClz(opParts, operands);
            case "brev":  return translateBrev(opParts, operands);
            case "dp4a":  return translateDp4a(opParts, operands);
            case "testp": return translateTestp(opParts, operands);
            case "wmma":  return null; // skip wmma for now
            case "nop":   return "/* nop */";
            default:      return "/* unsupported: " + line + " */";
        }
    }

    // -------------------------------------------------------------------------
    // Individual instruction translators
    // -------------------------------------------------------------------------

    private static String translateConditionalBranch(String line) {
        // @[!]pred bra TARGET;   — conditional branch
        Pattern braPat = Pattern.compile("^@(!?)(\\S+)\\s+bra(?:\\.uni)?\\s+(\\w+);?$");
        Matcher m = braPat.matcher(line);
        if (m.matches()) {
            String neg   = m.group(1);
            String pred  = substituteBuiltins(m.group(2));
            String label = m.group(3);
            return "!".equals(neg) ? "if (!" + pred + ") goto " + label + ";"
                                   : "if (" + pred + ") goto " + label + ";";
        }

        // @[!]pred non_branch_instruction  — predicated instruction
        // e.g. @rpb0 cvt.rzi.s32.f32 rsi3, rfi3;
        //      @!rpb0 mov.s32 rsi3, 0;
        Pattern predPat = Pattern.compile("^@(!?)(\\S+)\\s+(.+)$");
        Matcher pm = predPat.matcher(line);
        if (pm.matches()) {
            String neg   = pm.group(1);
            String pred  = substituteBuiltins(pm.group(2));
            String instr = pm.group(3);
            String translated = translateInstruction(instr);
            if (translated == null) return null;
            String cond = "!".equals(neg) ? "if (!" + pred + ")" : "if (" + pred + ")";
            return cond + " { " + translated + " }";
        }
        return "/* cond branch: " + line + " */";
    }

    private static String translateBranch(String[] opParts, String[] ops) {
        if (ops.length >= 1) {
            return "goto " + ops[0] + ";";
        }
        return "/* bra */";
    }

    private static String translateMov(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        return dest + " = " + src + ";";
    }

    private static String translateBinaryOp(String[] opParts, String[] ops, String op) {
        if (ops.length < 3) return null;
        String dest = subst(ops[0]);
        String a = subst(ops[1]);
        String b = subst(ops[2]);
        return dest + " = " + a + " " + op + " " + b + ";";
    }

    private static String translateMul(String[] opParts, String[] ops) {
        if (ops.length < 3) return null;
        String dest = subst(ops[0]);
        String a = subst(ops[1]);
        String b = subst(ops[2]);
        // mul.wide produces a wider result; treat as regular multiply
        return dest + " = " + a + " * " + b + ";";
    }

    private static String translateMad(String[] opParts, String[] ops) {
        // mad.rn.f32 / mad.rn.f64 — fused multiply-add with single rounding
        // Use fmaf/fma to match PTX semantics; unfused a*b+c has two roundings which
        // can shift quantisation boundaries by 1 ULP.
        if (ops.length < 4) return null;
        String dest = subst(ops[0]);
        String a = subst(ops[1]);
        String b = subst(ops[2]);
        String c = subst(ops[3]);
        String lastType = opParts.length > 1 ? opParts[opParts.length - 1] : "";
        if (lastType.equals("f64")) {
            return dest + " = fma(" + a + ", " + b + ", " + c + ");";
        }
        if (lastType.equals("f32")) {
            return dest + " = fmaf(" + a + ", " + b + ", " + c + ");";
        }
        // Integer mad — no fusion needed
        return dest + " = " + a + " * " + b + " + " + c + ";";
    }

    private static String translateFma(String[] opParts, String[] ops) {
        if (ops.length < 4) return null;
        String dest = subst(ops[0]);
        String a = subst(ops[1]);
        String b = subst(ops[2]);
        String c = subst(ops[3]);
        // Check if float or double
        boolean isF64 = opParts.length > 1 && opParts[opParts.length - 1].equals("f64");
        if (isF64) {
            return dest + " = fma(" + a + ", " + b + ", " + c + ");";
        }
        return dest + " = fmaf(" + a + ", " + b + ", " + c + ");";
    }

    private static String translateUnaryOp(String[] opParts, String[] ops, String op) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        return dest + " = " + op + src + ";";
    }

    private static String translateAbs(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "s32";
        if (type.startsWith("f")) {
            boolean isF64 = type.equals("f64");
            return dest + " = " + (isF64 ? "fabs(" : "fabsf(") + src + ");";
        }
        return dest + " = abs(" + src + ");";
    }

    private static String translateNot(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[1] : "b32";
        if (type.equals("pred")) {
            return dest + " = !" + src + ";";
        }
        return dest + " = ~" + src + ";";
    }

    private static String translateAnd(String[] opParts, String[] ops) {
        if (ops.length < 3) return null;
        String dest = subst(ops[0]);
        String a = subst(ops[1]);
        String b = subst(ops[2]);
        String type = opParts.length > 1 ? opParts[1] : "b32";
        if (type.equals("pred")) {
            return dest + " = " + a + " && " + b + ";";
        }
        return dest + " = " + a + " & " + b + ";";
    }

    private static String translateOr(String[] opParts, String[] ops) {
        if (ops.length < 3) return null;
        String dest = subst(ops[0]);
        String a = subst(ops[1]);
        String b = subst(ops[2]);
        String type = opParts.length > 1 ? opParts[1] : "b32";
        if (type.equals("pred")) {
            return dest + " = " + a + " || " + b + ";";
        }
        return dest + " = " + a + " | " + b + ";";
    }

    private static String translateShr(String[] opParts, String[] ops) {
        if (ops.length < 3) return null;
        String dest = subst(ops[0]);
        String a = subst(ops[1]);
        String b = subst(ops[2]);
        // For signed types, Java/C >> is arithmetic (sign-extending)
        // For unsigned, >>> is logical - in C >> on unsigned is also logical
        return dest + " = " + a + " >> " + b + ";";
    }

    private static String translateMinMax(String[] opParts, String[] ops, String func) {
        if (ops.length < 3) return null;
        String dest = subst(ops[0]);
        String a = subst(ops[1]);
        String b = subst(ops[2]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "s32";
        String cfunc;
        if (type.equals("f64")) {
            cfunc = func.equals("min") ? "fmin" : "fmax";
        } else if (type.startsWith("f")) {
            cfunc = func.equals("min") ? "fminf" : "fmaxf";
        } else {
            cfunc = func; // min/max work for integers in CUDA C
        }
        return dest + " = " + cfunc + "(" + a + ", " + b + ");";
    }

    private static String translateSetp(String[] opParts, String[] ops) {
        // setp.CMP.TYPE pred, a, b  or  setp.CMP.TYPE|p pred|q, a, b
        // For two-destination: setp.CMP.TYPE p|q, a, b
        if (ops.length < 3) return null;
        String cmpOp = opParts.length > 1 ? opParts[1] : "eq";
        String cmpStr = toCmpOp(cmpOp);
        String a = subst(ops[1]);
        String b = subst(ops[2]);

        String destStr = ops[0];
        // Check for two destinations (p|q)
        if (destStr.contains("|")) {
            String[] dests = destStr.split("\\|");
            String p = subst(dests[0].trim());
            String q = subst(dests[1].trim());
            return p + " = (" + a + " " + cmpStr + " " + b + "); " + q + " = !" + p + ";";
        }
        String dest = subst(destStr);
        return dest + " = (" + a + " " + cmpStr + " " + b + ");";
    }

    private static String translateSelp(String[] opParts, String[] ops) {
        // selp.TYPE dest, trueVal, falseVal, pred
        if (ops.length < 4) return null;
        String dest = subst(ops[0]);
        String tv = subst(ops[1]);
        String fv = subst(ops[2]);
        String pred = subst(ops[3]);
        return dest + " = " + pred + " ? " + tv + " : " + fv + ";";
    }

    private static String translateCvt(String[] opParts, String[] ops) {
        // cvt[.rnd][.sat].DTYPE.STYPE dest, src
        // opParts[-2] = DTYPE, opParts[-1] = STYPE
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String dtype = opParts.length > 2 ? opParts[opParts.length - 2] : "s32";
        String stype = opParts.length > 2 ? opParts[opParts.length - 1] : dtype;
        String ctype  = toCType(dtype);
        String sctype = toCType(stype);

        // Extract rounding-mode modifier if present (rn, rz, rzi, rmi, rpi, rni, rm, rp).
        String rnd = "";
        for (int i = 1; i < opParts.length - 2; i++) {
            String m = opParts[i];
            if (m.startsWith("rn") || m.startsWith("rz") || m.startsWith("rm")
                    || m.startsWith("rp") || m.equals("sat")) {
                rnd = m;
                break;
            }
        }

        // Float-to-float conversion with explicit rounding — map to CUDA rounding intrinsics.
        // e.g. cvt.rmi.f32.f32 → floorf(src);  cvt.rpi.f64.f64 → ceil(src)
        boolean sameFloatWidth = dtype.equals(stype) && (dtype.equals("f32") || dtype.equals("f64"));
        if (sameFloatWidth && !rnd.isEmpty()) {
            boolean isF64 = dtype.equals("f64");
            switch (rnd) {
                case "rmi": return dest + " = " + (isF64 ? "floor(" : "floorf(") + src + ");";
                case "rpi": return dest + " = " + (isF64 ? "ceil("  : "ceilf(")  + src + ");";
                case "rzi": return dest + " = " + (isF64 ? "trunc(" : "truncf(") + src + ");";
                case "rni": return dest + " = " + (isF64 ? "rint("  : "rintf(")  + src + ");";
                default:    break; // rn / rz / rm / rp — treat as round-to-nearest (no-op at same width)
            }
        }

        // Always cast via the source C-type first so that sign-extension semantics are
        // preserved.  For example, cvt.s64.s32 with a variable declared as unsigned int:
        //   (long long)(int)src   – sign-extends correctly
        //   (long long)src        – zero-extends (wrong for negative values)
        return dest + " = (" + ctype + ")(" + sctype + ")" + src + ";";
    }

    private static String translateLoad(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String addrExpr = ops[1].trim();

        String space = opParts.length > 1 ? opParts[1] : "global";
        String ptxType = opParts.length > 2 ? opParts[opParts.length - 1] : "u64";
        String ctype = toCType(ptxType);

        if (space.equals("param")) {
            // ld.param.TYPE dest, [paramName]
            String paramName = extractAddr(addrExpr);
            return dest + " = " + paramName + ";";
        } else if (space.equals("local")) {
            // ld.local.TYPE dest, [addr+offset] or [arr+offset]
            return dest + " = *(" + ctype + "*)(" + buildAddrExpr(addrExpr) + ");";
        } else if (space.equals("shared")) {
            // PTX shared memory uses two addressing forms:
            //   arrName[index]    -> direct array element access (no outer brackets)
            //   [addr+offset]     -> pointer-based access (with outer brackets)
            //
            // PTX shared arrays can be declared as .s32 but accessed with st/ld.f32 etc.
            // If the array was re-declared with the correct effective type (via pre-scan),
            // use direct array access so NVRTC can optimize without pointer-cast overhead.
            // Otherwise fall back to bitwise pointer-cast reinterpretation.
            if (!addrExpr.startsWith("[")) {
                String arrBase = addrExpr.contains("[") ? addrExpr.substring(0, addrExpr.indexOf('[')) : addrExpr;
                String inferredType = SHARED_EFFECTIVE_TYPE.get().get(arrBase);
                if (inferredType != null && inferredType.equals(ctype)) {
                    // Types agree — direct access (no pointer cast needed)
                    return dest + " = " + subst(addrExpr) + ";";
                }
                return dest + " = *((" + ctype + "*)&" + subst(addrExpr) + ");";
            }
            return dest + " = *(" + ctype + "*)(" + buildAddrExpr(addrExpr) + ");";
        } else {
            // ld.global.TYPE dest, [addr] or [addr+offset]
            // Use (ctype*)(addr) form so integer arithmetic happens before the pointer cast,
            // preventing C pointer arithmetic scaling (e.g. *((float*)reg+4) would add 16 bytes,
            // not 4).
            if ("b16".equals(ptxType) && isHalfReg(dest)) {
                return dest + " = __ushort_as_half(*((unsigned short*)(" + buildAddrExpr(addrExpr) + ")));";
            }
            return dest + " = *((" + ctype + "*)(" + buildAddrExpr(addrExpr) + "));";
        }
    }

    private static String translateStore(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String addrExpr = ops[0].trim();
        String src = subst(ops[1]);

        String space = opParts.length > 1 ? opParts[1] : "global";
        String ptxType = opParts.length > 2 ? opParts[opParts.length - 1] : "u64";
        String ctype = toCType(ptxType);

        if (space.equals("local")) {
            return "*((" + ctype + "*)(" + buildAddrExpr(addrExpr) + ")) = " + src + ";";
        } else if (space.equals("shared")) {
            // PTX shared memory uses two addressing forms:
            //   arrName[index]  -> direct array element access (no outer brackets)
            //   [addr+offset]   -> pointer-based access (with outer brackets)
            //
            // If the array was re-declared with the correct effective type (via pre-scan),
            // use direct array access. Otherwise use pointer-cast reinterpretation.
            if (!addrExpr.startsWith("[")) {
                String arrBase = addrExpr.contains("[") ? addrExpr.substring(0, addrExpr.indexOf('[')) : addrExpr;
                String inferredType = SHARED_EFFECTIVE_TYPE.get().get(arrBase);
                if (inferredType != null && inferredType.equals(ctype)) {
                    // Types agree — direct assignment (no pointer cast needed)
                    return subst(addrExpr) + " = " + src + ";";
                }
                return "*((" + ctype + "*)&" + subst(addrExpr) + ") = " + src + ";";
            }
            return "*((" + ctype + "*)(" + buildAddrExpr(addrExpr) + ")) = " + src + ";";
        } else {
            // b16 stores from half registers need __half_as_ushort() for correct bit-reinterpretation
            if ("b16".equals(ptxType) && isHalfReg(src)) {
                return "*((unsigned short*)(" + buildAddrExpr(addrExpr) + ")) = __half_as_ushort(" + src + ");";
            }
            return "*((" + ctype + "*)(" + buildAddrExpr(addrExpr) + ")) = " + src + ";";
        }
    }

    /** Returns true if the variable name corresponds to a half-float register.
     *  TornadoVM's PTX register allocator uses names starting with the f16 type prefix. */
    private static boolean isHalfReg(String varName) {
        // Half registers come from .reg .f16 declarations and end up with type "half" in C.
        // The variable names follow the pattern: prefix + index, where prefix contains "fh"
        // (e.g. rfh0, rfh1) since f16 registers use "fh" as the short name in TornadoVM.
        return varName != null && varName.matches(".*fh\\d+");
    }

    private static String translateAtom(String[] opParts, String[] ops, String operandsStr) {
        // atom.space.op.type dest, [addr], val
        // atom.space.op.type dest, [addr], val, val2 (for cas)
        if (ops.length < 3) return null;
        String dest = subst(ops[0]);
        String addrRaw = ops[1];
        String val = subst(ops[2]);

        String space = opParts.length > 1 ? opParts[1] : "global";
        String op = opParts.length > 2 ? opParts[2] : "add";
        String ptxType = opParts.length > 3 ? opParts[opParts.length - 1] : "u32";
        String ctype = toCType(ptxType);
        String addr = buildAddrExpr(addrRaw);

        switch (op) {
            case "add":  return dest + " = atomicAdd((" + ctype + "*)(" + addr + "), " + val + ");";
            case "sub":  return dest + " = atomicSub((" + ctype + "*)(" + addr + "), " + val + ");";
            case "and":  return dest + " = atomicAnd((" + ctype + "*)(" + addr + "), " + val + ");";
            case "or":   return dest + " = atomicOr((" + ctype + "*)(" + addr + "), " + val + ");";
            case "xor":  return dest + " = atomicXor((" + ctype + "*)(" + addr + "), " + val + ");";
            case "max":  return dest + " = atomicMax((" + ctype + "*)(" + addr + "), " + val + ");";
            case "min":  return dest + " = atomicMin((" + ctype + "*)(" + addr + "), " + val + ");";
            case "exch": return dest + " = atomicExch((" + ctype + "*)(" + addr + "), " + val + ");";
            case "cas": {
                if (ops.length < 4) return null;
                String val2 = subst(ops[3]);
                return dest + " = atomicCAS((" + ctype + "*)(" + addr + "), " + val + ", " + val2 + ");";
            }
            case "inc":  return dest + " = atomicInc((" + ctype + "*)(" + addr + "), " + val + ");";
            case "dec":  return dest + " = atomicDec((" + ctype + "*)(" + addr + "), " + val + ");";
            default:     return "/* atom." + op + " unsupported */";
        }
    }

    private static String translateRed(String[] opParts, String[] ops, String operandsStr) {
        // red.space.op.type [addr], val  (no dest)
        if (ops.length < 2) return null;
        String addrRaw = ops[0];
        String val = subst(ops[1]);
        String op = opParts.length > 2 ? opParts[2] : "add";
        String ptxType = opParts.length > 3 ? opParts[opParts.length - 1] : "u32";
        String ctype = toCType(ptxType);
        String addr = buildAddrExpr(addrRaw);
        switch (op) {
            case "add":  return "atomicAdd((" + ctype + "*)(" + addr + "), " + val + ");";
            case "and":  return "atomicAnd((" + ctype + "*)(" + addr + "), " + val + ");";
            case "or":   return "atomicOr((" + ctype + "*)(" + addr + "), " + val + ");";
            default:     return "/* red." + op + " unsupported */";
        }
    }

    private static String translateSqrt(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "f32";
        return dest + " = " + (type.equals("f64") ? "sqrt(" : "sqrtf(") + src + ");";
    }

    private static String translateRsqrt(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "f32";
        return dest + " = " + (type.equals("f64") ? "rsqrt(" : "rsqrtf(") + src + ");";
    }

    private static String translateTrig(String[] opParts, String[] ops, String f32func, String f64func) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "f32";
        return dest + " = " + (type.equals("f64") ? f64func : f32func) + "(" + src + ");";
    }

    private static String translateEx2(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "f32";
        return dest + " = " + (type.equals("f64") ? "exp2(" : "exp2f(") + src + ");";
    }

    private static String translateLg2(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "f32";
        return dest + " = " + (type.equals("f64") ? "log2(" : "log2f(") + src + ");";
    }

    private static String translateTanh(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "f32";
        return dest + " = " + (type.equals("f64") ? "tanh(" : "tanhf(") + src + ");";
    }

    private static String translateRcp(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "f32";
        if (type.equals("f64")) {
            return dest + " = 1.0 / " + src + ";";
        }
        return dest + " = 1.0f / " + src + ";";
    }

    private static String translateCall(String[] opParts, String[] ops, String operandsStr) {
        // PTX call: call (retvar), funcname, (params)  or call funcname, (params)
        // This is complex; emit a comment with the original and let NVRTC handle it
        // For simple cases: call.uni (retvar), funcname, (arglist)
        // We try to extract: call [.uni] [(retvar),] funcname, [(arglist)]
        Pattern p = Pattern.compile("(?:uni\\s+)?(?:\\((\\w+)\\)\\s*,\\s*)?(\\w+)(?:\\s*,\\s*\\((.*)\\))?");
        Matcher m = p.matcher(operandsStr.replaceAll(";$", ""));
        if (m.find()) {
            String retVar = m.group(1);
            String funcName = m.group(2);
            String argList = m.group(3);
            String args = (argList != null) ? substAll(argList) : "";
            if (retVar != null && !retVar.isEmpty()) {
                return retVar + " = " + funcName + "(" + args + ");";
            } else {
                return funcName + "(" + args + ");";
            }
        }
        return "/* call: " + operandsStr + " */";
    }

    private static String translateVote(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String pred = subst(ops[1]);
        String op = opParts.length > 1 ? opParts[1] : "any";
        switch (op) {
            case "all":  return dest + " = __all_sync(0xffffffff, " + pred + ");";
            case "any":  return dest + " = __any_sync(0xffffffff, " + pred + ");";
            case "uni":  return dest + " = __all_sync(0xffffffff, " + pred + ");";
            case "ballot": return dest + " = __ballot_sync(0xffffffff, " + pred + ");";
            default:     return "/* vote." + op + " */";
        }
    }

    private static String translateShfl(String[] opParts, String[] ops, String operandsStr) {
        // shfl.sync.mode.b32 dest, pred, src, offset, mask
        if (ops.length < 4) return null;
        String dest = subst(ops[0]);
        String src, offset, mask;
        // Determine if sync variant
        boolean isSync = Arrays.asList(opParts).contains("sync");
        int srcIdx = isSync ? 2 : 1;
        if (ops.length < srcIdx + 3) return "/* shfl operand count mismatch */";
        src = subst(ops[srcIdx]);
        offset = subst(ops[srcIdx + 1]);
        mask = (ops.length > srcIdx + 2) ? subst(ops[srcIdx + 2]) : "0xffffffff";

        String mode = "down";
        for (String p : opParts) {
            if (p.equals("up") || p.equals("down") || p.equals("bfly") || p.equals("idx")) {
                mode = p;
                break;
            }
        }
        switch (mode) {
            case "down":  return dest + " = __shfl_down_sync(" + mask + ", " + src + ", " + offset + ");";
            case "up":    return dest + " = __shfl_up_sync(" + mask + ", " + src + ", " + offset + ");";
            case "bfly":  return dest + " = __shfl_xor_sync(" + mask + ", " + src + ", " + offset + ");";
            case "idx":   return dest + " = __shfl_sync(" + mask + ", " + src + ", " + offset + ");";
            default:      return dest + " = __shfl_down_sync(" + mask + ", " + src + ", " + offset + ");";
        }
    }

    private static String translatePopc(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "b32";
        if (type.equals("b64")) {
            return dest + " = __popcll(" + src + ");";
        }
        return dest + " = __popc(" + src + ");";
    }

    private static String translateClz(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "b32";
        if (type.equals("b64")) {
            return dest + " = __clzll(" + src + ");";
        }
        return dest + " = __clz(" + src + ");";
    }

    private static String translateBrev(String[] opParts, String[] ops) {
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src = subst(ops[1]);
        String type = opParts.length > 1 ? opParts[opParts.length - 1] : "b32";
        if (type.equals("b64")) {
            return dest + " = __brevll(" + src + ");";
        }
        return dest + " = __brev(" + src + ");";
    }

    private static String translateDp4a(String[] opParts, String[] ops) {
        if (ops.length < 4) return null;
        String dest = subst(ops[0]);
        String a = subst(ops[1]);
        String b = subst(ops[2]);
        String c = subst(ops[3]);
        return dest + " = __dp4a(" + a + ", " + b + ", " + c + ");";
    }

    private static String translateTestp(String[] opParts, String[] ops) {
        // testp.{finite|infinite|number|nan|normal|subnormal}.TYPE dest, src
        // NVRTC does not expose isnormal/isfinite/fpclassify, so use IEEE 754 bitmask ops.
        // For f32: exponent bits = bits[30:23].  Exponent == 0 → zero/subnormal.  Exponent == 0xFF → inf/NaN.
        if (ops.length < 2) return null;
        String dest = subst(ops[0]);
        String src  = subst(ops[1]);
        String type = opParts[opParts.length - 1]; // last part = f32 / f64
        String test = opParts.length > 1 ? opParts[1] : "normal";
        if ("f64".equals(type)) {
            // f64: use __double_as_longlong; exponent bits = bits[62:52]
            String bits = "__double_as_longlong(" + src + ")";
            String exp  = "((" + bits + " >> 52) & 0x7FFL)";
            switch (test) {
                case "normal":    return dest + " = (" + exp + " != 0L && " + exp + " != 0x7FFL) ? 1 : 0;";
                case "finite":    return dest + " = (" + exp + " != 0x7FFL) ? 1 : 0;";
                case "infinite":  return dest + " = (" + exp + " == 0x7FFL && (" + bits + " & 0x000FFFFFFFFFFFFFL) == 0L) ? 1 : 0;";
                case "nan":       return dest + " = (" + exp + " == 0x7FFL && (" + bits + " & 0x000FFFFFFFFFFFFFL) != 0L) ? 1 : 0;";
                case "number":    return dest + " = (" + exp + " != 0x7FFL || (" + bits + " & 0x000FFFFFFFFFFFFFL) == 0L) ? 1 : 0;";
                case "subnormal": return dest + " = (" + exp + " == 0L && (" + bits + " & 0x000FFFFFFFFFFFFFL) != 0L) ? 1 : 0;";
                default:          return dest + " = (" + exp + " != 0L && " + exp + " != 0x7FFL) ? 1 : 0;";
            }
        } else {
            // f32: use __float_as_int; exponent bits = bits[30:23]
            String bits = "__float_as_int(" + src + ")";
            String exp  = "((" + bits + " >> 23) & 0xFF)";
            switch (test) {
                case "normal":    return dest + " = (" + exp + " != 0 && " + exp + " != 0xFF) ? 1 : 0;";
                case "finite":    return dest + " = (" + exp + " != 0xFF) ? 1 : 0;";
                case "infinite":  return dest + " = (" + exp + " == 0xFF && (" + bits + " & 0x7FFFFF) == 0) ? 1 : 0;";
                case "nan":       return dest + " = (" + exp + " == 0xFF && (" + bits + " & 0x7FFFFF) != 0) ? 1 : 0;";
                case "number":    return dest + " = (" + exp + " != 0xFF || (" + bits + " & 0x7FFFFF) == 0) ? 1 : 0;";
                case "subnormal": return dest + " = (" + exp + " == 0 && (" + bits + " & 0x7FFFFF) != 0) ? 1 : 0;";
                default:          return dest + " = (" + exp + " != 0 && " + exp + " != 0xFF) ? 1 : 0;";
            }
        }
    }

    // -------------------------------------------------------------------------
    // Parameter list translation
    // -------------------------------------------------------------------------

    private static String translateParamList(String paramList, boolean isKernel) {
        // Splits by comma, but only at top level (not inside brackets)
        List<String> params = splitTopLevel(paramList, ',');
        List<String> cParams = new ArrayList<>();
        for (String param : params) {
            param = param.trim();
            if (param.isEmpty()) continue;
            String translated = translateOneParam(param, isKernel);
            if (translated != null) {
                cParams.add(translated);
            }
        }
        return String.join(", ", cParams);
    }

    private static String translateOneParam(String param, boolean isKernel) {
        // PTX param forms:
        //   .param .u64 name
        //   .param .u64 .ptr .global .align 8 name
        //   .param .align 8 .u64 name
        //   .param .align 8 .b8 name[16]    (struct/vector)
        //   .reg .TYPE name   (device function params)
        param = param.trim();
        if (param.isEmpty()) return null;

        // For device functions, params look like ".reg .s32 varname"
        // For kernels, params look like ".param .u64 name" or ".param .u64 .ptr ... name"
        // Extract the last token as parameter name
        String[] tokens = param.split("\\s+");
        if (tokens.length == 0) return null;

        String lastName = tokens[tokens.length - 1];
        // Handle array: "name[N]"
        boolean isArray = lastName.contains("[");
        String paramName = isArray ? lastName.substring(0, lastName.indexOf('[')) : lastName;
        String arraySuffix = isArray ? lastName.substring(lastName.indexOf('[')) : "";
        // Sanitize C++ reserved keywords that may appear as PTX parameter names
        paramName = sanitizeParamName(paramName);

        // Find the type token (after .param or .reg, skipping modifiers like .ptr, .global, .align N)
        String ptxType = findTypeToken(tokens);

        if (isKernel) {
            // All kernel params are passed as unsigned long long (pointer-sized)
            // Exception: if it's a struct/vector via .b8 name[N], use char*
            if (ptxType != null && ptxType.startsWith("b8") && isArray) {
                return "char* " + paramName;
            }
            return "unsigned long long " + paramName + arraySuffix;
        } else {
            // Device function params: use the actual type
            String ctype = (ptxType != null) ? toCType(ptxType) : "unsigned long long";
            return ctype + " " + paramName + arraySuffix;
        }
    }

    private static String findTypeToken(String[] tokens) {
        // Skip .param, .reg, .ptr, .global, .local, .shared, .const,
        //       .align, (number after .align), and qualifiers
        boolean skipNext = false;
        for (String tok : tokens) {
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (tok.equals(".align")) {
                skipNext = true;
                continue;
            }
            if (tok.equals(".param") || tok.equals(".reg")
                    || tok.equals(".ptr") || tok.equals(".global")
                    || tok.equals(".local") || tok.equals(".shared")
                    || tok.equals(".const")) {
                continue;
            }
            if (tok.startsWith(".")) {
                // This looks like a type: .s32, .u64, .f32, .b8 etc.
                return tok.substring(1); // strip leading dot
            }
        }
        return null;
    }

    /** Rename PTX parameter names that are C++ reserved keywords. */
    private static String sanitizeParamName(String name) {
        // C++ keywords that can appear as Java/PTX parameter names
        switch (name) {
            case "this":      return "this_param";
            case "class":     return "class_param";
            case "template":  return "template_param";
            case "namespace": return "namespace_param";
            case "new":       return "new_param";
            case "delete":    return "delete_param";
            case "operator":  return "operator_param";
            case "virtual":   return "virtual_param";
            case "register":  return "register_param";
            default:          return name;
        }
    }

    private static String extractFuncReturnType(String retDecl) {
        // Patterns: ".reg .TYPE retVar" or ".param .align N .bN retVar[N]"
        String[] tokens = retDecl.trim().split("\\s+");
        String ptxType = findTypeToken(tokens);
        if (ptxType == null) return "void";
        return toCType(ptxType);
    }

    private static void emitFunctionSignature(StringBuilder out, boolean isKernel,
            String name, String returnType, String params) {
        if (isKernel) {
            out.append("extern \"C\" __global__ void ").append(name).append("(").append(params).append(")");
        } else {
            String ret = (returnType != null) ? returnType : "void";
            out.append("__device__ ").append(ret).append(" ").append(name).append("(").append(params).append(")");
        }
    }

    // -------------------------------------------------------------------------
    // Address / operand helpers
    // -------------------------------------------------------------------------

    /**
     * Build a C address expression from a PTX address operand.
     * PTX: [addr], [addr+16], [arr], [arr+4]
     * C: addr, addr+16, arr, arr+4
     */
    private static String buildAddrExpr(String addrExpr) {
        addrExpr = addrExpr.trim();
        if (addrExpr.startsWith("[") && addrExpr.endsWith("]")) {
            String inner = addrExpr.substring(1, addrExpr.length() - 1).trim();
            return subst(inner);
        }
        return subst(addrExpr);
    }

    /** Extract the address name from a PTX bracket expression [name] */
    private static String extractAddr(String addrExpr) {
        addrExpr = addrExpr.trim();
        if (addrExpr.startsWith("[") && addrExpr.endsWith("]")) {
            String inner = addrExpr.substring(1, addrExpr.length() - 1).trim();
            // Remove +0 offset if present
            if (inner.contains("+")) {
                String[] parts = inner.split("\\+", 2);
                String offset = parts[1].trim();
                if (offset.equals("0")) return subst(parts[0].trim());
                return subst(inner);
            }
            return subst(inner);
        }
        return subst(addrExpr);
    }

    // -------------------------------------------------------------------------
    // Value substitution
    // -------------------------------------------------------------------------

    /** Substitute built-in registers and float hex literals in a single token */
    private static String subst(String val) {
        val = val.trim();
        // Check built-in registers
        String builtin = BUILTIN_REGS.get(val);
        if (builtin != null) return builtin;

        // Float hex literal: 0F3F800000
        Matcher fm = FLOAT_HEX_PAT.matcher(val);
        if (fm.matches()) {
            return "__int_as_float(0x" + fm.group(1).toUpperCase() + ")";
        }
        // Double hex literal: 0D3FF0000000000000
        Matcher dm = DOUBLE_HEX_PAT.matcher(val);
        if (dm.matches()) {
            return "__longlong_as_double(0x" + dm.group(1).toUpperCase() + "LL)";
        }
        return val;
    }

    /** Substitute built-ins in a comma-separated list */
    private static String substAll(String list) {
        String[] parts = list.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(subst(parts[i].trim()));
        }
        return sb.toString();
    }

    /** Substitute built-in registers in a string (not just a single token) */
    private static String substituteBuiltins(String s) {
        for (Map.Entry<String, String> e : BUILTIN_REGS.entrySet()) {
            s = s.replace(e.getKey(), e.getValue());
        }
        return s;
    }

    // -------------------------------------------------------------------------
    // Type and operator helpers
    // -------------------------------------------------------------------------

    private static String toCType(String ptxType) {
        String c = PTX_TO_C_TYPE.get(ptxType);
        return (c != null) ? c : "unsigned long long";
    }

    private static String toCmpOp(String ptxCmp) {
        switch (ptxCmp) {
            case "eq":  return "==";
            case "ne":  return "!=";
            case "lt":  return "<";
            case "le":  return "<=";
            case "gt":  return ">";
            case "ge":  return ">=";
            case "lo":  return "<";   // unsigned less-than
            case "ls":  return "<=";  // unsigned less-or-equal
            case "hi":  return ">";   // unsigned greater-than
            case "hs":  return ">=";  // unsigned greater-or-equal
            case "equ": return "==";  // unordered eq (NaN-safe)
            case "neu": return "!=";
            case "ltu": return "<";
            case "leu": return "<=";
            case "gtu": return ">";
            case "geu": return ">=";
            case "num": return "==";  // both numbers (not NaN): use ==
            case "nan": return "!=";  // either NaN
            default:    return "==";
        }
    }

    // -------------------------------------------------------------------------
    // String utilities
    // -------------------------------------------------------------------------

    /**
     * Split a comma-separated operand string, respecting brackets.
     */
    private static String[] splitOperands(String ops) {
        return splitTopLevel(ops, ',').toArray(new String[0]);
    }

    /**
     * Split a string by a delimiter, but only at the top level
     * (not inside parentheses or brackets).
     */
    private static List<String> splitTopLevel(String s, char delim) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            if (c == delim && depth == 0) {
                result.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        String last = cur.toString().trim();
        if (!last.isEmpty()) result.add(last);
        return result;
    }
}
