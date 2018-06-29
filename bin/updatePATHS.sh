#!/bin/bash 

## Update PATHS in Tornado

file=`ls dist/tornado-sdk/`
echo -e "\n########################################################## "
echo -e "\e[32mTornado build sucess\e[39m"
echo "Updating PATH and TORNADO_SDK to $file"

cd bin/

echo -e "Binaries: $PWD "
echo -e "Version : $(git rev-parse --short HEAD)"

if [ -L bin ]
then
  unlink bin
fi

if [ -L sdk ]
then
  unlink sdk
fi

cd ..

ln -s $PWD/dist/tornado-sdk/$file/bin/ bin/bin
ln -s $PWD/dist/tornado-sdk/$file/ bin/sdk

echo "########################################################## "

