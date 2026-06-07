Morse IME
---

## Building Project

First create file `local.properties` writing this (modify for your local path) under the project root:

```properties
sdk.dir=/path/to/AndroidSDK
ndk.dir=/path/to/AndroidSDK/ndk/<version>
```

Then create config file `config.toml` under the root:

```toml
[ndk]
targets = ["arm64-v8a-21"]
build_type = "release"
```

Then, do

```shell
./gradlew asD
# or for release builds,
# ./gradlew asR
```
