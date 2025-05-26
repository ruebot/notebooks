import io.archivesunleashed._
import io.archivesunleashed.udfs._

// Data and Results.
val warcs = "/home/nruest/Projects/digfemcan/evie/warcs/*"
val results = "/home/nruest/Projects/digfemcan/evie/results/20250620/"

// TikTok and Instagram URLs.
val urlsPattern = Array(".*tiktok\\.com/.*", ".*instagram\\.com/.*")

// Load and filter data.
val webpages = RecordLoader.loadArchives(warcs, sc).webpages().cache()
val tiktokInsta = webpages.filter(hasUrlPatterns($"url", lit(urlsPattern))).select("url")

// Write out results.
tiktokInsta.coalesce(1)
  .write
  .option("timestampFormat", "yyyy/MM/dd HH:mm:ss ZZ")
  .option("escape", "\"")
  .option("encoding", "utf-8")
  .option("header", "true")
  .format("csv")
  .save(results + "tiktok-insta")
