# experiment-java-server

Amplitude Experiment Server-side SDK for Java and Kotlin

## Install

```gradle
implementation "com.amplitude:experiment-java-server:0.0.1"
```

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