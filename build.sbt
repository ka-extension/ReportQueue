import com.typesafe.sbt.packager.MappingsHelper._

lazy val server = (project in file("server")).settings(commonSettings).settings(
  scalaJSProjects := Seq(client),
  pipelineStages in Assets := Seq(scalaJSPipeline),
  pipelineStages := Seq(digest, gzip),
  // triggers scalaJSPipeline when using compile or continuous compilation
  compile in Compile := ((compile in Compile) dependsOn scalaJSPipeline).value,
  libraryDependencies ++= Seq(
    "com.vmunier" %% "scalajs-scripts" % "1.1.1",
    guice,
    specs2 % Test,
    "com.google.api-client" % "google-api-client" % "1.23.0",
    "com.google.oauth-client" % "google-oauth-client-jetty" % "1.23.0",
    "com.google.apis" % "google-api-services-sheets" % "v4-rev515-1.23.0",
    "org.scalaj" %% "scalaj-http" % "2.3.0",
    "org.webjars" % "material-design-lite" % "1.3.0",
    "org.webjars" % "material-design-icons" % "3.0.1"
  ),
  mappings in Universal ++= directory(baseDirectory.value / "conf"),
  // Compile the project before generating Eclipse files, so that generated .scala or .class files for views and routes are present
  EclipseKeys.preTasks := Seq(compile in Compile)
).enablePlugins(PlayScala).
  dependsOn(sharedJvm)

lazy val client = (project in file("client")).settings(commonSettings).settings(
  scalaJSUseMainModuleInitializer := true,
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.4"
  )
).enablePlugins(ScalaJSPlugin, ScalaJSWeb).
  dependsOn(sharedJs)

lazy val shared = (crossProject.crossType(CrossType.Pure) in file("shared")).settings(commonSettings)
lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

lazy val commonSettings = Seq(
  scalaVersion := "2.12.4",
  organization := "com.ethanmcdonough"
)

// loads the server project at sbt startup
onLoad in Global := (onLoad in Global).value andThen {s: State => "project server" :: s}
