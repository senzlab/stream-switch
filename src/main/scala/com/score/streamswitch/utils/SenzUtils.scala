package com.score.streamswitch.utils

/**
  * Created by eranga on 8/3/16.
  */
object SenzUtils {
  def getPingSenz(receiver: String, sender: String) = {
    val timestamp = (System.currentTimeMillis / 1000).toString
    s"PING #time $timestamp @$receiver ^$sender"
  }

  /**
    * Check weather the schema feature enabled
    * Flag set via platform recipe
    *
    * @return feature enabled or not
    */
  def enableFeature(feature: String): Boolean = {
    sys.env.getOrElse(feature, "false").toBoolean
  }
}
