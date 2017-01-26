package mlpipeline

import ontInterface.OwlMovie
import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.feature._
import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, SQLContext}
import org.apache.spark.{SparkConf, SparkContext}
import recommendation.SimpleRecommendation._

import scala.collection.immutable.HashMap

object SparkNaiveBayes {


  def main(args: Array[String]) {
    System.setProperty("hadoop.home.dir", "J:\\winutils")

    val trainFolder = "data/Categories/*"
    val conf = new SparkConf().setAppName(s"NBExample").setMaster("local[*]").set("spark.driver.memory", "4g").set("spark.executor.memory", "4g")
    val sc = new SparkContext(conf)

    Logger.getRootLogger.setLevel(Level.WARN)


    // Load documents, and prepare them for NB.
    val preprocessStart = System.nanoTime()
    val (input, corpus) =
      preprocess(sc, trainFolder)

    var hm = new HashMap[String, Int]()
    val CATEGORIES = List("Cancer", "Cold")
    var index = 0
    CATEGORIES.foreach(f => {
      hm += CATEGORIES(index) -> index
      index += 1
    })
    val mapping = sc.broadcast(hm)
    val data = input.zip(corpus)
    val featureVector = data.map(f => {
      val location_array = f._1._1.split("/")
      val class_name = location_array(location_array.length - 2)

      new LabeledPoint(hm.get(class_name).get.toDouble, f._2)
    })

    val model = NaiveBayes.train(featureVector, lambda = 1.0, modelType = "multinomial")

    val testDir = "data/test/*"

    val LDAK = 3

    val topicData = SparkLDAMain.main(sc, testDir, LDAK, "em")

    val testFV = getTFIDFVector(sc, topicData)

    val result = model.predict(testFV)

    var percentageScore = 0
    val recommendedGenre = getRecommendedGenre(sc, 0)
    println("Recommended Genre : " + recommendedGenre + " for user 0")
    result.collect().foreach(f => {
      println(f + ":" + CATEGORIES(f.toInt))
      if (recommendedGenre.contains(CATEGORIES(f.toInt)))
        percentageScore = percentageScore + 1
    })

    println("The data is " + (percentageScore.toDouble / LDAK.toDouble) * 100 + "% recommended")


    var owl = new OwlMovie()
    CATEGORIES.foreach(f => {
      owl.createSubClass("Description", f + "_Description")
    })

    input.collect.foreach(f => {
      val location_array = f._1.split("/")
      val class_name = location_array(location_array.length - 2)
      val array = f._2.split(" ")
      array.foreach(ff => {
        owl.createIndividual(":" + ff, class_name+"_Description")
      })

    })

    owl.createClass(":Topic")



    val resultData = topicData.zip(result.collect())
    var i = 1
    resultData.map(f => {
      owl.createIndividual(":Topic" + i, ":Topic")
      var className = ""
      if (f._2 == 0)
        className = ":Cancer"
      else
        className = ":Cold"

      val array = f._1.split(" ")
      array.foreach(ff => {
        owl.createIndividual(":" + ff, className + "_Description")
        owl.createObjectProperty(":" + ff, "hasTopic", ":Topic" + i)
      })
      i = i + 1

    })

    owl = addRecoToOnt(sc, owl)

    CATEGORIES.foreach(f => {
      owl.createObjectProperty(":" + f, "hasDescription", f + "_Description")
    })


    owl.saveOntology()

    sc.stop()
  }

  private def preprocess(sc: SparkContext,
                         paths: String): (RDD[(String, String)], RDD[Vector]) = {

    val sqlContext = SQLContext.getOrCreate(sc)
    import sqlContext.implicits._
    val df = sc.wholeTextFiles(paths).map(f => {
      var ff = f._2.replaceAll("[^a-zA-Z\\s:]", " ")
      ff = ff.replaceAll(":", "")
      // println(ff)
      (f._1, CoreNLP.returnLemma(ff))
    }).toDF("location", "docs")


    val tokenizer = new RegexTokenizer()
      .setInputCol("docs")
      .setOutputCol("rawTokens")
    val stopWordsRemover = new StopWordsRemover()
      .setInputCol("rawTokens")
      .setOutputCol("tokens")

    val tf = new org.apache.spark.ml.feature.HashingTF()
      .setInputCol("tokens")
      .setOutputCol("features")
    val idf = new org.apache.spark.ml.feature.IDF()
      .setInputCol("features")
      .setOutputCol("idfFeatures")

    val pipeline = new Pipeline()
      .setStages(Array(tokenizer, stopWordsRemover, tf, idf))

    val model = pipeline.fit(df)
    val documents = model.transform(df)
      .select("idfFeatures")
      .rdd
      .map { case Row(features: Vector) => features }

    val input = model.transform(df).select("location", "docs").rdd.map { case Row(location: String, docs: String) => (location, docs) }
    println(model.transform(df).printSchema())
    (input, documents)
  }

  def getTFIDFVector(sc: SparkContext, input: Array[String]): RDD[Vector] = {

    val sqlContext = SQLContext.getOrCreate(sc)
    import sqlContext.implicits._
    val df = sc.parallelize(input.toSeq).toDF("docs")


    val tokenizer = new RegexTokenizer()
      .setInputCol("docs")
      .setOutputCol("rawTokens")
    val stopWordsRemover = new StopWordsRemover()
      .setInputCol("rawTokens")
      .setOutputCol("tokens")

    val tf = new org.apache.spark.ml.feature.HashingTF()
      .setInputCol("tokens")
      .setOutputCol("features")
    val idf = new org.apache.spark.ml.feature.IDF()
      .setInputCol("features")
      .setOutputCol("idfFeatures")



    val pipeline = new Pipeline()
      .setStages(Array(tokenizer, stopWordsRemover, tf, idf))

    val model = pipeline.fit(df)
    val documents = model.transform(df)
      .select("idfFeatures")
      .rdd
      .map { case Row(features: Vector) => features }

    documents
  }
}
