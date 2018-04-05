
# Testing GUI using Browsers driven by selenium


**Python selenium client library**

The RASTA selenium client needs to support session re-use. This is a fork with the fix to support this.

Install python-selenium

This version of python selenium supports session re-use.

    pip install https://wwwin-gitlab-sjc.cisco.com/svs/python-selenium/raw/master/selenium-3.3.1.tar.gz


## Software requirements for user desktop

On the user desktop, selenium server will run and a browser will be controlled via a browser specific driver.

**Java**

Ensure you have java installed:

    java -version


**Selenium server**

Download selenium standalone server:

http://www.seleniumhq.org/download/


**Browser drivers**

Download Geckdriver or Chromedriver:

https://github.com/mozilla/geckodriver/releases

https://sites.google.com/a/chromium.org/chromedriver/


Unzip the geckodriver/chromedriver, ensure you have the executable in the same directory as the selenium server .jar file.


## Prepare to run robot scripts

**Run selenium server**

    java -jar selenium-server-standalone-3.4.0.jar


Check if you have a TCP service listening on port 4444:

    netstat -an | grep 4444
    tcp46      0      0  *.4444                 *.*                    LISTEN     


On the server where the robot scripts runs, create a file called 'session.txt' 
that has the following url on the first line:

    http://localhost:4444/wd/hub

The 'localhost' should refer to the host where the selenium server is running (does not have to be on the same machine). If you are running selenium server on some other machine, make sure you have connectivity to the TCP port (default: 4444)

Edit nso-ui-example.robot and check if the NSO url and credentials are correct.

Ensure PYTHONPATH is setup correctly (so it can find the rasta libs)

## Run example

Note: if you want to use chrome, update line 12 for the nso-ui-example script and add 'chrome' (lower case) as argument (at least two spaces between the keyword and argument)

    robot nso-ui-example.robot


![ui-example](https://wwwin-github.cisco.com/AS-Community/RASTA/blob/master/examples/nso-ui/nso-ui-example.gif)


