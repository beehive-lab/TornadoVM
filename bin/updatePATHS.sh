#!/bin/bash 

## Update PATHS in Tornado

file=`ls dist/tornado-sdk/`
echo -e "\n########################################################## "
echo -e "\e[32mTornado build success\e[39m"
echo "Updating PATH and TORNADO_SDK to $file"

cd bin/

echo -e "Binaries: $PWD "
echo -e "Commit  : $(git rev-parse --short HEAD)"

if [ -L bin ]
then
  unlink bin
else
  # Windows cleanup - Mingw copies files during `ln`
  rm -rf bin
fi

if [ -L sdk ]
then
  unlink sdk
else
  # Windows cleanup - Mingw copies files during `ln`
  rm -rf sdk
fi

cd ..

ln -s $PWD/dist/tornado-sdk/$file/bin/ bin/bin
ln -s $PWD/dist/tornado-sdk/$file/ bin/sdk

echo "########################################################## "

