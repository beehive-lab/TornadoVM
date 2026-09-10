# Phase 1 reference kernels: CUDA Tile C++ written against TornadoVM's CUDA ABI.
# Verified 2026-09-10 on RTX 4090 (sm_89) with nvcc 13.3.73 from the pip wheels.
#
#   make            build both cubins
#   make sass       show the tensor-core instructions in the GEMM
#   make resources  show register/shared/param-block usage

NVCC ?= $(HOME)/.local/lib/python3.10/site-packages/nvidia/cu13/bin/nvcc
CUOBJDUMP ?= /usr/local/cuda/bin/cuobjdump
ARCH ?= sm_89
FLAGS = -tilecubin --tile-only -std=c++20 -arch=$(ARCH)

all: tile_saxpy.cubin tile_gemm.cubin

%.cubin: %.cu
	$(NVCC) $(FLAGS) -o $@ $<

sass: tile_gemm.cubin
	$(CUOBJDUMP) -sass tile_gemm.cubin | grep -oE "HMMA[.0-9A-Z_]*" | sort | uniq -c

resources: tile_gemm.cubin
	$(CUOBJDUMP) -res-usage tile_gemm.cubin

clean:
	rm -f *.cubin
.PHONY: all sass resources clean
