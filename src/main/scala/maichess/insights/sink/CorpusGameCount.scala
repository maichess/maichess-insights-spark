package maichess.insights.sink

import com.mongodb.client.MongoClients
import com.mongodb.client.model.{Filters, Updates}

/** Writes the parsed game count back to the control-plane catalog.
  *
  * The insights-service creates the `insights_corpora` document on ingestion submit with
  * `game_count` 0; the ingestion job fills it in once parsing is done (see `CorpusRecord`
  * in the control plane). database-service stores the record's id as Mongo `_id` (a string,
  * the corpusId), so the update matches on `_id`. A single point update via the Mongo driver
  * — not a Dataset write — so it does not touch the connector. No-op when the corpus is
  * unknown (the control plane owns creation).
  *
  * I/O glue (live Mongo); excluded from coverage via the `sink` package exclusion.
  */
object CorpusGameCount {

  def update(mongoUri: String, db: String, corpusId: String, count: Long): Unit = {
    val client = MongoClients.create(mongoUri)
    try
      client
        .getDatabase(db)
        .getCollection("insights_corpora")
        .updateOne(Filters.eq("_id", corpusId), Updates.set("game_count", count))
    finally client.close()
  }
}
