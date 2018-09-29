import sbt._
import Keys._

object BuildSettings {

  lazy val metaVersion = "4.0.0"

  val buildSettings = Seq(
    version := "0.1-SNAPSHOT",
    scalacOptions ++= Seq("-optimize", "-feature", /*"-Yinline-warnings",*/ "-deprecation"),
    //javaOptions := Seq("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5140"),
    scalaVersion := "2.11.11",
    crossScalaVersions := Seq("2.11.11"),
    resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases",
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies += "org.scalameta" %% "scalameta" % metaVersion
  ) ++ PublishSettings.publishSettings
  
  
  val publishableSettings = PublishSettings.publishableSettings
  val macroSettings = MacroSettings.macroSettings

  def exposeClasspaths(projectName: String) = Seq(
     fullClasspath in Compile := {
      val defaultValue = (fullClasspath in Compile).value
      val classpath = defaultValue.files.map(_.getAbsolutePath)
      System.setProperty("sbt.paths." + projectName + ".classpath", classpath.mkString(java.io.File.pathSeparatorChar.toString))
      defaultValue
    }
  )
}