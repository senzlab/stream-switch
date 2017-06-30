package com.score.streamswitch.actors

import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.io.{IO, Udp}
import com.score.streamswitch.config.AppConfig
import com.score.streamswitch.protocols._
import com.score.streamswitch.utils.{LogUtil, SenzParser}
import org.slf4j.LoggerFactory

import scala.util.Success

object StreamListenerActor {

  case class ClientRef(remote: InetSocketAddress, actorRef: ActorRef)

  val nameRefs = scala.collection.mutable.LinkedHashMap[String, ClientRef]()
  val portRefs = scala.collection.mutable.LinkedHashMap[Integer, ClientRef]()

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
      logger.debug(s"Received data $msg from ${remote.getAddress}:${remote.getPort}")

      if (msg.startsWith("DATA")) {
        // this should be DATA #STREAM on #TO eranga SIG
        // match for stream on/off
        SenzParser.parseSenz(msg) match {
          case Success(Senz(SenzType.DATA, from, `switchName`, attr, _)) =>
            attr("#STREAM") match {
              // DATA #STREAM ON
              case "ON" =>
                val to = attr("#TO")

                nameRefs.get(to) match {
                  case Some(ref) =>
                    // have 'to' ref
                    // put portRefs
                    portRefs.put(remote.getPort, ref)
                    portRefs.put(ref.remote.getPort, ClientRef(remote, socket))
                  case None =>
                    // no 'to' ref yet
                    // just put nameRef
                    nameRefs.put(from, ClientRef(remote, socket))
                }
              case "OFF" =>
                // DATA #STREAM OFF
                // remove from portRefs, nameRefs
                portRefs.remove(remote.getPort)
                nameRefs.remove(from)
            }
          case e =>
            logger.error(s"unsupported msg $e")
        }
      } else {
        // this should be base64 encoded audio payload
        // directly send them to receiver
        // receiver identifies via port
        val ref = portRefs(remote.getPort)
        ref.actorRef ! msg
      }
    case Udp.Unbind =>
      socket ! Udp.Unbind
    case Udp.Unbound =>
      context.stop(self)
  }

}


