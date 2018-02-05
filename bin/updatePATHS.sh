#!/bin/bash 

## Update PATHS in Tornado

file=`ls dist/tornado-sdk/`
echo " ##################################################### "
echo "Updating PATH and TORNADO_SDK to $file"
echo " ##################################################### "

cd bin/

echo $PWD 
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

