package com.score.streamswitch.actors

import java.io.{PrintWriter, StringWriter}
import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.io.{IO, Udp}
import com.score.streamswitch.actors.StreamHandlerActor.{Init, StopStream}
import com.score.streamswitch.config.AppConfig
import com.score.streamswitch.protocols._
import com.score.streamswitch.utils.SenzParser
import org.slf4j.LoggerFactory

import scala.util.{Success, Try}

object StreamListenerActor {
  val refs = scala.collection.mutable.LinkedHashMap[String, Ref]()
  val streamRefs = scala.collection.mutable.LinkedHashMap[InetSocketAddress, Ref]()

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
      logger.debug(s"Received data $msg from ${remote.getAddress}, to ${remote.getPort}")

      SenzParser.parse(msg) match {
        case Success(stream) =>
          // forward to sender and receiver
          Try {
            StreamListenerActor.refs(stream.receiver).actorRef ! Msg(stream.data)
          }
        case _ =>
          // match for DATA #STREAM ON ...
          SenzParser.parseSenz(msg) match {
            case Success(Senz(SenzType.DATA, s, `switchName`, attr, _)) =>
              // create new actor and put to store
              attr("#STREAM") match {
                case "ON" =>
                  val handler = context.actorOf(StreamHandlerActor.props(socket))
                  handler ! Init(s, remote)
                case "OFF" =>
                  Try {
                    StreamListenerActor.refs(s).actorRef ! StopStream
                  }
              }
            case e =>
              logger.error(s"unsupported msg $e")
          }
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


