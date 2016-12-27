package com.score.streamswitch.actors

import java.net.InetSocketAddress

import com.score.streamswitch.actors.StreamHandlerActor.{Start, Stop}
import akka.actor.{Actor, ActorRef, Props}
import akka.io.Udp
import akka.util.ByteString
import com.score.streamswitch.protocols._
import org.slf4j.LoggerFactory


object StreamHandlerActor {

  case class Start(name: String, remote: InetSocketAddress)

  case class Stop(name: String)

  def props(socket: ActorRef): Props = Props(classOf[StreamHandlerActor], socket)

}

class StreamHandlerActor(socket: ActorRef) extends Actor {

  def logger = LoggerFactory.getLogger(this.getClass)

  var name: String = _

  var remote: InetSocketAddress = _

  override def preStart() = {
    logger.info("[_________START ACTOR__________] " + context.self.path)
  }

  override def postStop() = {
    logger.info("[_________STOP ACTOR__________] " + context.self.path)
  }

  override def receive = {
    case Start(n, r) =>
      // initialize name and remote
      name = n
      remote = r

      // put to store
      val ref = Ref(self)
      StreamListenerActor.actorRefs.put(name, ref)

      logger.info(s"Handler started with name $name remote(${remote.getAddress}, ${remote.getPort})")
    case Stop(n) =>
      // remove from store
      StreamListenerActor.actorRefs.remove(n)

      logger.info(s"handler stopped with name $name")
    case SenzMsg(Senz(SenzType.STREAM, _, receiver, attr, _), data) =>
      // only handle stream from here
      // forward message to receiver
      StreamListenerActor.actorRefs(receiver).actorRef ! Msg(data)
    case Msg(data) =>
      logger.debug(s"Send data $data to $name")
      socket ! Udp.Send(ByteString(data), remote)
  }
}
