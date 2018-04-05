*** Settings ***
Library    rasta.RASTA
Resource   rasta.robot

*** Variables ***
${url}         http://localhost:8080
${nso_username}     admin
${nso_password}     admin

*** Test Cases ***
Open NSO portal and login
    Start browser
    Sleep  2s
    Login to NSO server
    Sleep  2s

Add something
    Click on button "+"
    Enter "pe2" in input item "Name"
    Enter "127.0.0.1" in input item "Address"
    Enter "10026" in input item "Port"
    Sleep  3s
    #Click on button "Add"
    Click on button "Cancel"

Logout and close browser
    Sleep  3s
    Logout from NSO server
    Sleep  3s
    Close browser

