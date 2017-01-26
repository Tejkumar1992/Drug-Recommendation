package recommendation

import java.io.File

import ontInterface.OwlMovie
import org.apache.spark.mllib.recommendation.{ALS, MatrixFactorizationModel, Rating}
import org.apache.spark.{SparkConf, SparkContext}

import scala.io.Source

/**
  * Created by Bhulakshmi on 16-Jul-16.
  */
object SimpleRecommendation {

  def main(args: Array[String]) {
    System.setProperty("hadoop.home.dir", "J:\\winutils")
    val conf = new SparkConf().setMaster("local[*]").setAppName("SimpleRecommendation")
      .set("spark.executor.memory", "2g")

    val sc = new SparkContext(conf)

    val modelPath = "model/Recommendations"
    var model: MatrixFactorizationModel = null

    val movieLensHomeDir = "data/Recommendation"
    // load personal ratings


    val myRatings = loadRatings(movieLensHomeDir + "/personalRating.txt")
    print(myRatings.mkString("  "))
    val myRatingsRDD = sc.parallelize(myRatings, 1)

    val movies = sc.textFile(new File(movieLensHomeDir, "drugs.dat").toString).map { line =>
      val fields = line.split("::")
      // format: (movieId, movieName)
      (fields(0).toInt, fields(1))
    }.collect().toMap


    if (!new File(modelPath).exists()) {


      // load ratings and movie titles


      val ratings = sc.textFile(new File(movieLensHomeDir, "ratings.dat").toString).map { line =>

        val fields = line.split("::")
        // format: (timestamp % 10, Rating(userId, movieId, rating))
        Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble)
      }

      val training = ratings.union(myRatingsRDD)
        .repartition(4)
        .cache()


      // Build the recommendation model using ALS
      val rank = 12
      val numIterations = 20
      model = ALS.train(training, rank, numIterations, 0.1)
      model.save(sc, modelPath)
    }
    else {
      model = MatrixFactorizationModel.load(sc, modelPath)
    }
    val myRatedMovieIds = myRatings.map(_.product).toSet
    val candidates = sc.parallelize(movies.keys.filter(!myRatedMovieIds.contains(_)).toSeq)

    val recommendations = model.predict(candidates.map((0, _))).collect()

    var i = 1
    println("Drugs recommended for you:")
    recommendations.foreach { r =>
      //println(r)
      println("%2d".format(i) + ": " + movies(r.product))
      i += 1
    }
    /*Recommends products to a user.
    user: the user to recommend products to
     num: how many products to return. The number returned may be less than this.
    */
    i = 1
    val recommendation2 = model.recommendProducts(0, 5)
    println("Expected disease based on symptoms for user 0:")
    recommendation2.foreach { r =>
      //println(r)
      println("%2d".format(i) + ": " + movies(r.product))
      i += 1
    }

    // clean up
    sc.stop()

  }

  def getRecommendedGenre(sc: SparkContext, userId: Int): String = {

    val modelPath = "model/Recommendations"
    var model: MatrixFactorizationModel = null

    val movieLensHomeDir = "data/Recommendation"
    val movies = sc.textFile(new File(movieLensHomeDir, "drugs.dat").toString).map { line =>
      val fields = line.split("::")
      // format: (movieId, genre)
      (fields(0).toInt, fields(2))
    }.collect().toMap
    model = MatrixFactorizationModel.load(sc, modelPath)

    /*Recommends products to a user.
    user: the user to recommend products to
     num: how many products to return. The number returned may be less than this.
    */
    var i = 1
    var genre = ""
    val recommendation2 = model.recommendProducts(userId, 5)
    println("Expected disease based on symptoms for user 0:")
    recommendation2.foreach { r =>
      //println(r)
      println("%2d".format(i) + ": " + movies(r.product))
      genre += movies(r.product) + "|"
      i += 1
    }

    genre
  }

  def addRecoToOnt(sc: SparkContext, owl: OwlMovie): OwlMovie = {

    val modelPath = "model/Recommendations"
    var model: MatrixFactorizationModel = null

    val movieLensHomeDir = "data/Recommendation"
    val movies = sc.textFile(new File(movieLensHomeDir, "drugs.dat").toString).collect().map { line =>
      val fields = line.split("::")
      // format:
      val movieName = ":" + fields(1).replace(":", "").replace(" ", "_")
      val genre = fields(2).replace("|", ";")

      (fields(0).toInt, movieName + ":b:" + genre)
    }.toMap
    model = MatrixFactorizationModel.load(sc, modelPath)

    owl.createIndividual(":" + "User_0", ":User")

    var i = 1

    val recommendation2 = model.recommendProducts(0, 5)
    println("Recommended Medicine/Drug based on symptoms for user 0:")
    recommendation2.foreach { r =>
      //println(r)
      println("%2d".format(i) + ": " + movies(r.product))

      owl.createIndividual(":" + r.product, ":MovieID")
      val genre = movies(r.product)
      val arr = genre.split(":b:")
      owl.createIndividual(arr(0), ":Movie")
      owl.createObjectProperty(arr(0), "hasID", ":" + ":" + r.product)

      owl.createObjectProperty(":User_0", "hasRecommendedMovie", arr(0))
      val garray = arr(1).split(";")
      garray.distinct.foreach(f => {
        owl.createIndividual(":" + f, ":Genre")
        owl.createObjectProperty(arr(0), "hasGenre", ":" + f)
        owl.createObjectProperty(":User_0", "hasRecommendedGenre", ":" + f)
      })
      i += 1
    }

    owl
  }


  def loadRatings(path: String): Seq[Rating] = {
    val lines = Source.fromFile(path).getLines()
    val ratings = lines.map { line =>
      val fields = line.split("::")
      Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble)
    }.filter(_.rating > 0.0)
    if (ratings.isEmpty) {
      sys.error("No ratings provided for the drugs.")
    } else {
      ratings.toSeq
    }
  }

}
