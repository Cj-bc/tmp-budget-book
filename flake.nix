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
        mkApp = code: {
          type = "app";
           program =  (pkgs.writeShellScriptBin "exe" code).outPath + "/bin/exe";
        };
      in {
        packages.${system} = {
          android = pkgs.stdenv.mkDerivation {
            name = "tmp-budget-book";
            buildInputs = [ pkgs.jdk21 pkgs.gradle_9 androidSdk.androidsdk ];
          };
          web = pkgs.stdenv.mkDerivation {
            name = "tmp-budget-book";
            buildInputs = [ pkgs.jdk21 pkgs.gradle_9 pkgs.nodejs_26 pkgs.yarn ];
          };
        };
        devShells.${system} = {
          android = pkgs.mkShell {
            packages = [ pkgs.jdk21 pkgs.gradle_9 androidSdk.androidsdk ];
            ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${androidSdk.androidsdk}/libexec/android-sdk";
          };
          web = pkgs.mkShell {
            packages = [ pkgs.jdk21 pkgs.gradle_9 pkgs.nodejs_26 pkgs.yarn ];
          };
        };

        apps.${system}.build-web = mkApp "${pkgs.gradle_9}/bin/gradle :composeApp:wasmJsBrowserDistribution --no-daemon --stacktrace";
      });
}
