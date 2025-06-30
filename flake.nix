{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.11";
    flake-parts = {
      url = "github:hercules-ci/flake-parts";
    };
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = inputs@{ nixpkgs, flake-parts, ... }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [ "x86_64-linux" "aarch64-darwin" "x86_64-darwin" ];
      imports = [
        inputs.treefmt-nix.flakeModule
      ];
      perSystem = { system, config, pkgs, ... }:
        {
          devShells.default = pkgs.mkShell {
            packages = [ pkgs.openjdk21 pkgs.scalafmt pkgs.sbt pkgs.clang pkgs.glibc.dev pkgs.nodejs_23];
            inputsFrom = [
              config.treefmt.build.devShell
            ];
          };

          treefmt.config = {
            projectRootFile = "flake.nix";

            programs = {
              nixpkgs-fmt.enable = true;
              scalafmt.enable = true;
            };
            settings.formatter.scalafmt.include = [ "*.scala" "*.sc" ];
          };
        };
    };
}

