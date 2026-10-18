{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";
    utils.url = "github:Cj-bc/my-nix-utils";
  };

  outputs = { self, nixpkgs, utils }:
    utils.lib.eachSystems [ "x86_64-linux" "aarch64-linux" ] (system:
      let
        pkgs = import nixpkgs {
          system = system;
          # The Android SDK is unfree and needs its license accepted.
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };
        # The Build-Tools version AGP uses by default (36.0.0 for AGP 9.4). If it's missing, AGP
        # tries to download it, which fails in the sandbox. Update it along with AGP.
        buildToolsVersion = "36.0.0";
        androidSdk = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "35" ];
          buildToolsVersions = [ buildToolsVersion ];
          includeEmulator = false;
          includeNDK = false;
        };
        # AGP 9.4 requires Gradle >= 9.6.0, newer than nixpkgs' gradle_9.
        # Keep in sync with gradle/wrapper/gradle-wrapper.properties.
        gradle = (pkgs.gradle-packages.mkGradle {
          version = "9.6.0";
          hash = "sha256-u66y/vhxCBjPDiYSAdq5ZMVy+SuUKBLfDDYg1ipSmgE=";
          defaultJava = pkgs.jdk21;
        }).wrapped;

        # Kotlin/Wasm installs npm packages with yarn, which can't reach the network in the sandbox.
        # Two lockfiles are involved: the project's (kotlin-js-store) and the one bundled in the
        # Kotlin Gradle plugin for its webpack/karma tooling.
        # Keep the plugin jar in sync with the kotlin version in gradle/libs.versions.toml
        # (its hash is in deps.json).
        kotlinGradlePluginJar = pkgs.fetchurl {
          url = "https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/2.3.20/kotlin-gradle-plugin-2.3.20-gradle813.jar";
          hash = "sha256-RuFu4mWU9ID+y5LJbarjdTDoNQJreON+8yUelX8D9vs=";
        };
        kotlinToolingYarnLock = pkgs.runCommand "kotlin-npm-tooling-yarn.lock" {
          nativeBuildInputs = [ pkgs.unzip ];
        } ''
          unzip -p ${kotlinGradlePluginJar} org/jetbrains/kotlin/gradle/targets/js/yarn/yarn.lock > $out
        '';
        yarnLocks = [ ./kotlin-js-store/wasm/yarn.lock kotlinToolingYarnLock ];
        yarnDeps = [
          (pkgs.fetchYarnDeps {
            yarnLock = ./kotlin-js-store/wasm/yarn.lock;
            hash = "sha256-V7UxLZHVfZO7ePzt+0Fje49Gn8vzoXzXROrGBXDcIjg=";
          })
          (pkgs.fetchYarnDeps {
            yarnLock = kotlinToolingYarnLock;
            hash = "sha256-WBUHn03o+aTkNnIpIuLEfjyJci90dobzACzXWel837Y=";
          })
        ];
        # fetchYarnDeps names tarballs for fixup-yarn-lock, but we can't rewrite the lockfiles
        # Kotlin copies around at build time. Re-expose them under the names yarn's
        # offline mirror looks up (scope-prefixed tarball basename).
        yarnOfflineMirror = pkgs.runCommand "yarn-offline-mirror" { } ''
          mkdir $out
          urls=$(sed -nE 's/^ *resolved "([^"#]*).*/\1/p' ${pkgs.lib.concatMapStringsSep " " (l: "${l}") yarnLocks} | sort -u)
          for url in $urls; do
            if [[ $url == https://codeload.github.com/*/tar.gz/* || $url == git+* ]]; then
              src=$(basename "$url")
            else
              src=$(echo "$url" | sed -E 's#https://(.)*(.com)/##g; s#[@/%:-]#_#g')
            fi
            path=''${url#*://*/}
            if [[ $path =~ ^(@[^/]+)/[^/]+/-/([^/]+)$ ]]; then
              dst="''${BASH_REMATCH[1]}-''${BASH_REMATCH[2]}"
            else
              dst=$(basename "$path")
            fi
            for cache in ${pkgs.lib.escapeShellArgs yarnDeps}; do
              if [ -e "$cache/$src" ]; then ln -sf "$cache/$src" "$out/$dst"; continue 2; fi
            done
            echo "missing $url in offline caches" >&2; exit 1
          done
        '';
        gradleNativeBuildInputs = [ pkgs.jdk21 gradle pkgs.nodejs_26 pkgs.yarn pkgs.binaryen ];
        androidHome = "${androidSdk.androidsdk}/libexec/android-sdk";
        gradleFlags = [
          "-Dorg.gradle.java.home=${pkgs.jdk21}"
          # The aapt2 that AGP downloads from Maven is dynamically linked against a loader that
          # isn't in the sandbox; use the patched one from the Nix Android SDK instead.
          "-Pandroid.aapt2FromMavenOverride=${androidHome}/build-tools/${buildToolsVersion}/aapt2"
        ];
        webBuildTask = ":composeApp:wasmJsBrowserDistribution";
        androidBuildTask = ":androidApp:assembleDebug";

        # Kotlin runs `yarn install` both for the project (build/wasm) and for its own tooling
        # (under GRADLE_USER_HOME); a ~/.yarnrc makes both install from the offline mirror.
        setupYarnOffline = ''
          export HOME=$(mktemp -d)
          cat > $HOME/.yarnrc <<EOF
          yarn-offline-mirror "$yarnOfflineCache"
          --install.offline true
          EOF
        '';

        # Gradle's work that doesn't depend on the Kotlin sources: fetching Maven dependencies,
        # compiling the build scripts, transforming artifacts and installing Kotlin's npm tooling.
        # It is a separate derivation whose source is only the build scripts, so it is cached in
        # the Nix store and reused by `web` and `android` as long as those don't change.
        gradleDeps = pkgs.stdenv.mkDerivation (finalAttrs: {
          name = "tmp-budget-book-gradle-deps";
          nativeBuildInputs = gradleNativeBuildInputs;
          src = pkgs.lib.fileset.toSource {
            root = ./.;
            fileset = pkgs.lib.fileset.unions [
              ./settings.gradle.kts
              ./build.gradle.kts
              ./gradle.properties
              ./gradle
              ./composeApp/build.gradle.kts
              ./kotlin-js-store
              # Its manifest and resources too: assembling the APK needs them.
              ./androidApp
            ];
          };

          # Maven dependencies are recorded in ./deps.json.
          # Regenerate it (e.g. after changing dependencies) with:
          #   USE_BWRAP=0 $(nix build --no-link --print-out-paths .#web.gradleDeps.mitmCache.updateScript)
          mitmCache = gradle.fetchDeps {
            pkg = finalAttrs.finalPackage;
            data = ./deps.json;
          };

          inherit gradleFlags;
          gradleBuildTask = "${webBuildTask} ${androidBuildTask}";
          gradleUpdateTask = finalAttrs.gradleBuildTask;
          yarnOfflineCache = yarnOfflineMirror;
          ANDROID_HOME = androidHome;

          # Same path as in `web` and `android`, so path-keyed entries in the cache stay valid.
          postUnpack = ''
            export GRADLE_USER_HOME=$NIX_BUILD_TOP/gradle-home
          '';

          # Placeholder sources, so that the same tasks run as in the real build (an empty
          # source set would skip compilation and webpack, leaving their dependencies unfetched).
          preBuild = ''
            ${setupYarnOffline}

            for sourceSet in commonMain wasmJsMain androidMain; do
              mkdir -p composeApp/src/$sourceSet/kotlin
              echo "package gradledeps" > composeApp/src/$sourceSet/kotlin/$sourceSet.kt
            done
          '';

          installPhase = ''
            runHook preInstall

            # Drop the state of the Gradle processes that ran here.
            rm -rf $GRADLE_USER_HOME/daemon $GRADLE_USER_HOME/.tmp
            find $GRADLE_USER_HOME/caches \( -name '*.lock' -o -name gc.properties \) -delete
            cp -r $GRADLE_USER_HOME $out

            runHook postInstall
          '';
        });

        # Start from the Gradle home warmed up by gradleDeps. No mitmCache is given to the
        # packages, so the gradle setup hook adds --offline: everything must already be in it.
        useGradleDeps = ''
          export GRADLE_USER_HOME=$NIX_BUILD_TOP/gradle-home
          cp -r ${gradleDeps} $GRADLE_USER_HOME
          chmod -R u+w $GRADLE_USER_HOME
        '';

        exeWrapper = pkgs.writeShellScriptBin "tmp-budget-book" ''
          #!/usr/bin/env -S bash
          ${pkgs.deno}/bin/deno run --allow-read=@read_path@ --allow-net=127.0.0.1 @server_path@
        '';

        mkApp = code: {
          type = "app";
           program =  (pkgs.writeShellScriptBin "exe" code).outPath + "/bin/exe";
        };
      in {
        packages.${system} = {
          # A debug APK, signed with a debug key generated during the build.
          android = pkgs.stdenv.mkDerivation {
            name = "tmp-budget-book-android";
            nativeBuildInputs = gradleNativeBuildInputs;
            src = ./.;

            inherit gradleFlags;
            gradleBuildTask = androidBuildTask;
            yarnOfflineCache = yarnOfflineMirror;
            ANDROID_HOME = androidHome;

            postUnpack = useGradleDeps;
            preBuild = setupYarnOffline;

            passthru = { inherit gradleDeps; };

            installPhase = ''
              runHook preInstall

              install -Dm644 androidApp/build/outputs/apk/debug/androidApp-debug.apk $out/tmp-budget-book.apk

              runHook postInstall
            '';
          };
          web = pkgs.stdenv.mkDerivation (finalAttrs: {
            name = "tmp-budget-book";
            nativeBuildInputs = gradleNativeBuildInputs ++ [ exeWrapper pkgs.makeWrapper pkgs.deno ];
            buildInputs = [ pkgs.deno ];
            src = ./.;

            inherit gradleFlags;
            gradleBuildTask = webBuildTask;
            yarnOfflineCache = yarnOfflineMirror;

            postUnpack = useGradleDeps;

            preBuild = ''
              ${setupYarnOffline}

              patchShebangs --host server.ts
              server_path=$out/lib/server.ts read_path=$out/lib/public substituteAll ${exeWrapper}/bin/tmp-budget-book tmp-budget-book
              read_path=$out/lib/public substituteAll server.ts server.ts
             '';

            passthru = { inherit gradleDeps; };

            installPhase = ''
              runHook preInstall

              mkdir -p $out/bin
              mkdir -p $out/lib/public

              mv tmp-budget-book $out/bin/
              chmod +x $out/bin/tmp-budget-book

              mv server.ts $out/lib/
              mv composeApp/build/dist/wasmJs/productionExecutable/* $out/lib/public

              runHook postInstall
            '';
          });
        };
        devShells.${system} = {
          android = pkgs.mkShell {
            packages = [ pkgs.jdk21 gradle androidSdk.androidsdk ];
            ANDROID_HOME = androidHome;
            ANDROID_SDK_ROOT = androidHome;
          };
          web = pkgs.mkShell {
            packages = [ pkgs.jdk21 gradle pkgs.nodejs_26 pkgs.yarn pkgs.binaryen ];
          };
        };

        apps.${system}.build-web = mkApp "${gradle}/bin/gradle :composeApp:wasmJsBrowserDistribution --no-daemon --stacktrace";
      });
}
