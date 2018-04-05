
# This is the robot file setting up the environment (netsim, etc.) and making a few simple checks
# if the installtion is successful
#
# We are separating the test environment setup and the actual test cases through the use
# of Robot's "Suite Setup". This has the benefit that no test cases are executed when the
# setup fails.
# Please note that the environment will be removed again after the test run, you can
# comment out the "Suite Teardown" line to keep it

*** Settings ***

# Resource files where we are leveraging generic keywords to build our tests
Library    rasta.RASTA
Resource   rasta.robot

# Variable settings. We can use multiple ways to set variables, but a yaml file is
# preferred as it allows to easily create multiline variables including leading spaces/etc., list
# variables/etc.

Variables   config.yaml

Suite Setup       Setup Environment
# we don't want to tear down the environment as we leverage it
# for the other tests, so the Suite Teardown is commented out
#Suite Teardown    Teardown Environment

*** Variables ***
# Directory where we create the netsim/ncs-run directories to install the 
# netsim environment. If you run this on the machine where you run the
# test, you might as well use the below to use the local directory where you run the 
# test
${WORK_DIR}        %{RASTAHOME}/examples/netsim-setup

*** Test Cases ***

Check Packages and Devices
    use testbed "${testbed}"
    connect to device "${nso}"
    Run NSO command "show devices list" with expected result list "${netsimDevices}"
    Run NSO command "show packages package package-version" with expected result list "${packages}"

*** Keywords ***

Setup Environment
    Log to Console     Bringing up the environment (netsim/ncs), please wait...     no_newline=true
    Create Environment
    Add Service Package
    Apply Base Config
    Log to Console     done

Create Environment
    [tags]      environment  critical
    [Documentation]     Create Environment
    ...     | Setup netsim environment, ncs

    use testbed "${testbed}"
    set command timeout to "300" seconds
    connect to device "${server}"
    Execute command "cd ${WORK_DIR}" on device "${server}"
    Execute command "${createEnvironment_1}" on device "${server}"
    Execute command "${createEnvironment_2}" on device "${server}"
    Execute command "${createEnvironment_3}" on device "${server}"
    connect to device "${nso}"
    Set NSO cli style to "${cli_style}"
    Execute command "request devices sync-from" on device "${nso}"
    Log to Console     ncs & netsim started...       no_newline=true

Add Service Package
    [tags]      environment  critical
    [Documentation]     Add Service Package
    Execute command "cp sw-init-l3vpn.tar ncs-run/packages" on device "${server}"
    Execute command "request packages reload" on device "${nso}"
    Log to Console      service package added...       no_newline=true

Apply Base Config
    [tags]      environment  critical
    [Documentation]     create some initial configuration on the netsim devices
    configure device "${nso}" with config "${deviceBaseConfig}"

Teardown Environment
    [tags]      environment  critical
    Log to Console     Tearing down the environment (netsim/ncs)
    connect to device "${server}"
    Execute command "cd ${WORK_DIR}" on device "${server}"
    Execute command "${deleteEnvironment}" on device "${server}"

