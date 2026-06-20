package maichess.insights.ingest

import com.github.luben.zstd.ZstdInputStream
import maichess.insights.sink.CorpusGameCount
import maichess.insights.tournament.{TournamentAssembler, TournamentExport, TournamentExportParser}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.spark.sql.{Dataset, SaveMode, SparkSession}
import maichess.insights.model.{GameRow, PlyRow}

import java.net.URI
import java.nio.charset.StandardCharsets

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

  def run(cfg: IngestArgs)(implicit spark: SparkSession): Unit = cfg.source match {
    case t: SourceDescriptor.TournamentExport => runTournament(cfg, t)
    case _                                    => runPgn(cfg)
  }

  /** Tournament path: fetch the analytics export JSON, assemble the parsed rows from
    * its UCI games (board-replayed for SAN + `fen_before`), filter, and write the
    * same `insights-parsed` layout the analysis jobs read. The export is small (one
    * tournament), so the rows are assembled on the driver and parallelised once.
    */
  private def runTournament(cfg: IngestArgs, src: SourceDescriptor.TournamentExport)(implicit
      spark: SparkSession,
  ): Unit = {
    import spark.implicits._
    val exportDoc = TournamentExportParser.parse(fetchTournamentExport(src.serverUrl, src.tournamentId))
    require(
      exportDoc.schemaVersion == TournamentExport.SupportedSchemaVersion,
      s"unsupported analytics-export schemaVersion: ${exportDoc.schemaVersion}",
    )

    val kept = TournamentAssembler
      .assemble(cfg.corpusId, exportDoc)
      .filter(a => Ingestion.keep(a.game, cfg.filter))

    val gameDs = spark.createDataset(kept.map(_.game))
    val plyDs = spark.createDataset(kept.flatMap(_.plies))
    writeParsed(gameDs, plyDs, cfg)
  }

  private def runPgn(cfg: IngestArgs)(implicit spark: SparkSession): Unit = {
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
    if (raw.compressed) decompressZstd(compressedPath, scratch, fs)
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

      writeParsed(gameDs, plyDs, cfg)
    } finally {
      // Delete the decompressed scratch (keep raw + Parquet).
      if (raw.compressed && fs.exists(scratch)) fs.delete(scratch, false)
    }
  }

  /** Write the partitioned Parquet to `insights-parsed` and fill the control-plane
    * catalog's game count (created as 0 on submit). Shared by both source paths.
    */
  private def writeParsed(gameDs: Dataset[GameRow], plyDs: Dataset[PlyRow], cfg: IngestArgs): Unit = {
    val base = s"s3a://${cfg.parsedBucket}/${cfg.corpusId}"
    gameDs.write.mode(SaveMode.Overwrite).partitionBy("yearMonth", "ratingBand").parquet(s"$base/games")
    plyDs.write.mode(SaveMode.Overwrite).parquet(s"$base/plies")
    cfg.mongoUri.foreach(uri => CorpusGameCount.update(uri, cfg.mongoDb, cfg.corpusId, gameDs.count()))
  }

  // Fetch the (public) tournament analytics export JSON. The endpoint requires no
  // auth and returns the whole document in one body.
  private def fetchTournamentExport(serverUrl: String, tournamentId: String): String = {
    val url = s"${serverUrl.stripSuffix("/")}/api/tournament/$tournamentId/analytics-export"
    val in = new URI(url).toURL.openStream()
    try new String(in.readAllBytes(), StandardCharsets.UTF_8)
    finally in.close()
  }

  private def copyUrlToFs(url: String, dest: Path, fs: FileSystem): Unit = {
    val in = new URI(url).toURL.openStream()
    val out = fs.create(dest, true)
    try org.apache.hadoop.io.IOUtils.copyBytes(in, out, 1 << 20)
    finally { in.close(); out.close() }
  }

  // Decompress with zstd-jni (bundled by Spark for its own zstd codec, present in the
  // runtime image) rather than Hadoop's ZStandardCodec: the image's libhadoop is built
  // WITHOUT zstd support and the native lib isn't even loaded, so the Hadoop codec throws
  // "native zStandard library not available". zstd-jni ships its own native lib in the jar.
  private def decompressZstd(src: Path, dest: Path, fs: FileSystem): Unit = {
    val in = new ZstdInputStream(fs.open(src))
    val out = fs.create(dest, true)
    try org.apache.hadoop.io.IOUtils.copyBytes(in, out, 1 << 20)
    finally { in.close(); out.close() }
  }
}
