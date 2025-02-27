# OpenTelemetry Resource Providers

This package includes some standard `ResourceProvider`s for filling in attributes related to
common environments. Currently, the resources provide the following semantic conventions:

## Populated attributes

### Container

Provider: `io.opentelemetry.instrumentation.resources.ContainerResource`

Specification: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/semantic_conventions/container.md

Implemented attributes:
- `container.id`

### Host

Provider: `io.opentelemetry.instrumentation.resources.HostResource`

Specification: https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/semantic_conventions/host.md

Implemented attributes:
- `host.name`
- `host.arch`

### Operating System

Provider: `io.opentelemetry.instrumentation.resources.OsResource`

Specification: https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/resource/semantic_conventions/os.md

Implemented attributes:
- `os.type`
- `os.description`

### Process

Implementation: `io.opentelemetry.instrumentation.resources.ProcessResource`

Specification: https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/resource/semantic_conventions/process.md#process

Implemented attributes:
- `process.pid`
- `process.executable.path` (note, we assume the `java` binary is located in the `bin` subfolder of `JAVA_HOME`)
- `process.command_line` (note this includes all system properties and arguments when running)

### Java Runtime

Implementation: `io.opentelemetry.instrumentation.resources.ProcessRuntimeResource`

Specification: https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/resource/semantic_conventions/process.md#process-runtimes

Implemented attributes:
- `process.runtime.name`
- `process.runtime.version`
- `process.runtime.description`

## Platforms

This package currently does not run on Android. It has been verified on OpenJDK and should work on
other server JVM distributions but if you find any issues please let us know.
