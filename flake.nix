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
        packages.${system}.default = pkgs.stdenv.mkDerivation {
          buildInputs = [ pkgs.jdk21 pkgs.gradle androidSdk.androidsdk ];
        };

        devShells.${system}.default = pkgs.mkShell {
          packages = [ pkgs.jdk21 pkgs.gradle androidSdk.androidsdk ];
          ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
          ANDROID_SDK_ROOT = "${androidSdk.androidsdk}/libexec/android-sdk";
        };

        apps.${system}.build-web = mkApp "${pkgs.gradle}/bin/gradle :composeApp:wasmJsBrowserDistribution --no-daemon";
      });
}
