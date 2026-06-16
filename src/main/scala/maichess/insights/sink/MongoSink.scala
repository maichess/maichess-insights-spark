package maichess.insights.sink

import org.apache.spark.sql.{Dataset, Encoder, SaveMode, SparkSession}

/** Write path to the `insights_*` Mongo collections via the Spark MongoDB connector
  * — the one place the Spark module writes to a datastore directly (documented
  * exception in the ADR; the .NET service still reads via database-service).
  *
  * Pure I/O glue (live Mongo connection) — excluded from coverage, mirrored in
  * `stryker4s.conf`.
  */
final class MongoSink(uri: String, database: String)(implicit spark: SparkSession) {

  /** Replace a collection's documents for this run. Rows already carry `corpusId`,
    * so a collection can hold multiple corpora; we overwrite by replacing the whole
    * collection (the control plane runs one corpus at a time per analysis).
    */
  def write[T](rows: Dataset[T], collection: String): Unit =
    rows.write
      .format("mongodb")
      .mode(SaveMode.Overwrite)
      .option("connection.uri", uri)
      .option("database", database)
      .option("collection", collection)
      .save()

  /** Append a single document (used for the `insights_jobs` lifecycle record) so the
    * catalog accumulates runs rather than being replaced.
    */
  def append[T](row: T, collection: String)(implicit enc: Encoder[T]): Unit =
    spark
      .createDataset(Seq(row))
      .write
      .format("mongodb")
      .mode(SaveMode.Append)
      .option("connection.uri", uri)
      .option("database", database)
      .option("collection", collection)
      .save()
}
