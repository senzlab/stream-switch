package com.score.streamswitch.utils

import com.score.streamswitch.protocols.{Senz, SenzType}

import scala.util.Try

object SenzParser {

  def parseSenz(senzMsg: String) = {
    Try {
      val tokens = senzMsg.trim.split(" ")

      val senzType = SenzType.withName(tokens.head.trim)
      val signature = Some(tokens.last.trim)
      val sender = tokens.find(_.startsWith("^")).get.trim.substring(1)
      val receiver = tokens.find(_.startsWith("@")).get.trim.substring(1)
      val attr = getAttr(tokens.drop(1).dropRight(3))

      Senz(senzType, sender, receiver, attr, signature)
    }
  }

  private def getAttr(tokens: Array[String], attr: Map[String, String] = Map[String, String]()): Map[String, String] = {
    tokens match {
      case Array() =>
        // empty array
        attr
      case Array(_) =>
        // last index
        if (tokens(0).startsWith("#")) attr + (tokens(0) -> "") else attr
      case Array(_, _*) =>
        // have at least two elements
        if (tokens(0).startsWith("$")) {
          // $key 5.23
          getAttr(tokens.drop(2), attr + (tokens(0) -> tokens(1)))
        } else if (tokens(0).startsWith("#")) {
          if (tokens(1).startsWith("#") || tokens(1).startsWith("$")) {
            // #lat $key 23.23
            // #lat #lon
            getAttr(tokens.drop(1), attr + (tokens(0) -> ""))
          } else {
            // #lat 3.342
            getAttr(tokens.drop(2), attr + (tokens(0) -> tokens(1)))
          }
        } else {
          attr
        }
    }
  }

  def composeSenz(senz: Senz): String = {
    // attributes comes as
    //    1. #lat 3.432 #lon 23.343
    //    2. #lat #lon
    var attr = ""
    for ((k, v) <- senz.attributes) {
      attr += s"$k $v".trim + " "
    }

    s"${senz.senzType} ${attr.trim} @${senz.receiver} ^${senz.sender} ${senz.signature}"
  }

}

