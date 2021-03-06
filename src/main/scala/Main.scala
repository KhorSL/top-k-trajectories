import java.io.PrintWriter

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD

import annotation.tailrec
import scala.reflect.ClassTag

import java.text.SimpleDateFormat

case class DataPoint(driverID: String, orderID: String, minute: Int, longitude: Float, latitude: Float) extends Serializable

object Main extends Main {

  @transient lazy val conf: SparkConf = new SparkConf().setMaster("local").setAppName("Top-k_Trajectories")
  @transient lazy val sc: SparkContext = new SparkContext(conf)

  def main(args: Array[String]): Unit = {
    val interestLongitude = 0f
    val interestLatitude = 0f
    val start = 7*60 + 25
    val end = 7*60 + 26
    val lines = sc.textFile("data/didi_sample_data")
    val dataPoints = rawDataPoints(lines)
    //dataPoints.saveAsTextFile("data/out/test.txt")
    val trajectories = getTrajectories(dataPoints)
    val filteredTrajectories = filterTrajectoriesByTime(trajectories, start, end)

    filteredTrajectories.saveAsTextFile("data/out/test.txt")
  }
}

class Main extends Serializable {

  final val MAX_MINUTE = 1439

  /** Load data points from the given file */
  def rawDataPoints(lines: RDD[String]): RDD[DataPoint] = {
    val hourDf:SimpleDateFormat = new SimpleDateFormat("HH")
    val minuteDf:SimpleDateFormat = new SimpleDateFormat("mm")
    lines.map(line => {
      val arr = line.split(",")
      DataPoint(
        driverID = arr(0),
        orderID = arr(1),
        minute = (hourDf.format(arr(2).toLong * 1000L).toInt * 60) + minuteDf.format(arr(2).toLong * 1000L).toInt,
        longitude = arr(3).toFloat,
        latitude = arr(4).toFloat)
    })
  }

  /** Group the trajectories together */
  def getTrajectories(dataPoints: RDD[DataPoint]): RDD[(String, Iterable[DataPoint])] = {
    dataPoints.map(dp => (dp.orderID, dp)).groupByKey()
  }

  /** Filter the trajectories based on user input time */
  def filterTrajectoriesByTime(dataPoints: RDD[(String, Iterable[DataPoint])], start: Int, end: Int):
  RDD[(String, Iterable[DataPoint])] = {
    dataPoints.filter { case (key, xs) =>
      xs.exists { xss =>
        if (start <= end) {
          (xss.minute >= start) && (xss.minute <= end)
        } else {
          (xss.minute >= start) && (xss.minute <= MAX_MINUTE) || (xss.minute >= 0) && (xss.minute <= end)
        }
      }
    }
  }
}
