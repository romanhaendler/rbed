# Releasing

rbed is published to Maven Central as `io.github.romanhaendler:rbed` through the [Central Portal](https://central.sonatype.com).

## One-time setup

1. Sign in to [central.sonatype.com](https://central.sonatype.com) with the GitHub account `romanhaendler`. The namespace `io.github.romanhaendler` is verified automatically.
2. Under *View Account*, generate a user token and add it to `~/.m2/settings.xml`:

   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>TOKEN_USERNAME</username>
         <password>TOKEN_PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```

3. Create a GPG key and publish it, so that Central can verify the signatures:

   ```sh
   brew install gnupg pinentry-mac
   gpg --full-generate-key                                   # RSA 4096, your name and email
   gpg --list-secret-keys --keyid-format long                # the key id follows "rsa4096/"
   gpg --keyserver keys.openpgp.org --send-keys KEY_ID
   ```

   keys.openpgp.org sends a confirmation email before it publishes the key with your address.

## Each release

1. Set the release version, e.g. `mvn versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false`, update the version in the README's install section, and commit.
2. Upload: `mvn clean deploy -Prelease`. This builds, tests, signs and uploads, then waits until Central has validated the bundle.
3. Check the deployment on [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments) and click *Publish*. A published version can never be changed or deleted.
4. Tag and release on GitHub: `git tag v0.1.0 && git push origin v0.1.0 && gh release create v0.1.0 --generate-notes`.
5. Set the next development version, e.g. `0.2.0-SNAPSHOT`, and commit.
