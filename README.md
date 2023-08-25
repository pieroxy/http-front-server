# http-front-server

Java written http server, which can:

* Load balance requests across several backends
* Route requests based on domain name and other attributes
* Password protect parts of your services
* Handle your https stack and compression
* Handle all your http redirections
* Can hold requests while your backend restarts
* And many more to come

## Requirements / Technical bits

* Has been built with JDK 17
* Uses Apache Tomcat 10
* Uses Apache HttpClient 5 library under the hood for reverse proxying
* The reverse proxy bits have been hugely influenced (and partly copied and pasted) from [David Smiley's own reverse proxy](https://github.com/mitre/HTTP-Proxy-Servlettiti) 

## Status

* Stable.
* Features in development.

## Links

* [The roadmap](doc/ROADMAP.md)
* Documentation to come