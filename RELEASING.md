# Releasing

rbed is published to Maven Central as `io.github.romanhaendler:rbed` by the GitHub workflow [Publish](.github/workflows/publish.yml).

## Releasing a version

1. Raise the version in `pom.xml` and in the README's install section, e.g. to `0.2.0`, and push to `main`.
2. The workflow sees that `0.2.0` is not on Maven Central yet, then builds, tests, signs, uploads and waits until Central has published it. It then tags `v0.2.0` and creates the GitHub release with the jars.

A push whose version is already on Maven Central releases nothing. A published version can never be changed or deleted, so every fix gets a new version. Versions ending in `-SNAPSHOT` are never released.

## How the workflow is trusted

Maven Central has no trust relationship with GitHub Actions like npm's Trusted Publishing, so the workflow uses secrets of the GitHub environment `maven-central`, which only `main` may use:

| Secret | What it is |
| --- | --- |
| `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` | A user token of [central.sonatype.com](https://central.sonatype.com) (*View Account* → *Generate User Token*). |
| `GPG_PRIVATE_KEY` | Only the signing subkey of the key `34B0836E9C5DD51D668D059A56A65A814808CD25`, never the primary key. |
| `GPG_PASSPHRASE` | Its passphrase. |

The primary key stays on the maintainer's machine. If a secret leaks, revoke the signing subkey, publish the key again and store a new subkey; the identity and the signatures of earlier releases stay valid.

### Replacing the signing subkey

```sh
KEY=34B0836E9C5DD51D668D059A56A65A814808CD25
gpg --quick-add-key $KEY rsa4096 sign 2y
gpg --keyserver keys.openpgp.org --send-keys $KEY
gpg --list-keys --with-subkey-fingerprints $KEY    # the new subkey is the last one with [S]
gpg --export-secret-subkeys --armor 'SUBKEY_FINGERPRINT!' | gh secret set GPG_PRIVATE_KEY --env maven-central -R romanhaendler/rbed
```

The `!` exports exactly that subkey. Central verifies signatures against the key on keys.openpgp.org, so the key must be sent there before the next release.

## Releasing by hand

With the token in `~/.m2/settings.xml` (server id `central`) and the key in the local GnuPG, `mvn clean deploy -Prelease` does the same as the workflow, except tagging and the GitHub release.
