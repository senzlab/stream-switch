package com.score.streamswitch.config

import com.typesafe.config.ConfigFactory

import scala.util.Try

/**
  * Load configurations define in application.conf from here
  *
  * @author eranga herath(erangaeb@gmail.com)
  */
trait AppConfig {
  // config object
  val config = ConfigFactory.load()

  // switch config
  lazy val switchMode = Try(config.getString("switch.mode")).getOrElse("DEV")
  lazy val switchName = Try(config.getString("switch.name")).getOrElse("streamswitch")
  lazy val switchPort = Try(config.getInt("switch.port")).getOrElse(9090)

  // keys config
  lazy val keysDir = Try(config.getString("keys.dir")).getOrElse(".keys")
  lazy val publicKeyLocation = Try(config.getString("keys.public-key-location")).getOrElse(".keys/id_rsa.pub")
  lazy val privateKeyLocation = Try(config.getString("keys.private-key-location")).getOrElse(".keys/id_rsa")
}
