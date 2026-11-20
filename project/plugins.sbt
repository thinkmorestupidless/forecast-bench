// Packaging the service as a container image (`sbt Docker/publishLocal`).
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.13.0")
// Formatting: `sbt scalafmtCheckAll` in CI, the native CLI in the pre-commit hook.
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")
