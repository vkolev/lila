package lila.tournament

import lila.socket.{ SocketActor, History, Historical }
import lila.socket.actorApi.{ Connected ⇒ _, _ }
import lila.memo.ExpireSetMemo
import actorApi._

import scala.concurrent.duration.Duration
import akka.actor._
import play.api.libs.iteratee._
import play.api.libs.json._

private[tournament] final class Socket(
    tournamentId: String,
    val history: History,
    messenger: Messenger,
    getUsername: String ⇒ Fu[Option[String]],
    uidTimeout: Duration,
    socketTimeout: Duration) extends SocketActor[Member](uidTimeout) with Historical[Member] {

  val joiningMemo = new ExpireSetMemo(uidTimeout)

  var lastPingTime = nowMillis

  def receiveSpecific = {

    case StartGame(game) ⇒ game.players foreach { player ⇒
      for {
        userId ← player.userId
        member ← memberByUserId(userId)
      } {
        notifyMember("redirect", game fullIdOf player.color)(member)
        notifyReload
      }
    }

    case Reload     ⇒ notifyReload

    case Start      ⇒ notifyVersion("start", JsNull)

    case ReloadPage ⇒ notifyVersion("reloadPage", JsNull)

    case PingVersion(uid, v) ⇒ {
      ping(uid)
      lastPingTime = nowMillis
      withMember(uid) { m ⇒
        history.since(v).fold(resync(m))(_ foreach m.channel.push)
      }
    }

    case Broom ⇒ {
      broom()
      if (lastPingTime < (nowMillis - socketTimeout.toMillis)) {
        context.parent ! CloseSocket(tournamentId)
      }
    }

    case Talk(tourId, u, t) ⇒ messenger(tourId, u, t) effectFold (
      e ⇒ logwarn(e.toString),
      message ⇒ notifyVersion("talk", Json.obj(
        "u" -> message.userId,
        "t" -> message.text
      ))
    )

    case GetVersion ⇒ sender ! history.version

    case Join(uid, user, version) ⇒ {
      val (enumerator, channel) = Concurrent.broadcast[JsValue]
      val member = Member(channel, user)
      addMember(uid, member)
      notifyCrowd
      sender ! Connected(enumerator, member)
    }

    case Quit(uid) ⇒ {
      quit(uid)
      notifyCrowd
    }

    case Close ⇒ {
      members.values foreach { _.channel.end() }
      self ! PoisonPill
    }

    case Joining(userId) ⇒ joiningMemo put userId
  }

  override def userIds = (super.userIds ++ joiningMemo.keys).toList.distinct

  def notifyCrowd {
    members.values.map(_.userId).toList.partition(_.isDefined) match {
      case (users, anons) ⇒
        (users.flatten.distinct map getUsername).sequence foreach { userList ⇒
          notifyVersion("crowd", showSpectators(userList.flatten, anons.size))
        }
    }
  }

  def notifyReload {
    notifyVersion("reload", JsNull)
  }
}
