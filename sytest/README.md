# TIM Referenzimplementierung - Messenger-Proxy - SyTest

SyTest plugin to run tests against a messenger-proxy in front of a synapse server.

## Usage
From the root directory do the following steps:
1. The application should be built at least once because the build artifact is used to create the docker image. This can be done like so:
    ```shell
    $ ./mvnw clean install
    ```

2. ```shell
    docker build -f sytest/Dockerfile --build-arg "JAR_FILE=/target/mp-backend-*-jar-with-dependencies.jar" --progress=plain .
    ```

### tap_to_html.pl
The `tap_to_html.pl` script is can be used to generate HTML formatted test reports from SyTests Tap output format.
It requires Perl and the `TAP::Formatter::HTML` module to be installed, which can be done as following:
```shell
cpan install TAP::Formatter::HTML
```
#### Usage
```shell
perl scripts/tap_to_html.pl <PATH_TO_TAP_FILE> <PATH_TO_OUTPUT_FILE>
```
## Override Tests
The customized files can be stored under the path sytests/lib/SyTest/OverrideTests.
For overriding a test, it is necessary to insert a copy line in file /sytest/scripts/messenger_proxy_sytest.sh at line 175.
Please note that it should only be used in exceptional cases.
