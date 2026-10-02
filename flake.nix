{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-26.05";
    utils.url = "github:Cj-bc/my-nix-utils";
  };

  outputs = { self, nixpkgs, utils }:
    utils.lib.eachSystems [ "x86_64-linux" "aarch64-linux" ] (system:
      let
        pkgs = import nixpkgs { system = system; };
        androidSdk = pkgs.androidenv.composeAndroidPackages {
          platformVersions = [ "35" ];
          buildToolsVersions = [ "35.0.0" ];
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
            hash = "sha256-SN5cRRHUsfCfAPj+seyX++aXPDvhElFHDf6fCwW4Fz4=";
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
        mkApp = code: {
          type = "app";
           program =  (pkgs.writeShellScriptBin "exe" code).outPath + "/bin/exe";
        };
      in {
        packages.${system} = {
          android = pkgs.stdenv.mkDerivation {
            name = "tmp-budget-book";
            buildInputs = [ pkgs.jdk21 gradle androidSdk.androidsdk ];
          };
          web = pkgs.stdenv.mkDerivation (finalAttrs: {
            name = "tmp-budget-book";
            nativeBuildInputs = [ pkgs.jdk21 gradle pkgs.nodejs_26 pkgs.yarn pkgs.binaryen
              pkgs.makeWrapper
            ];
            src = ./.;

            # Maven dependencies are recorded in ./deps.json.
            # Regenerate it (e.g. after changing dependencies) with:
            #   USE_BWRAP=0 $(nix build --no-link --print-out-paths .#web.mitmCache.updateScript)
            mitmCache = gradle.fetchDeps {
              pkg = finalAttrs.finalPackage;
              data = ./deps.json;
            };

            yarnOfflineCache = yarnOfflineMirror;

            gradleFlags = [ "-Dorg.gradle.java.home=${pkgs.jdk21}" ];
            gradleBuildTask = ":composeApp:wasmJsBrowserDistribution";
            gradleUpdateTask = finalAttrs.gradleBuildTask;

            # Kotlin runs `yarn install` both for the project (build/wasm) and for its own tooling
            # (under GRADLE_USER_HOME); a ~/.yarnrc makes both install from the offline mirror.
            preBuild = ''
              export HOME=$(mktemp -d)
              cat > $HOME/.yarnrc <<EOF
              yarn-offline-mirror "$yarnOfflineCache"
              --install.offline true
              EOF
            '';
            installPhase = ''
              runHook preInstall
              
              mkdir -p $out/bin
              mkdir -p $out/lib/${self.packages.${system}.web.name}/public

              mv build/wasm/packages/kakeibo/kotlin $out/lib/public

              runHook postInstall
            '';
          });
        };
        devShells.${system} = {
          android = pkgs.mkShell {
            packages = [ pkgs.jdk21 gradle androidSdk.androidsdk ];
            ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${androidSdk.androidsdk}/libexec/android-sdk";
          };
          web = pkgs.mkShell {
            packages = [ pkgs.jdk21 gradle pkgs.nodejs_26 pkgs.yarn pkgs.binaryen ];
          };
        };

        apps.${system}.build-web = mkApp "${gradle}/bin/gradle :composeApp:wasmJsBrowserDistribution --no-daemon --stacktrace";
      });
}
