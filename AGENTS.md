# About environment

If you are currently working in Windows environemnt, you might have nix installed within WSLv2.
Try calling `wsl -e /root/.nix-profile/bin/nix`.

# GitHub Actions (APK workflows)

- Environment secrets are passed to `.github/actions/build-apk` as inputs from the jobs that set `environment:`. A reusable workflow (`workflow_call`) with `environment:` on its job got all the secrets empty, so don't move back to it.
- The release `versionCode` is `github.run_number` of `release-apk.yml`. Renaming that workflow restarts the count from 1, and the new APKs can't update the installed app.
