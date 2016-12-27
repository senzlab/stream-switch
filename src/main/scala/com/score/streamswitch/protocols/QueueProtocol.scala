package com.score.streamswitch.protocols

import akka.actor.ActorRef
import com.score.streamswitch.protocols.QueueType.QueueType

object QueueType extends Enumeration {
  type QueueType = Value
  val SHARE, GET, DATA, STREAM = Value
}

case class Enqueue(qObj: QueueObj)

case class Dequeue(uid: String)

case class QueueObj(uid: String, queueType: QueueType, senzMsg: SenzMsg)

case class Dispatch(actorRef: ActorRef, user: String)

