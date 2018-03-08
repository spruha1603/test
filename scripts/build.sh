# #!/bin/bash

source ${WORKSPACE}/nso-install/ncsrc

echo Building packages...

cd ${WORKSPACE}
make clean all

echo Creating runtime directory...
ncs-setup --dest ${WORKSPACE}/nso-run

echo Copying packages...
cp -r ${WORKSPACE}/packages ${WORKSPACE}/nso-run

echo Copying templates-store...
cp -r ${WORKSPACE}/template-store ${WORKSPACE}/nso-run

## Adjust the netsim devices required for your tests
echo Creating netsim devices...
cd ${WORKSPACE}/nso-run
ncs-netsim --dir netsim create-network ${WORKSPACE}/nso-run/packages/cisco-iosxr 1 GRACIS

echo "Stop netsim devices in case they are still up from previous runs..."
cd ${WORKSPACE}/nso-run
ncs-netsim stop

echo Start netsim devices...
cd ${WORKSPACE}/nso-run
ncs-netsim start

echo "Stop NSO in case it is still up from previous runs..."
ncs --stop || true

echo Start NSO...
cd ${WORKSPACE}/nso-run
ncs --with-package-reload

echo Load netsim devices to NSO...
cd ${WORKSPACE}/nso-run
ncs-netsim ncs-xml-init > devices.xml
ncs_load -l -m devices.xml && rm -f devices.xml
ncs_load -l -m ../config/tbs.xml

echo "Sync-from (not really needed)..."
echo " request devices sync-from" | ncs_cli -u admin;
