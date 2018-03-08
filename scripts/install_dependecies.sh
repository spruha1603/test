# #!/bin/bash

PYTHON3_VERSION=3.4.6
PYTHON3_DIR=Python-${PYTHON3_VERSION}
PYTHON3_FILE=${PYTHON3_DIR}.tgz
PYTHON3_URL=https://www.python.org/ftp/python/${PYTHON3_VERSION}/${PYTHON3_FILE}


echo "workspace = ${WORKSPACE}"
#############################################################################
## Install dependencies, install and setup NSO, compile and load packages  ##
#############################################################################

echo Install python locally...
rm -rf ${WORKSPACE}/python-local
mkdir -p ${WORKSPACE}/python-local
cd ${WORKSPACE}/python-local
wget $PYTHON3_URL
tar zxfv $PYTHON3_FILE
find ${WORKSPACE}/python-local -type d | xargs chmod 0755
cd $PYTHON3_DIR
./configure --prefix=${WORKSPACE}/python-local

echo Compiling python...
make && make install
export PATH=${WORKSPACE}/python-local/${PYTHON3_DIR}/:$PATH
export PYTHONPATH=${WORKSPACE}/python-local/${PYTHON3_DIR}

echo Setup Python Virtual environment...
cd ${WORKSPACE}/python-local
python -m venv auto
cd auto
source bin/activate
export PYTHONPATH=.:$PYTHONPATH

echo Install PyATS
python -m pip install -i http://pyats-pypi.cisco.com/simple --trusted-host pyats-pypi.cisco.com unicon
echo install python modules
python -m pip install -r ${WORKSPACE}/rasta/requirements.txt

python -c "import requests"
echo "import result: $(echo $?)"

deactivate
