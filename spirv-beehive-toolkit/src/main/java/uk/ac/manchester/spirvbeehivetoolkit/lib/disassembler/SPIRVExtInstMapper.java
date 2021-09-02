/*
* MIT License
*
* Copyright (c) 2021, APT Group, Department of Computer Science,
* The University of Manchester.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy
* of this software and associated documentation files (the "Software"), to deal
* in the Software without restriction, including without limitation the rights
* to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the Software is
* furnished to do so, subject to the following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
* IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
* OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
* SOFTWARE.
*/

package uk.ac.manchester.spirvbeehivetoolkit.lib.disassembler;

import java.util.HashMap;
import javax.annotation.Generated;

/**
 * SPIR-V handles built in functions by OpenCL or GLSL by importing their standard.
 * This standard contains all of the functions provided by them and
 * a mapping between the function names and corresponding numbers.
 */
@Generated("beehive-lab.spirvbeehivetoolkit.generator")
public class SPIRVExtInstMapper {
    private static HashMap<Integer, String> extInstNameMap;

    /**
     * Get the name of the function mapped to the given number.
     * @param opCode - The number of the external function
     * @return The name of the external function mapped to this number.
     */
    public static String get(int opCode) {
        return extInstNameMap.get(opCode);
    }

    /**
     * Load all of the mapped values into memory (otherwise it is not available in order save space)
     */
    public static void loadOpenCL() {
        extInstNameMap = new HashMap<>(162);
        extInstNameMap.put(0, "acos");
        extInstNameMap.put(1, "acosh");
        extInstNameMap.put(2, "acospi");
        extInstNameMap.put(3, "asin");
        extInstNameMap.put(4, "asinh");
        extInstNameMap.put(5, "asinpi");
        extInstNameMap.put(6, "atan");
        extInstNameMap.put(7, "atan2");
        extInstNameMap.put(8, "atanh");
        extInstNameMap.put(9, "atanpi");
        extInstNameMap.put(10, "atan2pi");
        extInstNameMap.put(11, "cbrt");
        extInstNameMap.put(12, "ceil");
        extInstNameMap.put(13, "copysign");
        extInstNameMap.put(14, "cos");
        extInstNameMap.put(15, "cosh");
        extInstNameMap.put(16, "cospi");
        extInstNameMap.put(17, "erfc");
        extInstNameMap.put(18, "erf");
        extInstNameMap.put(19, "exp");
        extInstNameMap.put(20, "exp2");
        extInstNameMap.put(21, "exp10");
        extInstNameMap.put(22, "expm1");
        extInstNameMap.put(23, "fabs");
        extInstNameMap.put(24, "fdim");
        extInstNameMap.put(25, "floor");
        extInstNameMap.put(26, "fma");
        extInstNameMap.put(27, "fmax");
        extInstNameMap.put(28, "fmin");
        extInstNameMap.put(29, "fmod");
        extInstNameMap.put(30, "fract");
        extInstNameMap.put(31, "frexp");
        extInstNameMap.put(32, "hypot");
        extInstNameMap.put(33, "ilogb");
        extInstNameMap.put(34, "ldexp");
        extInstNameMap.put(35, "lgamma");
        extInstNameMap.put(36, "lgamma_r");
        extInstNameMap.put(37, "log");
        extInstNameMap.put(38, "log2");
        extInstNameMap.put(39, "log10");
        extInstNameMap.put(40, "log1p");
        extInstNameMap.put(41, "logb");
        extInstNameMap.put(42, "mad");
        extInstNameMap.put(43, "maxmag");
        extInstNameMap.put(44, "minmag");
        extInstNameMap.put(45, "modf");
        extInstNameMap.put(46, "nan");
        extInstNameMap.put(47, "nextafter");
        extInstNameMap.put(48, "pow");
        extInstNameMap.put(49, "pown");
        extInstNameMap.put(50, "powr");
        extInstNameMap.put(51, "remainder");
        extInstNameMap.put(52, "remquo");
        extInstNameMap.put(53, "rint");
        extInstNameMap.put(54, "rootn");
        extInstNameMap.put(55, "round");
        extInstNameMap.put(56, "rsqrt");
        extInstNameMap.put(57, "sin");
        extInstNameMap.put(58, "sincos");
        extInstNameMap.put(59, "sinh");
        extInstNameMap.put(60, "sinpi");
        extInstNameMap.put(61, "sqrt");
        extInstNameMap.put(62, "tan");
        extInstNameMap.put(63, "tanh");
        extInstNameMap.put(64, "tanpi");
        extInstNameMap.put(65, "tgamma");
        extInstNameMap.put(66, "trunc");
        extInstNameMap.put(67, "half_cos");
        extInstNameMap.put(68, "half_divide");
        extInstNameMap.put(69, "half_exp");
        extInstNameMap.put(70, "half_exp2");
        extInstNameMap.put(71, "half_exp10");
        extInstNameMap.put(72, "half_log");
        extInstNameMap.put(73, "half_log2");
        extInstNameMap.put(74, "half_log10");
        extInstNameMap.put(75, "half_powr");
        extInstNameMap.put(76, "half_recip");
        extInstNameMap.put(77, "half_rsqrt");
        extInstNameMap.put(78, "half_sin");
        extInstNameMap.put(79, "half_sqrt");
        extInstNameMap.put(80, "half_tan");
        extInstNameMap.put(81, "native_cos");
        extInstNameMap.put(82, "native_divide");
        extInstNameMap.put(83, "native_exp");
        extInstNameMap.put(84, "native_exp2");
        extInstNameMap.put(85, "native_exp10");
        extInstNameMap.put(86, "native_log");
        extInstNameMap.put(87, "native_log2");
        extInstNameMap.put(88, "native_log10");
        extInstNameMap.put(89, "native_powr");
        extInstNameMap.put(90, "native_recip");
        extInstNameMap.put(91, "native_rsqrt");
        extInstNameMap.put(92, "native_sin");
        extInstNameMap.put(93, "native_sqrt");
        extInstNameMap.put(94, "native_tan");
        extInstNameMap.put(95, "fclamp");
        extInstNameMap.put(96, "degrees");
        extInstNameMap.put(97, "fmax_common");
        extInstNameMap.put(98, "fmin_common");
        extInstNameMap.put(99, "mix");
        extInstNameMap.put(100, "radians");
        extInstNameMap.put(101, "step");
        extInstNameMap.put(102, "smoothstep");
        extInstNameMap.put(103, "sign");
        extInstNameMap.put(104, "cross");
        extInstNameMap.put(105, "distance");
        extInstNameMap.put(106, "length");
        extInstNameMap.put(107, "normalize");
        extInstNameMap.put(108, "fast_distance");
        extInstNameMap.put(109, "fast_length");
        extInstNameMap.put(110, "fast_normalize");
        extInstNameMap.put(141, "s_abs");
        extInstNameMap.put(142, "s_abs_diff");
        extInstNameMap.put(143, "s_add_sat");
        extInstNameMap.put(144, "u_add_sat");
        extInstNameMap.put(145, "s_hadd");
        extInstNameMap.put(146, "u_hadd");
        extInstNameMap.put(147, "s_rhadd");
        extInstNameMap.put(148, "u_rhadd");
        extInstNameMap.put(149, "s_clamp");
        extInstNameMap.put(150, "u_clamp");
        extInstNameMap.put(151, "clz");
        extInstNameMap.put(152, "ctz");
        extInstNameMap.put(153, "s_mad_hi");
        extInstNameMap.put(154, "u_mad_sat");
        extInstNameMap.put(155, "s_mad_sat");
        extInstNameMap.put(156, "s_max");
        extInstNameMap.put(157, "u_max");
        extInstNameMap.put(158, "s_min");
        extInstNameMap.put(159, "u_min");
        extInstNameMap.put(160, "s_mul_hi");
        extInstNameMap.put(161, "rotate");
        extInstNameMap.put(162, "s_sub_sat");
        extInstNameMap.put(163, "u_sub_sat");
        extInstNameMap.put(164, "u_upsample");
        extInstNameMap.put(165, "s_upsample");
        extInstNameMap.put(166, "popcount");
        extInstNameMap.put(167, "s_mad24");
        extInstNameMap.put(168, "u_mad24");
        extInstNameMap.put(169, "s_mul24");
        extInstNameMap.put(170, "u_mul24");
        extInstNameMap.put(171, "vloadn");
        extInstNameMap.put(172, "vstoren");
        extInstNameMap.put(173, "vload_half");
        extInstNameMap.put(174, "vload_halfn");
        extInstNameMap.put(175, "vstore_half");
        extInstNameMap.put(176, "vstore_half_r");
        extInstNameMap.put(177, "vstore_halfn");
        extInstNameMap.put(178, "vstore_halfn_r");
        extInstNameMap.put(179, "vloada_halfn");
        extInstNameMap.put(180, "vstorea_halfn");
        extInstNameMap.put(181, "vstorea_halfn_r");
        extInstNameMap.put(182, "shuffle");
        extInstNameMap.put(183, "shuffle2");
        extInstNameMap.put(184, "printf");
        extInstNameMap.put(185, "prefetch");
        extInstNameMap.put(186, "bitselect");
        extInstNameMap.put(187, "select");
        extInstNameMap.put(201, "u_abs");
        extInstNameMap.put(202, "u_abs_diff");
        extInstNameMap.put(203, "u_mul_hi");
        extInstNameMap.put(204, "u_mad_hi");
    }
}