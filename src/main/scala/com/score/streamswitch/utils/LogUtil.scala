package com.score.streamswitch.utils

import java.io.{PrintWriter, StringWriter}

import org.slf4j.LoggerFactory

object LogUtil {
  def logger = LoggerFactory.getLogger(this.getClass)

  def logFailure(throwable: Throwable) = {
    val writer = new StringWriter
    throwable.printStackTrace(new PrintWriter(writer))
    logger.error(writer.toString)
  }
}
