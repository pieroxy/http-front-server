# nullbird `http-front-server` documentation

## Installation

`nullbirf-hfs` doesn't need any installation. Just download the JAR file from any release, create a config file
and run the program:

```sh
java -jar nullbird-hfs-core-1.2.3.jar proxy-conf.json
```

### Pre requisites

`nullbirf-hfs` need Java 17 or greater. You can see your java version with `java -version`. 
To install Java 17, you can either :

* Download it from your store (Linux)
  * On Ubuntu you can get it through `sudo apt install openjdk-17-jre`
* Download it from the [official distros](https://openjdk.org/install/)

### The config file

The program comes bundled with a template of a config file. In order to use it, run the program with the following 
parameter:

```shell
java -jar nullbird-hfs-core-1.2.3.jar --save-config-template template.json
```

Then, have a look at the file `template.json` and tweak it to fit your needs.

### How to listen to port `80` and `443` ?

On Linux, you cannot listen to a port below 1024 without doing something special. You can:

#### Run the program as root.

This is discouraged. Root should be reserved for the system.

#### Use `authbind`

Install `authbind` and run the following as `root`:

```shell
touch /etc/authbind/byport/80 /etc/authbind/byport/443
chown <user>:<group> /etc/authbind/byport/80 /etc/authbind/byport/443
chmod 700 /etc/authbind/byport/80 /etc/authbind/byport/443
```

This way, the user `<user>` will be allowed to run programs that listen to the ports `80` and `443`.
You then need to launch hfs with the following command:

```shell
authbind java -jar nullbird-hfs-core-1.2.3.jar proxy-conf.json
```

#### Use `iptables` to redirect ports

Run the following as `root`:

```shell
/sbin/iptables -A FORWARD -p tcp --destination-port 443 -j ACCEPT
/sbin/iptables -t nat -A PREROUTING -j REDIRECT -p tcp --destination-port 443 --to-ports 8443
/sbin/iptables -A FORWARD -p tcp --destination-port 80 -j ACCEPT
/sbin/iptables -t nat -A PREROUTING -j REDIRECT -p tcp --destination-port 80 --to-ports 8080

# below needed for local access
/sbin/iptables -t nat -I OUTPUT -p tcp -o lo --dport 80 -j REDIRECT --to-ports 8080
/sbin/iptables -t nat -I OUTPUT -p tcp -o lo --dport 443 -j REDIRECT --to-ports 8443
```

Then, all requests to the port `80` of your host will be forwarded to port `8080` and all requests
to the port `443` to the port `8443`. You can then run `nullbird-hfs` on ports http `8080` and https `8443`.

The drawback of this method is that both ports `8080` and `8443` are opened to the outside.

