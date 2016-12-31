package com.score.streamswitch.actors

import java.io.{PrintWriter, StringWriter}
import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.io.{IO, Udp}
import com.score.streamswitch.actors.StreamHandlerActor.{Start, StreamRef}
import com.score.streamswitch.config.AppConfig
import com.score.streamswitch.protocols._
import com.score.streamswitch.utils.SenzParser
import org.slf4j.LoggerFactory

import scala.util.{Success, Try}

object StreamListenerActor {
  val refs = scala.collection.mutable.LinkedHashMap[String, Ref]()
  val streamRefs = scala.collection.mutable.LinkedHashMap[String, StreamRef]()

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
      logger.info(s"Bound socket ")
      context.become(ready(sender()))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      val msg = data.decodeString("UTF-8")
      logger.debug(s"Received data $msg")
      logger.debug(s"Received from address ${remote.getAddress} port ${remote.getPort}")

      // parse data and obtain senz
      val senz = Try {
        SenzParser.parseSenz(msg)
      }
      senz match {
        case Success(Senz(SenzType.DATA, s, `switchName`, attr, _)) =>
          // TODO verify signature

          // create new actor and put to store
          attr("#STREAM") match {
            case "ON" =>
              // init new handler
              val handler = context.actorOf(StreamHandlerActor.props(socket))
              handler ! Start(s, remote)

              // to receiver
              val to = attr("#TO")

              // create streams
              StreamListenerActor.refs.get(to) match {
                case Some(toRef) =>
                  // fromRef(handler) to create stream with to
                  handler ! StreamRef(toRef)

                  // toRef to create stream with from(handler)
                  toRef.actorRef ! StreamRef(Ref(handler))
                case None =>
                  // do nothing
                  logger.info(s"Still no to $to connected")
              }
            case "OFF" =>
              // TODO remove from to from refs
              // TODO remove streams
            case e =>
              // not support
              logger.debug(s"Unsupported STREAM $e")
          }
        case _ =>
          logger.error(s"Unsupported msg $msg")

          // forward message
          val peer = s"${remote.getHostName}:${remote.getPort}"
          StreamListenerActor.streamRefs(peer).toRef.actorRef ! Msg(msg)
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


