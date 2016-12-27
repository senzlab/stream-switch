package com.score.streamswitch.config

trait DbConfig {
  val client = MongoFactory.client
  val senzDb = MongoFactory.senzDb
  val coll = MongoFactory.coll
}
