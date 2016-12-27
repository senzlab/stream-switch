package com.score.streamswitch.boot

import com.score.streamswitch.actors.StreamListenerActor
import akka.actor.ActorSystem
import com.score.streamswitch.utils.SenzFactory

/**
  * Created by eranga on 1/9/16.
  */
object Main extends App {

  // setup logging
  SenzFactory.setupLogging()

  // setup keys
  SenzFactory.setupKeys()

  // start actor
  implicit val system = ActorSystem("senz")
  system.actorOf(StreamListenerActor.props(), name = "StreamListener")

}
