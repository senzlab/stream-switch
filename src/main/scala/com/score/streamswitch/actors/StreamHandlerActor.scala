package com.score.streamswitch.actors

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, Props}
import akka.io.Udp
import akka.util.ByteString
import com.score.streamswitch.actors.StreamHandlerActor.{Init, StartStream, StopStream}
import com.score.streamswitch.protocols._
import org.slf4j.LoggerFactory


object StreamHandlerActor {

  case class Init(name: String, remote: InetSocketAddress)

  case class StartStream(toRef: Ref)

  case class StopStream()

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
    case Init(n, r) =>
      // initialize name and remote
      name = n
      remote = r

      // create ref with handler and put it to ref store
      val ref = Ref(self)
      StreamListenerActor.refs.put(name, ref)

      logger.info(s"Init Handler with name $name remote(${remote.getAddress}, ${remote.getPort})")
    case StartStream(toRef) =>
      StreamListenerActor.streamRefs.put(remote, toRef)

      logger.info(s"Stream started with remote: ${remote.getAddress}, ${remote.getPort}")
    case StopStream =>
      // remove from store
      StreamListenerActor.refs.remove(name)
      StreamListenerActor.streamRefs.remove(remote)

      logger.info(s"Stream stopped with remote: ${remote.getAddress}, ${remote.getPort}")
    case Msg(data) =>
      logger.debug(s"Send data $data to $name")
      socket ! Udp.Send(ByteString(data), remote)
  }
}
