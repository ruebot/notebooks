import org.apache.spark.sql.functions._
import org.apache.spark.sql.expressions.UserDefinedFunction
import io.archivesunleashed._
import io.archivesunleashed.udfs._

// Data and Results.
val warcs = "/home/nruest/Projects/digfemcan/evie/warcs/*"
val results = "/home/nruest/Projects/digfemcan/evie/results/20250620/"

// Load and filter data.
val webpages = RecordLoader.loadArchives(warcs, sc).webpages()
val mimeTypes = Array("text/html")
val urlsPattern = Array(".*eviemagazine\\.com/post.*")

val articles = webpages
  .filter(hasMIMETypes($"mime_type_web_server", lit(mimeTypes)))
  .filter(hasMIMETypes($"mime_type_tika", lit(mimeTypes)))
  .filter(hasUrlPatterns($"url", lit(urlsPattern)))
  .dropDuplicates("url")


// Extract titles from content column.
val articlesWithTitle = articles
  .withColumn("title", regexp_extract($"content", "^([^|]+?)\\s\\|", 1))
  .drop("last_modified_date", "domain", "mime_type_web_server", "mime_type_tika", "language")

// UDF for month numbers.
val monthNum: UserDefinedFunction = udf((month: String) => {
  val months = Map(
    "Jan" -> "01", "Feb" -> "02", "Mar" -> "03", "Apr" -> "04",
    "May" -> "05", "Jun" -> "06", "Jul" -> "07", "Aug" -> "08",
    "Sep" -> "09", "Oct" -> "10", "Nov" -> "11", "Dec" -> "12"
  )
  months.getOrElse(month, "00")
})

// Author, publication date, and read time regexes.
val authorRegex =
  """(?i)(?:By|Written by)\s+((?:[A-Z]\.){1,3}(?:\s+[A-Z][\p{L}'\-]*)*|(?:Dr\.)\s+(?:[A-Z][\p{L}'\-]*\s*)+|(?:[A-Z][\p{L}'\-]*(?:\s+(?:&|and)\s+[A-Z][\p{L}'\-]*|\s+[A-Z][\p{L}'\-]*)*))(?=\d*\s*(?:min\s+read)?\s*(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b)"""

val publicationDateRegex =
  """(?i)(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{1,2})(?:st|nd|rd|th)?[,\s]+(\d{4})"""

val readTimeRegex = "(\\d+) min"

// Extract Author, Publication Date, and Read Time.
val enriched = articlesWithTitle
  .withColumn("author", trim(regexp_extract($"content", authorRegex, 1)))
  .withColumn("pub_month", regexp_extract($"content", publicationDateRegex, 1))
  .withColumn("pub_day", lpad(regexp_extract($"content", publicationDateRegex, 2), 2, "0"))
  .withColumn("pub_year", regexp_extract($"content", publicationDateRegex, 3))
  .withColumn("publication_date", concat_ws("", $"pub_year", monthNum($"pub_month"), $"pub_day"))
  .withColumn("read_time", regexp_extract($"content", readTimeRegex, 1).cast("int"))

// Final dataframe.
val finalDF = enriched
  .drop("pub_month", "pub_day", "pub_year")
  .select("crawl_date", "url", "title", "author", "publication_date", "read_time", "content")

// Write out results.
finalDF.coalesce(1)
  .write
  .option("timestampFormat", "yyyy/MM/dd HH:mm:ss ZZ")
  .option("escape", "\"")
  .option("encoding", "utf-8")
  .option("header", "true")
  .format("csv")
  .save(results + "evie-articles")
