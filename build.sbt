
name := "hosebird-skeleton"

version := "0.1"

scalaVersion := "2.10.0"

scalacOptions ++= Seq("-unchecked", "-deprecation" )

libraryDependencies ++= Seq( "org.scalatest"        % "scalatest_2.10"          % "1.9.1"       % "test",
                             "com.twitter"          % "hbc-core"                % "2.0.2",
                             "com.twitter"          % "hbc-twitter4j"           % "2.0.2",
                             "org.apache.commons"   % "commons-lang3"           % "3.1",
                             "org.slf4j"            % "slf4j-simple"            % "1.7.6",
                             "org.slf4j"            % "log4j-over-slf4j"        % "1.7.6",
                             "org.slf4j"            % "slf4j-api"               % "1.7.6" )

