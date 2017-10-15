package com.score.streamswitch.actors

import java.net.InetSocketAddress

import akka.actor.SupervisorStrategy.Resume
import akka.actor.{Actor, ActorRef, OneForOneStrategy, Props}
import akka.io.{IO, Udp}
import akka.util.ByteString
import com.score.streamswitch.config.AppConfig
import com.score.streamswitch.protocols._
import com.score.streamswitch.utils.{LogUtil, SenzParser}
import org.slf4j.LoggerFactory

import scala.util.Success

object StreamListenerActor {

  case class SocketRef(remote: InetSocketAddress, actorRef: ActorRef)

  val sendRefs = scala.collection.mutable.LinkedHashMap[String, SocketRef]()
  val recvRefs = scala.collection.mutable.LinkedHashMap[String, SocketRef]()
  val socketRefs = scala.collection.mutable.LinkedHashMap[Integer, SocketRef]()

  def props(): Props = Props(classOf[StreamListenerActor])
}

class StreamListenerActor extends Actor with AppConfig {

  import StreamListenerActor._
  import context.system

  def logger = LoggerFactory.getLogger(this.getClass)

  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(switchPort))

  override def supervisorStrategy = OneForOneStrategy() {
    case e: Exception =>
      logger.error("Exception caught, [STOP ACTOR] " + e)
      LogUtil.logFailure(e)

      // stop failed actors here
      Resume
  }

  override def receive = {
    case Udp.Bound(local) =>
      logger.info(s"Bound socket ")

      context.become(ready(sender()))
  }

  def ready(actorRef: ActorRef): Receive = {
    case Udp.Received(data, remote) =>
      val msg = data.decodeString("UTF-8")
      logger.debug(s"Received data $msg from ${remote.getAddress}:${remote.getPort}")

      if (msg.startsWith("DATA")) {
        // this should be
        // DATA #STREAM O #TO eranga SIG
        // DATA #STREAM N #TO eranga SIG
        SenzParser.parseSenz(msg) match {
          case Success(Senz(SenzType.DATA, from, `switchName`, attr, _)) =>
            attr.get("#STREAM") match {
              case Some("O") =>
                // DATA #STREAM O
                // send ref
                put(from, attr("#TO"), SocketRef(remote, actorRef), o = true)
              case Some("N") =>
                // recv ref
                // DATA #STREAM N
                put(from, attr("#TO"), SocketRef(remote, actorRef), o = false)
              case Some("OFF") =>
                logger.debug(s"Stream off")

                // DATA #STREAM OFF
                // remove from socketRefs, clientRefs
                socketRefs.remove(remote.getPort)

                // remove refs
                sendRefs.remove(from)
                recvRefs.remove(from)
              case e =>
                logger.debug(s"Unrecognized stream $e")
            }
          case e =>
            logger.error(s"unsupported msg $e")
        }
      } else {
        // this should be base64 encoded audio payload
        // directly send them to receiver
        // receiver identifies via port
        val ref = socketRefs(remote.getPort)
        ref.actorRef ! Udp.Send(ByteString(msg), ref.remote)
      }
    case Udp.Unbind =>
      actorRef ! Udp.Unbind
    case Udp.Unbound =>
      context.stop(self)
  }

  def put(from: String, to: String, socketRef: SocketRef, o: Boolean) = {
    // put ref to appropriate place
    if (o) sendRefs.put(from, socketRef)
    else recvRefs.put(from, socketRef)

    (sendRefs.get(from), recvRefs.get(from), sendRefs.get(to), recvRefs.get(to)) match {
      case (Some(sfref), Some(rfref), Some(stref), Some(rtref)) =>
        logger.debug("All refs filled, populate socket refs now")

        // have all refs,
        // put socket refs
        socketRefs.put(sfref.remote.getPort, rtref)
        socketRefs.put(stref.remote.getPort, rfref)
      case _ =>
        logger.debug("Still refs not filled")
    }
  }

}


