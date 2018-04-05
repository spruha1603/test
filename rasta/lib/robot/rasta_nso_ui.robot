*** Settings ***
Documentation    Resource file is for NSO UI automation.
...              It provides keywords that drives a browser via Selenium.

*** Keywords ***
Access service "${a}/${b}"
    [Documentation]   Accesses the service module in the NSO UI
    wait for the page to load via XPath "//span[contains(text(),'${a}:${b}')]"
    click on the object with XPath "//span[contains(text(),'${a}:${b}')]"

Click on tab "${tab}"
    [Documentation]   Select the named tab in the NSO UI
    wait for the page to load via XPath "//span[text()='${tab}']"
    click on the object with XPath "//span[text()='${tab}']"

Click on button "${button}"
    [Documentation]   Example button:
    ...
    ...               ..  image:: images/button.png
    ...
    ...               Example keywords:
    ...               **Click on button "+"**
    wait for the page to load via XPath "//button[contains(text(),'${button}')]"
    click on the object with XPath "//button[contains(text(),'${button}')]"

Click on filter button
    [Documentation]   ..  image:: images/filter_button.png
    wait for the page to load via XPath "//button/span[contains(@class,'glyphicon-filter')]"
    click on the object with XPath "//button/span[contains(@class,'glyphicon-filter')]"

Enter "${value}" in input field "${field}"
    [Documentation]   Example input field:
    ...
    ...               ..  image:: images/input_field.png
    ...
    ...               Example keyword: **Enter "123" in input field "id"**
    enter "${value}" in the field with XPath "//label[text()='${field}']/../../../tr[2]/td/div/div/div/input"

Enter "${value}" in input item "${field}"
    [Documentation]  Example input item:
    ...
    ...               ..  image:: images/input_item.png
    ...
    ...               Exampe keyword: **Enter "AR1" in input item "arpi"**
    wait until object is visible via XPath "//label[text()='${field}']/../input"
    enter "${value}" in the field with XPath "//label[text()='${field}']/../input"

Select "${value}" from input field "${field}"
    [Documentation]      Example input field:
    ...
    ...                  ..  image:: images/input_field_dropdown.png
    ...
    ...                  Example: **Select "Ethernet" from input field "interface-type"**
    wait for the page to load via XPath "//label[text()='${field}']/../../../tr[2]/td/div/div[@role='presentation']/input"
    click on the object with XPath "//label[text()='${field}']/../../../tr[2]/td/div/div[@role='presentation']/input"
    wait until object is visible via XPath "//div[text()='${value}']"
    click on the object with XPath "//div[text()='${value}']"
    click on the object with XPath "//body"

Select "${value}" from dropdown named "${field}"
    [Documentation]   Example dropdown:
    ...
    ...               ..  image:: images/dropdown.png
    ...
    ...               Example: **Select "GRTSANEM5" from dropdown "device"**
    wait for the page to load via XPath "//select[@name='${field}']"
    select element "${value}" from list with XPath "//select[@name='${field}']", wait 0

Select "${value}" from input dropdown "${field}"
    wait for the page to load via XPath "//label[text()='${field}']/../../../tr[2]/td/div/div/table//select"
    select element "${value}" from list with XPath "//label[text()='${field}']/../../../tr[2]/td/div/div/table//select", wait 0

Select "${value}" from choice "${field}"
    [Documentation]   Example input choice:
    ...
    ...               ..  image:: images/choice.png
    ...
    ...               Example: **Select "Ge" from choice "Choice - speed"**
    wait for the page to load via XPath "//div[text()='${field}']/../div[2]//select"
    select element "${value}" from list with XPath "//div[text()='${field}']/../div[2]//select", wait 0

Access module "${a}/${b}"
    [Documentation]   Access the module of the Models 'Modules' section
    wait for the page to load via XPath "//span[text()='${a}']"
    click on the object with XPath "//span[text()='${a}']"
    click on the object with XPath "//span[text()='${a}']/../../div/span"
    click on the object with XPath "//span[text()='${b}']"

Click on hamburger
    [Documentation]   Access the 'Models' window in the NSO UI via the hamburger icon
    click on the object with XPath "//div[@class='hamburger']"

Close popup
    [Documentation]   Close the popup window by clicking on the X icon on the top left of the popup window
    click on the object with XPath "//span[contains(@class,'close-icon')]|//span[contains(@class,'CloseIcon')]"

Login to NSO server "${url}" with credentials "${nso_username}/${nso_password}"
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
    wait for the page to load via XPath "//a[@id='ncs-nav-user']"

Login to NSO server
    Login to NSO server "${url}" with credentials "${nso_username}/${nso_password}"

Click on link "${text}"
    [Documentation]    Click on the HTML link on the page
    wait for the page to load via XPath "//a[text()='${text}']"
    click the object with visible XPath "//a[text()='${text}']"

Click on commit dropdown
    click on the object with XPath "//button[@aria-label='Commit']"

Wait for commit to finish
    [Documentation]     Wait for the commit button to change to white color
    wait until background color of object with XPath "//button[text()='Commit']" to match "#ffffff", check every 3 seconds, max 30 times

Logout from NSO server
    wait for the page to load via XPath "//ul[contains(@class,'navbar-right')]/li/a"
    click on the object with XPath "//ul[contains(@class,'navbar-right')]/li/a"
    wait for the page to load via XPath "//a[contains(text(),'Logout')]"
    click on the object with XPath "//a[contains(text(),'Logout')]"
    wait for the page to load via XPath "//input[@name='username']"

Click on Actions dropdown
    wait for the page to load via XPath "//span[contains(text(),'Actions')]/../..//span[@class='caret']"
    click on the object with XPath "//span[contains(text(),'Actions')]/../..//span[@class='caret']"

Click on "${action}" from Action dropdown
    wait for the page to load via XPath "//a//span[contains(text(),'${action}')]"
    click on the object with XPath "//a//span[contains(text(),'${action}')]"
