package com.score.streamswitch.actors

import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.io.{IO, Udp}
import akka.util.ByteString
import com.score.streamswitch.config.AppConfig
import com.score.streamswitch.protocols._
import com.score.streamswitch.utils.{LogUtil, SenzParser}
import org.slf4j.LoggerFactory

import scala.util.{Success, Try}

object StreamListenerActor {

  case class ClientRef(remote: InetSocketAddress, ref: ActorRef)

  val clientRefs = scala.collection.mutable.LinkedHashMap[String, ClientRef]()

  def props(): Props = Props(classOf[StreamListenerActor])
}

class StreamListenerActor extends Actor with AppConfig {

  import StreamListenerActor._
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
      LogUtil.logFailure(e)

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
          Try {
            // forward receiver
            val clientRef = clientRefs(stream.receiver)
            clientRef.ref ! Udp.Send(ByteString(data), clientRef.remote)
          }
        case _ =>
          // match for stream on/off
          // DATA #STREAM ON
          // DATA #STREAM OFF
          SenzParser.parseSenz(msg) match {
            case Success(Senz(SenzType.DATA, s, `switchName`, attr, _)) =>
              attr("#STREAM") match {
                case "ON" =>
                  // put to store
                  clientRefs.put(s, ClientRef(remote, socket))
                case "OFF" =>
                  // remove from store
                  clientRefs.remove(s)
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

}


