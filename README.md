# http-front-server
Java written http server, which can:

* Load balance requests across several backends
* Route requests based on domain name and other attributes
* Password protect parts of your services
* Handle your https stack and compression
* Handle all your http redirections
* And many more to come

## Requirements / Technical bits

* JDK 17
* Might run on GraalVM, has not been tested
* Uses Apache HttpClient 5 library under the hood
* The reverse proxy bits have been hugely influenced (and partly copied and pasted) from [David Smiley's own reverse proxy](https://github.com/mitre/HTTP-Proxy-Servlettiti) 

## Status

* In development.