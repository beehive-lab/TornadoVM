import ctypes, sys
lib, src_path, out, *opts = sys.argv[1:]
n = ctypes.CDLL(lib)
src = open(src_path, 'rb').read()
prog = ctypes.c_void_p()
assert n.nvrtcCreateProgram(ctypes.byref(prog), src, b"k.cu", 0, None, None) == 0
o = (ctypes.c_char_p * len(opts))(*[x.encode() for x in opts])
r = n.nvrtcCompileProgram(prog, len(opts), o)
size = ctypes.c_size_t(); n.nvrtcGetProgramLogSize(prog, ctypes.byref(size)); log = ctypes.create_string_buffer(size.value); n.nvrtcGetProgramLog(prog, log)
if r != 0: print("compile failed", r, log.value.decode()[:2000]); sys.exit(1)
n.nvrtcGetCUBINSize(prog, ctypes.byref(size)); buf = ctypes.create_string_buffer(size.value); n.nvrtcGetCUBIN(prog, buf)
open(out, 'wb').write(buf.raw)
v1=ctypes.c_int(); v2=ctypes.c_int(); n.nvrtcVersion(ctypes.byref(v1), ctypes.byref(v2)); print("nvrtc", v1.value, v2.value)
