package maichess.insights

import org.scalatest.Tag

/** Tag for suites that need a local[*] SparkSession. Spark 3.5 does not run on Java
  * 18+, so the build excludes this tag on a too-new test JVM (see build.sbt);
  * CI + the image run Java 17 where these execute.
  */
object SparkTest extends Tag("maichess.insights.SparkTest")
