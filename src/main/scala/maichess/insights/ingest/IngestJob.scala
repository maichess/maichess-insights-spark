package maichess.insights.ingest

import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.io.compress.ZStandardCodec
import org.apache.spark.sql.{SaveMode, SparkSession}

import java.net.URI

/** Ingestion Spark entry point (invoked by the SparkApplication from task 05).
  *
  * Pipeline (knowledge/operations/spark-and-minio.md): download `.zst` → decompress
  * **once** to a splittable form in MinIO → parse in parallel across all cores →
  * partitioned Parquet in `insights-parsed` → delete the decompressed scratch.
  *
  * I/O orchestration only — the testable transformation lives in [[Ingestion]] and
  * the pure parsing/assembly in `maichess.insights.pgn`. Excluded from coverage.
  */
object IngestJob {

  def main(args: Array[String]): Unit = {
    val cfg = JobArgs.parse(args)
    implicit val spark: SparkSession =
      SparkSession.builder().appName(s"insights-ingest-${cfg.corpusId}").getOrCreate()
    try run(cfg)
    finally spark.stop()
  }

  def run(cfg: IngestArgs)(implicit spark: SparkSession): Unit = {
    val hadoop = spark.sparkContext.hadoopConfiguration
    val fs = FileSystem.get(new URI(s"s3a://${cfg.rawBucket}"), hadoop)

    val raw = SourceResolver.resolve(cfg.source, cfg.rawBucket)
    val rawZst = new Path(s"s3a://${cfg.rawBucket}/${cfg.corpusId}.pgn.zst")
    val scratch = new Path(s"s3a://${cfg.rawBucket}/${cfg.corpusId}.scratch.pgn")

    // 1. Stage the raw bytes in MinIO (download remote .zst, or reuse an upload).
    val compressedPath =
      if (raw.uri.startsWith("s3a://")) new Path(raw.uri)
      else { copyUrlToFs(raw.uri, rawZst, fs); rawZst }

    // 2. Decompress once to a splittable .pgn (single-core zstd decode).
    if (raw.compressed) decompressZstd(compressedPath, scratch, fs, hadoop)
    val splittable = if (raw.compressed) scratch else compressedPath

    try {
      // 3. Parse in parallel: split the text on the game boundary (a blank line
      // followed by the next game's [Event tag), re-prepend the stripped delimiter.
      hadoop.set("textinputformat.record.delimiter", "\n\n[Event ")
      import spark.implicits._
      val games = spark.sparkContext
        .textFile(splittable.toString)
        .filter(_.trim.nonEmpty)
        .map(chunk => if (chunk.startsWith("[Event ")) chunk else "[Event " + chunk)
        .toDS()

      val (gameDs, plyDs) = Ingestion.transform(games, cfg.corpusId, cfg.filter, cfg.replayBoard)

      // 4. Partitioned Parquet in insights-parsed.
      val base = s"s3a://${cfg.parsedBucket}/${cfg.corpusId}"
      gameDs.write.mode(SaveMode.Overwrite).partitionBy("yearMonth", "ratingBand").parquet(s"$base/games")
      plyDs.write.mode(SaveMode.Overwrite).parquet(s"$base/plies")
    } finally {
      // 5. Delete the decompressed scratch (keep raw + Parquet).
      if (raw.compressed && fs.exists(scratch)) fs.delete(scratch, false)
    }
  }

  private def copyUrlToFs(url: String, dest: Path, fs: FileSystem): Unit = {
    val in = new URI(url).toURL.openStream()
    val out = fs.create(dest, true)
    try org.apache.hadoop.io.IOUtils.copyBytes(in, out, 1 << 20)
    finally { in.close(); out.close() }
  }

  private def decompressZstd(src: Path, dest: Path, fs: FileSystem, hadoop: org.apache.hadoop.conf.Configuration): Unit = {
    val codec = new ZStandardCodec()
    codec.setConf(hadoop)
    val in = codec.createInputStream(fs.open(src))
    val out = fs.create(dest, true)
    try org.apache.hadoop.io.IOUtils.copyBytes(in, out, 1 << 20)
    finally { in.close(); out.close() }
  }
}
