# #!/bin/bash

export PATH=${WORKSPACE}/python-local/${PYTHON3_DIR}/:$PATH
export PYTHONPATH=${WORKSPACE}/python-local/${PYTHON3_DIR}

echo Activate Python Virtual environment...
cd ${WORKSPACE}/python-local/auto
source bin/activate

#export PYTHONPATH=.:$PYTHONPATH

export RASTAHOME=${WORKSPACE}/rasta
source $RASTAHOME/env.sh

source ${WORKSPACE}/nso-install/ncsrc

echo Start RASTA test...


## Start tests: || true means that process will not stop because of failure.
## Once the tests are done, they can be collected by the robot plugin,
## which will determine success/failure according the plugin configs
cd ${WORKSPACE}/rasta/tests/check-dc-vlan-service
python -m robot *.robot || true

deactivate
