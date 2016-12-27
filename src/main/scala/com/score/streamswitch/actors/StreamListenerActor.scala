package com.score.streamswitch.actors

import java.io.{PrintWriter, StringWriter}
import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.io.{IO, Udp}
import com.score.streamswitch.actors.StreamHandlerActor.Start
import com.score.streamswitch.config.AppConfig
import com.score.streamswitch.protocols.{Ref, Senz, SenzMsg, SenzType}
import com.score.streamswitch.utils.SenzParser
import org.slf4j.LoggerFactory

object StreamListenerActor {
  val actorRefs = scala.collection.mutable.LinkedHashMap[String, Ref]()

  def props(): Props = Props(classOf[StreamListenerActor])
}

class StreamListenerActor extends Actor with AppConfig {

  import context.system

  def logger = LoggerFactory.getLogger(this.getClass)

  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(switchPort))

  override def preStart() = {
    logger.info("[_________START ACTOR__________] " + context.self.path)
  }

  override def postStop() = {
    logger.info("[_________STOP ACTOR__________] " + context.self.path)
  }

  override def supervisorStrategy = OneForOneStrategy() {
    case e: Exception =>
      logger.error("Exception caught, [STOP ACTOR] " + e)
      logFailure(e)

      // stop failed actors here
      Stop
  }

  override def receive = {
    case Udp.Bound(local) =>
      logger.debug(s"Bound socket ")
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      val msg = data.decodeString("UTF-8")
      logger.debug(s"Received data $msg")
      logger.debug(s"Received from address ${remote.getAddress} port ${remote.getPort}")

      // parse data and obtain senz
      val senz = SenzParser.parseSenz(msg)

      // on init
      senz match {
        case Senz(SenzType.DATA, s, `switchName`, attr, _) =>
          // TODO verify signature

          // create new actor and put to store
          attr.get("#STREAM") match {
            case Some("ON") =>
              // init new handler
              val handler = context.actorOf(StreamHandlerActor.props(socket))
              handler ! Start(s, remote)
            case e =>
              // not support
              logger.debug(s"Unsupported STREAM $e")
          }
        case Senz(SenzType.STREAM, s, _, _, _) =>
          // there should be an actor, if not its a failure
          // forward message to actor
          StreamListenerActor.actorRefs(s).actorRef ! SenzMsg(senz, msg)
      }
    case Udp.Unbind =>
      socket ! Udp.Unbind
    case Udp.Unbound =>
      context.stop(self)
  }

  private def logFailure(throwable: Throwable) = {
    val writer = new StringWriter
    throwable.printStackTrace(new PrintWriter(writer))
    logger.error(writer.toString)
  }
}


