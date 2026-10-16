# experiment-jvm-server

Amplitude Experiment Server-side SDK for Java and Kotlin

## Getting started

See the [Documentation](https://www.docs.developers.amplitude.com/experiment/sdks/jvm-sdk) for getting started.

## Remote evaluation HTTP transport

The default remote evaluation client sends HTTP/2 pings every 10 seconds. If a
connection stops responding to pings, OkHttp closes it so subsequent fetches can
open a new connection instead of waiting for the operating system's TCP timeout.
A request timeout alone cancels an HTTP/2 stream and may leave its connection reusable.

`fetchTimeoutMillis` applies to each attempt, including time waiting in the HTTP
dispatcher and reading the response. The default is 500 ms; zero disables this
timeout. Each retry has its own timeout, in addition to any configured backoff.

Timeout completion uses SDK-owned daemon workers, separate from the deadline
scheduler and Java's common pool. Blocking synchronous continuations can keep
these workers occupied; use asynchronous continuations with your own executor
for blocking application work.

To customize the transport in Java (the same builder is available in Kotlin):

```java
OkHttpClient httpClient = new OkHttpClient.Builder()
    .pingInterval(10, TimeUnit.SECONDS)
    .build();
RemoteEvaluationConfig config = RemoteEvaluationConfig.builder()
    .httpClient(httpClient)
    .build();
RemoteEvaluationClient experiment = Experiment.initializeRemote("SERVER_API_KEY", config);
```

The supplied client's protocols, dispatcher, connection pool, and ping interval
are retained. Configure pings explicitly when supplying a client: OkHttp disables
them by default. `httpProxy`, when set, overrides the supplied client's proxy.
The SDK does not shut down caller-owned resources. The per-attempt
`fetchTimeoutMillis` overrides the supplied client's call timeout.
