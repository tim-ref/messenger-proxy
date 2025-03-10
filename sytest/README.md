# TIM Referenzimplementierung - Messenger-Proxy - SyTest

SyTest plugin to run tests against a messenger-proxy in front of a synapse server.

## Running Sytest tests

From the repository root directory do the following steps:

1. The application should be built at least once because the build artifact is used to create the docker image. This can
   be done like so:
   ```shell
   ./mvnw clean install -Dmaven.test.skip=true
   ```

2. ```shell
   docker build -f sytest/Dockerfile --build-arg "JAR_FILE=/target/mp-backend-*-jar-with-dependencies.jar" --progress=plain .
   ```
   This command will print a link to the container's build logs including test results when it finishes.

### HTML reports

The script at [scripts/tap_to_html.pl](scripts/tap_to_html.pl) generates HTML-formatted test reports from SyTest's own, Tap-formatted reports.
It requires Perl and the `TAP::Formatter::HTML` module to be installed, which can be done like this:

```shell
cpan install TAP::Formatter::HTML
```

#### Usage

```shell
perl scripts/tap_to_html.pl <PATH_TO_TAP_FILE> <PATH_TO_HTML_FILE>
```

## Overriding tests

Place modified test files at [sytest/lib/SyTest/OverrideTests](lib/SyTest/OverrideTests), and insert a copy command into [sytest/scripts/messenger_proxy_sytest.sh](scripts/messenger_proxy_sytest.sh), line 175, for each test file.
Please note that you should only override tests in exceptional cases.
