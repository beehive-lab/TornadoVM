#!/bin/bash

# #########################################################################
# Runner script for dynamic reconfiguration
# #########################################################################

function runCommand() {
	javaProgram=$1
    size=$2
	policy=$3
	repetitions=$4

	#tornado --printKernel --debug \
	tornado \
		-Xmx20g -Xms20g \
		-Dtornado.opencl.userelative=True \
		-Dtornado.dynamic.verbose=True \
		$javaProgram $size $policy $repetitions
}

function runCommandRepetitions() {
	for y in `seq 1 $iter`
	do
		echo "**************************************************************************"
		echo "Running for size $2"
		echo "**************************************************************************"
		javaProgram=$1
        size=$2
		policy=$3
		repetitions=$4
		runCommand $javaProgram $size $policy $repetitions
		sleep 5
		echo "**************************************************************************"
	done
}

function nbody() {
	policy=$1
	item=1
	iter=1
	for i in {9..16} 
	do
		size=$((2**$i))
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.NBodyDynamic"
		repetitions=1
		runCommandRepetitions $javaProgram $size $policy $repetitions 
	done
}


function saxpy() {
	echo "Saxpy with policy: $1"
	policy=$1
	iter=1
	for i in {15..26} 
	do
		size=$((2**$i))
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.SaxpyDynamic"
		repetitions=5
        runCommandRepetitions $javaProgram $size $policy $repetitions 
	done
}

function montecarlo() {
	echo "Montecarlo with policy $1"
	policy=$1
	iter=1
	for i in {16..27} 
	do
		size=$((2**$i))
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.MontecarloDynamic"
		repetitions=5
        runCommandRepetitions $javaProgram $size $policy $repetitions 
	done
}


function renderTrack() {
	echo "RenderTrack with policy $1"
	policy=$1
	iter=1
	for i in {6..13} 
	do
		size=$((2**$i))
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.RenderTrackDynamic"
		repetitions=5
        runCommandRepetitions $javaProgram $size $policy $repetitions 
	done
}

function blackscholes() {
	echo "Blackscholes with policy $1"
	policy=$1
	iter=1
	for i in {16..26} 
	do
		size=$((2**$i))
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.BlackScholesDynamic"
		repetitions=5
        runCommandRepetitions $javaProgram $size $policy $repetitions 
	done
}


function dft() {
	echo "Running DFT with policy $1"
	policy=$1
	iter=1
	for i in {6..20} 
	do
		size=$((2**$i))
		javaProgram="uk.ac.manchester.tornado.examples.dynamic.DFTDynamic"
		repetitions=5
        runCommandRepetitions $javaProgram $size $policy $repetitions 
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
