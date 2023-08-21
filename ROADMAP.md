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

* Matchers
  * [Hi] Build a `And`, `Not` and a `Or` matcher
* Actions
  * [Med] Multiple Action (That holds a list of actions, ex: Set a cookie & redirect)
* https
  * [Hi] Handle directly certbot to update https certs
  * [Top] More sane default options to the https stack (actually build a https stack)
* ReverseProxy
  * [Med] Allow a back end to use a provided self-signed certificate
* Misc
  * [Hi] Hot reload of the configuration

## Stability

* [Med] Build a full end-to-end test, hopefully a unit test
* [Hi] Test config file parsing
* [Blocked] Test the https bits

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
