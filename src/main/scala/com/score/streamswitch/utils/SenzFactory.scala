package com.score.streamswitch.utils

import ch.qos.logback.classic.{Level, Logger}
import com.score.streamswitch.components.{CryptoCompImpl, KeyStoreCompImpl}
import com.score.streamswitch.config.{AppConfig, DbConfig}
import org.slf4j.LoggerFactory

object SenzFactory extends CryptoCompImpl with KeyStoreCompImpl with DbConfig with AppConfig {
  val setupLogging = () => {
    val rootLogger = LoggerFactory.getLogger("root").asInstanceOf[Logger]

    switchMode match {
      case "DEV" =>
        rootLogger.setLevel(Level.DEBUG)
      case "PROD" =>
        rootLogger.setLevel(Level.INFO)
      case _ =>
        rootLogger.setLevel(Level.INFO)
    }
  }

  val setupKeys = () => {
    crypto.initKeys()
  }
}
