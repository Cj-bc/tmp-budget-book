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
          web = pkgs.stdenv.mkDerivation {
            name = "tmp-budget-book";
            nativeBuildInputs = [ pkgs.jdk21 gradle pkgs.nodejs_26 pkgs.yarn pkgs.binaryen
              pkgs.makeWrapper
            ];
            src = ./.;
            buildPhase = ''
              runHook preBuild

              patchShebangs --build ./gradlew

              ./gradlew :composeApp:wasmJsBrowserDistribution --no-daemon;

              runHook postBuild
            '';
            installPhase = ''
              runHook preInstall
              
              mkdir -p $out/bin
              mkdir -p $out/etc/${self.packages.${system}.web.name}
              mv build/wasm/packages/kakeibo/kotlin $out/etc/${self.packages.${system}.web.name}

              runHook postInstall
            '';
          };
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
