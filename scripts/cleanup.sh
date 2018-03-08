# #!/bin/bash

source ${WORKSPACE}/nso-install/ncsrc

echo Stop netsim devices...
cd ${WORKSPACE}/nso-run
ncs-netsim stop

echo Stop NSO...
ncs --stop || true

echo Remove directories...
rm -rf ${WORKSPACE}/nso-install
rm -rf ${WORKSPACE}/nso-run
rm -rf ${WORKSPACE}/python-local
