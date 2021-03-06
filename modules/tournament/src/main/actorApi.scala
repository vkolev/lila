package lila.tournament
package actorApi

import lila.socket.SocketMember
import lila.user.User
import lila.game.Game

case class Member(
    channel: JsChannel,
    userId: Option[String],
    muted: Boolean) extends SocketMember {

  def canChat = !muted
}

object Member {
  def apply(channel: JsChannel, user: Option[User]): Member = Member(
    channel = channel,
    userId = user map (_.id),
    muted = user.zmap(_.muted))
}

case class Join(
  uid: String,
  user: Option[User],
  version: Int)
case class Talk(tourId: String, u: String, t: String)
case object Start
case object Reload
case object ReloadPage
case object HubTimeout
case class StartGame(game: Game)
case class Joining(userId: String)
case class Connected(enumerator: JsEnumerator, member: Member)

// organizer
case object CreatedTournaments
case object StartedTournaments
case class RemindTournaments(tours: List[Started])
case class RemindTournament(tour: Started)
case class TournamentTable(tours: List[Created])
