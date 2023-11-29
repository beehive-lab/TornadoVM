#!/usr/bin/env bash

configurationFilePath=$1
directoryBitstream=$2
entryPoint=$3

export S3_BUCKET=$(grep -w "AWS_S3_BUCKET" ${configurationFilePath} | awk -F'[[:space:]]*=[[:space:]]*' '{print $2}')
export S3_DCP_KEY=$(grep -w "AWS_S3_DCP_KEY" ${configurationFilePath} | awk -F'[[:space:]]*=[[:space:]]*' '{print $2}')
export S3_LOGS_KEY=$(grep -w "AWS_S3_LOGS_KEY" ${configurationFilePath} | awk -F'[[:space:]]*=[[:space:]]*' '{print $2}')
$VITIS_DIR/tools/create_vitis_afi.sh -xclbin=${directoryBitstream}/${entryPoint}.xclbin -s3_bucket="$S3_BUCKET" -s3_dcp_key="$S3_DCP_KEY" -s3_logs_key="$S3_LOGS_KEY"

sudo mv ${entryPoint}.awsxclbin ${directoryBitstream}/${entryPoint}.awsxclbin
sudo mv to_aws ${directoryBitstream}/to_aws
cd ${directoryBitstream}
sudo rm ${entryPoint}
sudo ln -s ${entryPoint}.awsxclbin ${entryPoint}
cd ../

cat *_afi_id.txt
echo "Now you need to wait till the AWS FPGA Image (AFI) is ready for execution. Check:"
echo "aws ec2 describe-fpga-images --fpga-image-ids <FpgaImageId>"
