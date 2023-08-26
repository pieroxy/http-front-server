In this file you will find what is next for nullbird-hfs

## Performance

* Raw performance
  * [Hi] Parametrize the AsyncClient
  * [Hi] Parametrize tomcat (allow chars in URLs, etc)
  * [Low] Support Brotli
  * [Later] Support zstd (if it ever becomes supported by browsers)
  * [Med] Support for HTTP/2 in the server
  * [Low] Support for HTTP/2 in the reverse proxy

## Functionality

* [Hi] Write more tests (actions & matchers) - namely to test all configurations
* Matchers
* Actions
  * [Med] Multiple Action (That holds a list of actions, ex: Set a cookie & redirect)
* https
  * [Hi] Handle directly certbot to update https certs
  * [Top] More sane default options to the https stack
* ReverseProxy
  * [Med] Allow a back end to use a provided self-signed certificate
* Misc
  * [Hi] Hot reload of the configuration

## Stability

* [Med] Build a full end-to-end test, hopefully a unit test
* [Hi] Test config file parsing
* [Blocked] Test the https bits

## Documentation

* [Top] Documentation for the actions (javadoc).
* [Top] Write a startup guide.
* [Top] Write some CLI handling, with a default conf.
* [Top] Write a startup guide with all actions and rules.

## Far away in the future

* [Later] Allow users to plug their own matchers / actions
* [Later] Nice UI to configure the thing

## Legend

* Priority:
  * [Top] Task is on top of the list
  * [Hi] High priority
  * [Med] Medium priority
  * [Low] Low priority
  * [Later] Will eventually be considered later
  * [Blocked] Blocked by another task
