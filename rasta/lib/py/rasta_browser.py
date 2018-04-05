import os
import re
from time import sleep
from random import choice, randint

from robot.api.deco import keyword
from robot.libraries.BuiltIn import BuiltIn

from selenium import webdriver
from selenium.common.exceptions import TimeoutException, WebDriverException, NoSuchElementException, ElementNotVisibleException
from selenium.webdriver.support.ui import WebDriverWait, Select
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.common.action_chains import ActionChains


class RastaBrowser(object):
    ROBOT_LIBRARY_SCOPE = "TEST SUITE"

    def start_browser(self, browser='firefox'):
        url = None
        session_id = None
        if os.path.isfile('session.txt'):
            with open('session.txt', 'r') as f:
              session = f.read().splitlines()
            f.close()
            if len(session) > 1:
              url = session[0]
              session_id = session[1]
            elif session:
              url = session[0]
            else:
              url = None

        if not url:
            raise ValueError('session.txt required with selenium server url')

        if browser == 'firefox':
            profile = webdriver.FirefoxProfile()
            #set firefox preferences
            profile.set_preference("network.proxy.type", 0)
            #profile.set_proxy(context.proxy.selenium_proxy())
            profile.set_preference("app.update.enabled", False)
            profile.native_events_enabled = True
            profile.set_preference('extensions.firebug.showFirstRunPage', False)

            desired_capabilities = webdriver.DesiredCapabilities.FIREFOX
            desired_capabilities["marionette"] = True

            browser = webdriver.Remote(
                browser_profile=profile,
                desired_capabilities=desired_capabilities,
                command_executor=url,
                session_id=session_id,
            )
            try:
                browser.get_window_size()
                # If get_window_size fails, the session_id is invalid, start a new sesssion
            except WebDriverException:
                browser = webdriver.Remote(
                    browser_profile=profile,
                    desired_capabilities=desired_capabilities,
                    command_executor=url,
                )

        elif browser == 'chrome':
            chrome_options = webdriver.ChromeOptions()
            chrome_options.add_experimental_option('prefs', {
                'credentials_enable_service': False,
                'profile': {
                    'password_manager_enabled': False
                }
            })

            # if self.use_proxy:
            #     chrome_options.add_argument("--proxy-server={0}".format(context.proxy.proxy))
            desired_capabilities = webdriver.DesiredCapabilities.CHROME
            desired_capabilities=chrome_options.to_capabilities()

            browser = webdriver.Remote(
                command_executor=url,
                desired_capabilities=desired_capabilities,
                session_id=session_id,
            )
            try:
                browser.get_window_size()
            except WebDriverException:
                browser = webdriver.Remote(
                    command_executor=url,
                    desired_capabilities=desired_capabilities,
                )
        else:
            raise ValueError("Unsupported browser, use 'firefox' or 'chrome'")

        self.browser = browser

        session_id = self.browser.session_id            #'4e167f26-dc1d-4f51-a207-f761eaf73c31'

        with open('session.txt', 'w') as f:
          f.write("{0}\n{1}\n".format(url, session_id))
        f.close()

    def close_browser(self):
        self.browser.quit()

    @keyword('visit ${site}')
    def browser_get(self, site):
        try:
            self.browser.get(site)
        except WebDriverException as e:
            error_log = str(e)
            raise

    @keyword('type "${search}" in the input with name "${inputname}"')
    def type_in_input_with_name(self, search, inputname):
        elem = self.browser.find_element_by_name(inputname)
        elem.clear()
        elem.send_keys(search)
        sleep(1)

    @keyword('click on a random item from list with XPath "${xpath}"')
    def click_on_random_item_from_list_with_xpath(self, xpath):
        try:
            elem = self.browser.find_elements_by_xpath(xpath)
            count = len(elem)
            item = elem[randint(0, count-1)]
            try:
                item.click()
            except Exception as e:
                self.error_log = str(e)
                raise
        except Exception as e:
            self.error_log = str(e)
            raise

    @keyword('click on the object with XPath "${xpath}"')
    def click_on_object_with_xpath(self, xpath):
        try:
            elem = self.browser.find_element_by_xpath(xpath)
            self.browser.execute_script("return arguments[0].scrollIntoView();", elem)
            elem.click()
        except NoSuchElementException as e:
            msg = str(e).splitlines()[0]
            self.error_log = msg
            raise NoSuchElementException(msg)
        except Exception as e:
            self.error_log = str(e)
            raise

    @keyword('wait for the page to load via XPath "${xpath}"')
    def wait_for_page_to_load_via_xpath(self, xpath):
        timeout = 30
        try:
            elem = WebDriverWait(self.browser, timeout).until(
                EC.presence_of_element_located((By.XPATH, xpath))
            )
        except TimeoutException as e:
            self.error_log = "Timeout waiting for %s" % xpath
            raise
        except Exception as e:
            self.error_log = str(e)
            raise

    @keyword('enter "${text}" in the field with XPath "${xpath}"')
    def enter_input_in_field_with_xpath(self, text, xpath):
        try:
            elem = self.browser.find_element_by_xpath(xpath)
            #elem.clear()
            elem.send_keys(text)
        except NoSuchElementException as e:
            msg =  str(e).splitlines()[0]
            self.error_log = msg
            raise NoSuchElementException(msg)
        except Exception as e:
            self.error_log = str(e)
            raise

    @keyword('select element "${selected}" from list with XPath "${xpath}", wait ${wait:\d+}')
    def select_element_from_list_with_xpath(self, selected, xpath, wait):
        try:
            elem = self.browser.find_element_by_xpath(xpath)
            s = Select(elem)
            s.select_by_visible_text(selected)
            sleep(int(wait))
        except NoSuchElementException as e:
            self.error_log = str(e).splitlines()[0]
            raise
        except Exception as e:
            self.error_log = str(e)
            raise

    @keyword('wait until object is visible via XPath "${xpath}"')
    def wait_until_object_is_visible_via_xpath(self, xpath):
        timeout = 30
        try:
            element_present = EC.visibility_of_element_located((By.XPATH, xpath))
            WebDriverWait(self.browser, timeout).until(element_present)
        except TimeoutException as e:
            msg = "Timeout waiting for %s" % xpath
            self.error_log = msg
            raise TimeoutException(msg)
        except Exception as e:
            self.error_log = str(e)
            raise

    @keyword('click the object with visible XPath "${xpath}"')
    def click_object_with_visible_xpath(self, xpath):
        elements = self.browser.find_elements_by_xpath(xpath)
        for elem in elements:
            if elem.is_displayed():
                elem.click()
                break

    @keyword('hide objects with XPath "${xpath}"')
    def hide_tooltips(self, xpath):
        elements = self.browser.find_elements_by_xpath(xpath)
        for elem in elements:
            if elem.is_displayed():
                self.browser.execute_script("return arguments[0].style.display = 'none';", elem)

    @keyword('wait until background color of object with XPath "${xpath}" matches "${color}", check every ${wait:\d+} seconds, max ${times:\d+} times')
    def find_color_of_object_with_xpath(self, xpath, color, wait, times):
        tries = int(times)
        while True:
            tries -= 1
            try:
                elem = self.browser.find_element_by_xpath(xpath)
                rgb = elem.value_of_css_property('background-color')
                r,g,b = map(int, re.search(r'rgb\((\d+),\s*(\d+),\s*(\d+)', rgb).groups())
                value = '#%02x%02x%02x' % (r, g, b)
                if value == color:
                    break
                sleep(int(wait))
            except Exception as e:
                self.error_log = str(e)
                raise
            if not tries:
                msg = "Too many retries - expected %s, actual %s" % (color, value)
                self.error_log = msg
                raise AssertionError(msg)

    @keyword('click on location of element with XPath "${xpath}"')
    def click_on_location_of_xpath(self, xpath):
        """ Move the mouse to the location of the element and click.
        This step does not work with Firefox, but it works with Chrome
        """
        elem = self.browser.find_element_by_xpath(xpath)
        action = ActionChains(self.browser).move_to_element(elem).click()
        action.perform()

    @keyword('click on location offset "${offset}" of element with XPath "${xpath}"')
    def click_on_location_of_xpath(self, offset, xpath):
        """ Move the mouse to the location with offset x,y of the element and click.
        This step does not work with Firefox, but it works with Chrome
        """
        x, y = offset.split(',')
        elem = self.browser.find_element_by_xpath(xpath)
        action = ActionChains(self.browser).move_to_element_with_offset(elem, x, y).click()
        action.perform()

    @keyword('we save the text with XPath "${xpath}" as "${variable}"')
    def store_xpath_variable(self, xpath, variable):
        """ Store the text from an XPath in a variable
        """
        elem = self.browser.find_element_by_xpath(xpath)
        BuiltIn().set_suite_variable('${%s}' % variable, elem.text)

