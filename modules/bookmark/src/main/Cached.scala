package lila.bookmark

import spray.caching.{ LruCache, Cache }

private[bookmark] final class Cached {

  def bookmarked(gameId: String, userId: String): Fu[Boolean] =
    userGameIds(userId) map (_ contains gameId)

  def count(userId: String): Fu[Int] =
    userGameIds(userId) map (_.size)

  def invalidateUserId(userId: String) {
    gameIdsCache.remove(userId)
  }

  private def userGameIds(userId: String): Fu[Set[String]] =
    gameIdsCache.fromFuture(userId.toLowerCase) {
      BookmarkRepo gameIdsByUserId userId.toLowerCase map (_.toSet)
    }

  private val gameIdsCache: Cache[Set[String]] = LruCache(maxCapacity = 99999)
}
