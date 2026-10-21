# Build the Kotlin/Wasm application, then serve its static distribution.
FROM nixos/nix AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradle.properties settings.gradle.kts build.gradle.kts ./
COPY composeApp composeApp
# .git is not copied, so pass the commit: --build-arg COMMIT=$(git rev-parse HEAD)
ARG COMMIT=unknown
RUN gradle :composeApp:wasmJsBrowserDistribution --no-daemon -Pkakeibo.commit=$COMMIT

FROM nginx:1.27-alpine
COPY --from=build /workspace/composeApp/build/dist/wasmJs/productionExecutable/ /usr/share/nginx/html/
EXPOSE 80
