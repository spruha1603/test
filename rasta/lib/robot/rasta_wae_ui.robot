
*** Keywords ***
Login to WAE server "${url}" with credentials "${nso_username}/${nso_password}"
    [Documentation]   Login to NSO server with variables:
    ...
    ...               ${url}
    ...
    ...               ${nso_username}
    ...
    ...               ${nso_password}
    ...
    ...               Before calling this keyword, the above variables should be set.
    visit ${url}
    wait for the page to load via XPath "//input[@name='username']"
    enter "${nso_username}" in the field with XPath "//input[@name='username']"
    enter "${nso_password}" in the field with XPath "//div[contains(@id,'password')]/div/input"
    click on the object with XPath "//div[contains(@id,'password')]/div/input"
    click on the object with XPath "//span[text()='Login']"
    wait for the page to load via XPath "//h4[text()='WAE Models']"


Logout from WAE server
    wait for the page to load via XPath "//li[@title='User']"
    click on the object with XPath "//li[@title='User']"
    wait for the page to load via XPath "//a[@id='logoutBtn']"
    click on the object with XPath "//a[@id='logoutBtn']"
    wait for the page to load via XPath "//button[text()='OK']"
    click on the object with XPath "//button[text()='OK']"
    wait for the page to load via XPath "//input[@name='username']"
