import sbt.ProjectRef

lazy val avlEditor = project
  .in(file("."))

name := "AVL Editor"

version := "1.0.0"

scalaVersion := "2.10.4"

resolvers += "jogamp-remote" at "https://jogamp.org/deployment/maven"

Compile / run / mainClass := Some("com.abajar.avleditor.Main")

enablePlugins(JavaAppPackaging)

maintainer := "Hugo Freire <hfreire@abajar.com>"

ThisBuild / scalacOptions ++= Seq("-feature")

// javacOptions ++= Seq("-Xlint:unchecked")
scalacOptions ++= Seq("-language:postfixOps", "-language:reflectiveCalls")

run / fork := true

run / envVars := Map("SWT_GTK3" -> "0")

val osName = System.getProperty("os.name").toLowerCase
val osArch = System.getProperty("os.arch")

// SWT 3.127.0 from Maven Central (org.eclipse.platform group)
val swtDependency: Option[sbt.ModuleID] = {
  if (osName.contains("linux"))
    Some(("org.eclipse.platform" % "org.eclipse.swt.gtk.linux.x86_64" % "3.127.0")
      .exclude("org.eclipse.platform", "org.eclipse.swt"))
  else if (osName.contains("windows"))
    Some(("org.eclipse.platform" % "org.eclipse.swt.win32.win32.x86_64" % "3.127.0")
      .exclude("org.eclipse.platform", "org.eclipse.swt"))
  else if (osName.contains("mac"))
    if (osArch.contains("aarch64"))
      Some(("org.eclipse.platform" % "org.eclipse.swt.cocoa.macosx.aarch64" % "3.127.0")
        .exclude("org.eclipse.platform", "org.eclipse.swt"))
    else
      Some(("org.eclipse.platform" % "org.eclipse.swt.cocoa.macosx.x86_64" % "3.127.0")
        .exclude("org.eclipse.platform", "org.eclipse.swt"))
  else
    None
}

libraryDependencies ++= Seq(
  "org.eclipse.persistence" % "org.eclipse.persistence.moxy" % "2.5.2",
  "junit" % "junit" % "4.12",
  "javax.xml.bind" % "jaxb-api" % "2.3.1",
  "org.jogamp.gluegen" % "gluegen-rt-main" % "2.5.0",
  "org.jogamp.jogl" % "jogl-all-main" % "2.5.0",
  "org.yaml" % "snakeyaml" % "2.0"
) ++ swtDependency.toSeq
