# Stage 1: Build the Kotlin WasmJS application
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Install libatomic required by Node.js versions used in Kotlin Wasm
RUN apt-get update && apt-get install -y libatomic1 && rm -rf /var/lib/apt/lists/*

# Copy Gradle wrapper and configuration files
COPY gradle/ /app/gradle/
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties /app/

# Copy module configurations
COPY terminal-core/build.gradle.kts /app/terminal-core/
COPY terminal-ui/build.gradle.kts /app/terminal-ui/
COPY demo-web/build.gradle.kts /app/demo-web/

# Download dependencies first
RUN ./gradlew :demo-web:wasmJsBrowserDevelopmentRun --dry-run || true

# Copy actual source files
COPY terminal-core/ /app/terminal-core/
COPY terminal-ui/ /app/terminal-ui/
COPY demo-web/ /app/demo-web/

# Build production assets
RUN ./gradlew :demo-web:wasmJsBrowserDistribution --no-daemon

# Stage 2: Serve the static web assets
FROM nginx:alpine
COPY --from=build /app/demo-web/build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
