# #!/bin/bash
NSO_BIN=nso-4.3.2.linux.x86_64.installer.bin
NSO_BIN_URL=http://engci-maven-master.cisco.com/artifactory/nso-release/tailf-releases/ncs/${NSO_BIN}

echo Download NSO...
wget -N $NSO_BIN_URL
ls -l

echo Remove possible leftovers from previous runs...
rm -rf ${WORKSPACE}/nso-install
rm -rf ${WORKSPACE}/nso-run

echo Create installation and runtime directories...
mkdir -p ${WORKSPACE}/nso-install
mkdir -p ${WORKSPACE}/nso-run

echo Install NSO...
sh ${WORKSPACE}/${NSO_BIN} ${WORKSPACE}/nso-install --local-install
