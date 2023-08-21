In this file you will find what is next for nullbird-hfs

## Performance

* Work against thread starvation, that should minimize footprint at high load while allowing higher loads:
  * Use nio servlets: https://www.oracle.com/webfolder/technetwork/tutorials/obe/java/HTML5andServlet31/HTML5andServlet%203.1.html
* Raw performance
  * Support Brotli
  * Support zstd (if it ever becomes supported by browsers)
  * Support for HTTP/2 in the server
  * Support for HTTP/2 in the reverse proxy

## Functionality

* Matchers
  * Build a `And`, `Not` and a `Or` matcher
  * Build a `ContainsCookie` matcher
* Actions
* https
  * Handle directly certbot to update https certs
  * More sane default options to the https stack (actually build a https stack)
* ReverseProxy
  * Allow a back end to use a provided self-signed certificate
* Misc
  * Hot reload of the configuration

## Stability

* Build a full end-to-end test, hopefully a unit test
* Test config file parsing
* Test the https bits

## Far away in the future

* Allow users to plug their own matchers / actions
* Nice UI to configure the thing