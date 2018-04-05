# This test case brings up a service through REST (based on the setup in ../netsim-setup)
# and uses different ways to check for the results to demonstrate some approaches
#

*** Settings ***

# In addition to the core rasta libraries which are included as part of the below
# ../../lib/robot/nso.robot resource file we need additional ones for the keywords implemented
# in this test.
# project-specific libraries can and should be kept in the "lib/" subdirectory
#
#   rasta.RASTA       Our core rasta libraries
#   OperatingSystem   provides some useful keywords to interact with the OS (like to run a command
#                     or to create directories)
#   lib.config_compare.CONFIG	  config diff implementation (we might add this as core Rasta lib)
#
Library    rasta.RASTA
Library    OperatingSystem
Library    lib.config_compare.CONFIG

# Resource files where we are leveraging generic keywords to build our tests, in addition
# to the ones exposed in the core rasta libraries
Resource   rasta.robot

# Variable settings. We can use multiple ways to set variables, but a yaml file is
# preferred as it allows to easily create multiline variables including leading spaces/etc., list
# variables/etc.
Variables   config.yaml

*** Test Cases ***

Setup L3VPN service
    Load Topology and connect to devices
    Sync NSO device config
    Create Interfaces
    Retrieve pre-service device configurations
    Create Service
    Retrieve post-service device configurations

Verify Service through Config Diff - Method 1
    Compare config "${cfgdir}/before/ce0" to "${cfgdir}/after/ce0" expect "${diffExpectedAdd.ce0}" to be added and "${diffExpectedRemoved.ce0}" removed

Verify Service through Config Diff - Method 2a
    Use ciscoconfdiff to compare ios configs "${cfgdir}/before/ce0" and "${cfgdir}/after/ce0"
    Compare latest ciscoconfdiff with reference diffs in files "${cfgdir}/expected/ce0-removed" and "${cfgdir}/expected/ce0-added"

Verify Service through Config Diff - Method 2b
    Use ciscoconfdiff to compare ios configs "${cfgdir}/before/ce0" and "${cfgdir}/after/ce0"
    Compare latest ciscoconfdiff with reference diffs in "${diffCiscoCompareExpectedRemoved.ce0}" and "${diffCiscoCompareExpectedAdded.ce0}"

Verify Service through NSO device config check
    Run NSO command "${diffCheckCommand}" with expected result list "${diffExpected}"

Remove Service
    Delete Service

*** Keywords ***
Load Topology and connect to devices
    [Tags]  setup
    use testbed "${testbed}"
    connect to device "${nso}"

    # In this example we are leveraging a netsim environment where
    # the devices-under-test are created dynamically. In order to interact with them
    # we have created a kewyord to add them dynamically to the test environment
    #
    Add devices "ce0;ce1;pe0;pe1" from NSO "${nso}" to testbed with credentials "admin"/"admin"
    connect to device "ce0"
    connect to device "ce1"
    #Note: pyats/unicon has issues connecting to a netsim xr device, so only considering ios
    #until we find a solution. this is only an issue with netsim, connecting to real devices
    #works just fine

Sync NSO device config
    Set NSO cli style to "${cli_style}"
    execute command "devices sync-from" on device "${nso}"

Retrieve pre-service device configurations
    Create Directory    ${cfgdir}
    Create Directory    ${cfgdir}/before
    Create Directory    ${cfgdir}/after
    Empty Directory     ${cfgdir}/before
    Empty Directory     ${cfgdir}/after
    execute command " show running | nomore" on device "ce0"
    store command " show running | nomore" from device "ce0" as "tmpval"
    Create File         ${cfgdir}/before/ce0     ${tmpval}

Create Interfaces
    [Documentation]   Creates Interfaces on netsim devices
    [Tags]  infra
    via NSO REST API configure device "${nso}" at URI "${interfacesUri}" using "PATCH" with "xml" payload "${interfacesPayload}"

Create Service
    [Documentation]   Creates a service instance on NSO via REST
    [Tags]  service
    via NSO REST API configure service on device "${nso}" with "xml" payload "${createPayload}"

Retrieve post-service device configurations
    execute command " show running | nomore" on device "ce0"
    store command " show running | nomore" from device "ce0" as "tmpval"
    Create File         ${cfgdir}/after/ce0     ${tmpval}


Delete Service
    [Documentation]   Delete a service instance on NSO via REST
    [Tags]  service
    via NSO REST API delete service on device "${nso}" with "xml" payload "${createPayload}"
