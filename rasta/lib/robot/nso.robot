*** Keywords ***

Connect to NSO via cli
    use testbed "${testbed}"
    connect to device "${nso}"

Show NSO state
    Run NSO command "show ncs-state daemon-status" with expected result "daemon-status started"

Run NSO command "${command}" with expected result "${result}"
    [Documentation]     Runs a specified command on NSO device ${nso} (variable needs to be set somewhere)
    ...     and verifies the result with the passed result string
    ...     The output must contain the exact string given in expected result.
    ...     Regular Expression are not supported here.
    execute command "${command}" on device "${nso}"
    store command "${command}" from device "${nso}" as "output"
    Should Contain     ${output}    ${result}

Run NSO command "${command}" with not expected result "${result}"
    [Documentation]     Runs a specified command on NSO device ${nso} (variable needs to be set somewhere)
    ...     and verifies the result with the passed result string
    ...     The output must contain the exact string given in expected result.
    ...     Regular Expression are not supported here.
    execute command "${command}" on device "${nso}"
    store command "${command}" from device "${nso}" as "output"
    Should Not Contain     ${output}    ${result}

Run NSO command "${command}" with expected result list "${result_list}"
    [Documentation]     Runs a specified command on NSO device ${nso} (variable needs to be set somewhere)
    ...     Then the output variable is compared with the list of exected results.
    ...     The output must contain all of the expected results from the results list.
    ...     List variables are best defined in a yaml file, but can also be defined in a robot file as
    ...        @{NAMES}      joe     jane    bob
    ...     Regular Expression are not supported here.
    execute command "${command}" on device "${nso}"
    store command "${command}" from device "${nso}" as "output"
    :FOR    ${var}  IN  @{result_list}
    \   Log                checking output for value ${var}
    \   Should Contain     ${output}    ${var}


Run NSO command "${command}" and ignore output
    [Documentation]     Runs a command on NSO device ${nso}. The output is ignored.
    execute command "${command}" on device "${nso}"

Get NSO leaf
    [Documentation]   Gets leaf value from NSO resource as keypath (key value pair)
    [Arguments]    ${config}     ${leaf}
    ${command}=    Set Variable    show running-config ${config} | display keypath | include ${leaf}
    execute command "${command}" on device "${nso}"
    store command "${command}" from device "${nso}" as "result"
    [Return]    ${result}

Get NSO device ${leaf} for device ${device}
    [Documentation]   Gets leaf value from device configuarion
    ${deviceconfig}=    Set Variable    devices device ${device} config
    Get NSO leaf    ${deviceconfig}     ${leaf}

Check if device ${device} parameter ${leaf} equals ${expected_value}
    [Documentation]   Compares leaf value taken from device configuarion with expected value
    Get NSO device ${leaf} for device ${device}
    Log    Checking if "${result}" contains "${leaf} ${expected_value}"
    Should Contain     ${result}    ${leaf} ${expected_value}

Get NSO service ${leaf} for service ${service} of type ${service_type}
    [Documentation]   Gets leaf value from service configuarion
    ${deviceconfig}=    Set Variable    services ${service_type} ${service}
    Get NSO leaf    ${deviceconfig}     ${leaf}

Check if ${service_type} service ${service} parameter ${leaf} equals ${expected_value}
    [Documentation]   Compares leaf value taken from service instance configuarion with expected value
    Get NSO service ${leaf} for service ${service} of type ${service_type}
    Log    Checking if "${result}" contains "${leaf} ${expected_value}"
    Should Contain     ${result}    ${leaf} ${expected_value}

Set NSO cli style to "${style}"
    [Documentation]   Sets the NSO CLI style to cisco/juniper (nso device $nso)
    set cli style to "${style}" on device "${nso}"
