# Rasta/Robot test with a local netsim installation

This directory contains a sample test case to set up and test a Netsim environment running on a local Mac or a remote unix/linux server. This setup is then used for subsequent example test cases in the parent directory.

## Installation


Please refer to the Installation chapter in [../../docs/README.md]() to install the Rasta environment on a docker container or a vagrant VM.

You can also set up the environment on an existing VM leveraging the ansible playbooks provided in [../../ansible/rasta]()

### Local NSO Installation

In my setup, I am using a local NSO install on my mac. If you have NSO running elsewhere, please define a linux host in testbed.yaml, adapt the ip address/settings of the "ncs" device in the testbed definition in the file testbed.yaml.

To setup NSO in the docker/vagrant environment, you can follow those steps, replacing CEC_USERID by your userid and supplying your CEC password when prompted by wget:

```
cd 
wget --user=CEC_USERID --ask-password --no-check-certificate https://earth.tail-f.com:8443/ncs/nso-4.4.1.4.linux.x86_64.installer.bin

# create local installation of NSO in ~/nso-4414 directory
sh nso-4.4.1.4.linux.x86_64.installer.bin ~/nso-4414 --local-install
source ~/nso-4414/ncsrc

echo "[ -f ~/nso-4414/ncsrc ] && source ~/nso-4414/ncsrc" >> ~/.bash_profile

# Download NEDs
wget --user=CEC_USERID --ask-password --no-check-certificate https://earth.tail-f.com:8443/ncs-pkgs/cisco-ios/4.4.1/ncs-4.4.1-cisco-ios-5.2.7.signed.bin https://earth.tail-f.com:8443/ncs-pkgs/cisco-iosxr/4.4.1/ncs-4.4.1-cisco-iosxr-5.3.1.signed.bin
sh ~/ncs-4.4.1-cisco-ios-5.2.7.signed.bin --skip-verification
sh ~/ncs-4.4.1-cisco-iosxr-5.3.1.signed.bin --skip-verification

# Install NEDs in local install directory, overwriting the
# old ones shipped with NCS
cd $NCS_DIR/packages/neds
rm -rf cisco-ios cisco-iosxr
tar zxf ~/ncs-4.4.1-cisco-ios-5.2.7.tar.gz
tar zxf ~/ncs-4.4.1-cisco-iosxr-5.3.1.tar.gz
cd
```

### Installing Java

The docker and vagrant images don't have java installed. Please install it (on vagrant boxes created by the Rasta ansible playbook, you need to "su - vagrant" with password R4st4 in roder to do "sudo ..")

```
sudo yum install java-1.8.0-openjdk
   or
sudo apt-get install openjdk-8-jre
```

## Preparing your environment

As a first step before you work with any robot/rasta project, you need to set up your environment. You can skip this if you are logged into a VM or container provisioned by the Rasta's ansible install script which has already added the appropriate commands to your ~/.bash_profile and your prompt already looks like this: ```(virtualenv-rasta) [oboehmer@localhost ~]```. If it doesn't, please change to the python virtualenv you have created and execute the env.sh in the RASTA home directory which sets the PYTHONPATH:

```
source ~/virtuanev-rasta/bin/activate
export RASTAHOME=$HOME/rasta
source $RASTAHOME/env.sh
```


The setup in testbed.yaml does an "ssh localhost" to exceute commands, so please add your ssh pubkey into the .ssh/authorized_keys file. In the docker/vagrant environment, please do the following:

```
$ ssh-keygen
[ hitting return to accept all defaults ]

$ cat .ssh/id_rsa.pub >> .ssh/authorized_keys

# Double-check that you can do "ssh localhost" without being asked for a password:
$ ssh localhost echo success
success
$

```
No you are all set to run the test

## Running the test


```
$ cd $RASTAHOME/examples/netsim-setup/

```

The test file **env-setup.robot** sets up a local NSO run directory, netsim devices (2 IOS, 2 XR), copies a simple service package to it and performs some basic checks. 



Verify the ${WORK_DIR} settings in the env-setup.robot file which points to the directory on the server (or the localhost) where we will create the netsim/ncs-run NSO runtime directories. 


Now execute the test:

```
robot env-setup.robot 
```

If all goes well, you are left with a running and working NSO environment, and you can execute the other examples which will use the setup.

Please check the file **log.html** which robot creates for test documentaion and troubleshooting.

We have seen timeout issues in some scenarios (especially when executing "sync-from"). 
In case some things fail and you need to start over, please clean up the environment first as the env-setup.robot expects a "clean" state without any prior local installation in the directory:

```
sh cleanup-env.sh
```



