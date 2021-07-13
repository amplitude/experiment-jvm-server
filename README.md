# experiment-jvm-server

Amplitude Experiment Server-side SDK for the Java and Kotlin

## Install

TODO

## Quick Start

```java
ExperimentClient client = Experiment.initialize("<YOUR_API_KEY>", ExperimentConfig());
ExperimentUser user = ExperimentUser.builder().userId("user@company.com").build();
Map<String, Variant> variants = client.fetch(user).get();
Variant variant = variants.get("<YOUR_FLAG_KEY");
if (variants.is("on")) {
    // handle on
} else {
    // handle off    
}
```