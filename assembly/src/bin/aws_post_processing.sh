#!/usr/bin/env bash

configurationFilePath=$1

export S3_BUCKET=$(grep -w "AWS_S3_BUCKET" ${configurationFilePath} | awk -F'=' '{print $2}')
export S3_DCP_KEY=$(grep -w "AWS_S3_DCP_KEY" ${configurationFilePath} | awk -F'=' '{print $2}')
export S3_LOGS_KEY=$(grep -w "AWS_S3_LOGS_KEY" ${configurationFilePath} | awk -F'=' '{print $2}')
$VITIS_DIR/tools/create_vitis_afi.sh -xclbin=fpga-source-comp/lookupBufferAddress.xclbin -s3_bucket=tornadovm-fpga-bucket -s3_dcp_key=outputfolder -s3_logs_key=logfolder

sudo mv lookupBufferAddress.awsxclbin fpga-source-comp/lookupBufferAddress.awsxclbin
sudo mv to_aws fpga-source-comp/to_aws
cd fpga-source-comp
sudo rm lookupBufferAddress
sudo ln -s lookupBufferAddress.awsxclbin lookupBufferAddress
cd ../

cat *_afi_id.txt
echo "Now you need to wait till the AWS FPGA Image (AFI) is ready for execution. Check:"
echo "aws ec2 describe-fpga-images --fpga-image-ids <FpgaImageId>"
