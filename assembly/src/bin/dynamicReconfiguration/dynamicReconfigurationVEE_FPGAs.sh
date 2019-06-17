#!/bin/bash

# #########################################################################
# Runner script for dynamic reconfiguration. This script assumes an FPGA 
# in the device 0:1
# For custom changes, change the lines marked with [FPGA]
# #########################################################################

## This function assumes that the FPGA is the device in index 1.
## If it is not, change XXX1.t0.device=0:1 for the corresponding device
function runCommand() {
	globalDims=$1
	localDims=$2
	pathToBinary=$3
	javaProgram=$4
	policy=$5
	repetitions=$6

	size=$globalDims

	if [ "$#" -eq 7 ]
	then
		size=$7 
	fi

	## [FPGA] CHANGE THIS LINE WITH THE FPGA DEVICE INDEX
	fpgaDevice="-DXXX1.t0.device=0:1"

	#tornado --printKernel --debug \
	tornado \
		-Xmx20g -Xms20g \
		-Dtornado.opencl.userelative=True \
		-Dtornado.dynamic.verbose=True \
		$fpgaDevice \
		-Dtornado.precompiled.binary=$pathToBinary \
		-DXXX1.t0.global.dims=$globalDims \
		-DXXX1.t0.local.dims=$localDims \
		$javaProgram $size $policy $repetitions
}


function runCommandRepetitions() {
	for y in `seq 1 $iter`
	do
		echo "**************************************************************************"
		echo "Running for size $j"
		runCommand $@
		sleep 5
	done
}

function nbody() {
	echo "======================="
	echo "NBody with policy: $1"
	echo "======================="
	policy=$1
	item=1
	iter=1
	for i in {9..16} 
	do
		j=$((2**$i))
		globalDims=$j
		localDims=64
		## [FPGA] CHANGE THIS LINE WITH THE PATH TO THE BINARY
		pathToBinary="/hdd/vee2019_artifact_supplements/bitstreams/nbd/lookupBufferAddress,XXX1.t0.device=0:1"
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.NBodyDynamic"
		repetitions=1
		runCommandRepetitions $globalDims $localDims $pathToBinary $javaProgram $policy $repetitions 
	done
}

function saxpy() {
	echo "======================="
	echo "Saxpy with policy: $1"
	echo "======================="
	policy=$1
	iter=1
	for i in {15..26} 
	do
		j=$((2**$i))
		globalDims=$j
		localDims=128
		## [FPGA] CHANGE THIS LINE WITH THE PATH TO THE BINARY
		pathToBinary="/hdd/vee2019_artifact_supplements/bitstreams/sax/lookupBufferAddress,XXX1.t0.device=0:1"
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.SaxpyDynamic"
		repetitions=5
		runCommandRepetitions $globalDims $localDims $pathToBinary $javaProgram $policy $repetitions

	done
}

function montecarlo() {
	echo "======================="
	echo "Montecarlo with policy $1"
	echo "======================="
	policy=$1
	iter=1
	for i in {16..27} 
	do
		j=$((2**$i))
		globalDims=$j
		localDims=128
		## [FPGA] CHANGE THIS LINE WITH THE PATH TO THE BINARY
		pathToBinary="/hdd/vee2019_artifact_supplements/bitstreams/mc/lookupBufferAddress,XXX1.t0.device=0:1"
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.MontecarloDynamic"
		repetitions=5

		runCommandRepetitions $globalDims $localDims $pathToBinary $javaProgram $policy $repetitions
	done
}

function renderTrack() {
	echo "======================="
	echo "RenderTrack with policy $1"
	echo "======================="
	policy=$1
	iter=1
	for i in {6..13} 
	do
		j=$((2**$i))
		globalDims=$j,1024
		localDims=64,16
		## [FPGA] CHANGE THIS LINE WITH THE PATH TO THE BINARY
		pathToBinary="/hdd/vee2019_artifact_supplements/bitstreams/rndr/lookupBufferAddress,XXX1.t0.device=0:1"
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.RenderTrackDynamic"
		repetitions=5
		size=$j
		runCommandRepetitions $globalDims $localDims $pathToBinary $javaProgram $policy $repetitions $size
	done
}

function blackscholes() {
	echo "======================="
	echo "Blackscholes with policy $1"
	echo "======================="
	policy=$1
	iter=1
	for i in {16..26} 
	do
		j=$((2**$i))
		globalDims="$j"
		localDims="64"
		## [FPGA] CHANGE THIS LINE WITH THE PATH TO THE BINARY
		pathToBinary="/hdd/vee2019_artifact_supplements/bitstreams/bs/lookupBufferAddress,XXX1.t0.device=0:1"
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.BlackScholesDynamic"
		repetitions=5
		runCommandRepetitions $globalDims $localDims $pathToBinary $javaProgram $policy $repetitions
	done
}

function dft() {
	echo "======================="
	echo "Running DFT with policy $1"
	echo "======================="
	policy=$1
	iter=1
	for i in {6..20} 
	do
		j=$((2**$i))
		globalDims="$j"
		localDims="64"
		## [FPGA] CHANGE THIS LINE WITH THE PATH TO THE BINARY
		pathToBinary="/hdd/vee2019_artifact_supplements/bitstreams/df/lookupBufferAddress,XXX1.t0.device=0:1"
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.DFTDynamic"
		repetitions=5
		runCommandRepetitions $globalDims $localDims $pathToBinary $javaProgram $policy $repetitions
	done
}

function runALL() {
	saxpy "end"
	saxpy "performance"

	montecarlo "end"
	montecarlo "performance"

	renderTrack "end"
	renderTrack "performance"

	blackscholes "end"
	blackscholes "performance"

	nbody "end"
	nbody "performance"

	dft "end"
	dft "performance"
}

runALL
