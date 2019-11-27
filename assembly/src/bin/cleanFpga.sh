
vendor=$1
FPGA_DIRECTORY="fpga-source-comp"
BINARY="lookupBufferAddress"

## Back up current kernel 
mv fpga-source-comp/lookupBufferAddress.cl fpga-source-comp/backup_source.cl

## Move current genereted directory
mv fpga-source-comp/lookupBufferAddress fpga-source-comp/intelFPGAFiles

## Create sym link to the original kerenl 
current=`pwd`
cd $FPGA_DIRECTORY

echo "[$vendor]"
if [[ $vendor = "Intel" ]]
then
	ln -s $BINARY.aocx $BINARY 
elif [[ $vendor = "Xilinx" ]]
then
	ln -s $BINARY.xclbin $BINARY
else
	echo "FPGA Vendor no supported yet."
fi

cd $current
