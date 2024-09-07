Android HTTP traffic Proxy setting tool
=============


This tool is a proxy configuration tool that takes advantage of Android VPNService feature. 
Only the communication from the specified application can be acquired.

## Supported Proxy Protocols

This application supports two proxy protocol types: `HTTP` and `SOCKS5`. By default, if the protocol is not specified, `HTTP` is assumed.

### Supported Input Formats

The application can parse and handle various proxy configurations in the following formats:

1. **IP Address and Port Only (HTTP is assumed by default)**:
   - Example: `10.10.10.1:8080`

2. **Explicit HTTP Protocol**:
   - Example: `http://10.10.10.1:8080`

3. **Explicit SOCKS5 Protocol**:
   - Example: `socks5://10.10.10.1:8080`

4. **HTTP with Username and Password**:
   - Example: `http://username:password@10.10.10.1:8080`

5. **SOCKS5 with Username and Password**:
   - Example: `socks5://username:password@10.10.10.1:8080`

### Examples of Supported Inputs

| Input Format                                         | Protocol Type |
|------------------------------------------------------|---------------|
| `10.10.10.1:8080`                                    | HTTP (default)|
| `http://10.10.10.1:8080`                             | HTTP          |
| `socks5://10.10.10.1:8080`                           | SOCKS5        |
| `http://username:password@10.10.10.1:8080`           | HTTP          |
| `socks5://username:password@10.10.10.1:8080`         | SOCKS5        |

### Handling Invalid Formats

If an invalid proxy format is provided, the application will display a notification (via a Toast) informing the user of the incorrect format.

## How to use it

When you start the ProxyMe application, the following screen will be launched.

![Tun Proxy](images/1.png)

* Proxy address (ipv4:port)
  * Specify the destination proxy server in the format **IPv4 address:port number**.
    The IP address must be described in IPv4 format.

* [Start] button
  * Start the VPN service.
* [Stop] button
  * Stop the VPN service.

![Tun Proxy](images/2.png)

## Menu

![Menu](images/3.png)

Application settings can be made from the menu icon at the top of the screen.

### Settings

Configure VPN service settings.

![Menu Settings](images/3.png) ⇒ ![Menu Settings](images/4.png)

There are two modes, Disallowed Application and Allowed Application, but you can not specify them at the same time.
Because of this you will have to choose whether you want to run in either mode.
The default is **Disallowed Application** selected.

* Disallowed Application
  * Select the application you want to exclude from VPN service.
    The selected application will no longer go through VPN service and behave the same as if you do not use VPN.

* Allowed Application
  * Select the application for which you want to perform VPN service.
    The selected application will now go through VPN service.
    Applications that are not selected behave the same as when not using VPN.
    In addition, if none of them are selected, communication of all applications will go through VPN.

* Clear all selection
  * Clear all selections of Allowed / Disallowed application list.



### About
Display application version

## Operating environment

* Android 5.0 (API Level 21) or later

### Build APK
 gradlew build


## Acknowledgments

1. https://github.com/raise-isayan/TunProxy
2. https://github.com/ys1231/appproxy


## Development environment

* AndroidStudio (https://developer.android.com/studio/index.html)
