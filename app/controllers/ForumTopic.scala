package controllers

import lila.app._
import lila.forum.CategRepo
import views._

object ForumTopic extends LilaController with ForumController {

  def form(categSlug: String) = Open { implicit ctx ⇒
    CategGrantWrite(categSlug) {
      OptionFuOk(CategRepo bySlug categSlug) { categ ⇒
        forms.anyCaptcha map { html.forum.topic.form(categ, forms.topic, _) }
      }
    }
  }

  def create(categSlug: String) = OpenBody { implicit ctx ⇒
    CategGrantWrite(categSlug) {
      implicit val req = ctx.body
      OptionFuResult(CategRepo bySlug categSlug) { categ ⇒
        forms.topic.bindFromRequest.fold(
          err ⇒ forms.anyCaptcha map { captcha ⇒
            BadRequest(html.forum.topic.form(categ, err, captcha))
          },
          data ⇒ Firewall {
            topicApi.makeTopic(categ, data) map { topic ⇒
              Redirect(routes.ForumTopic.show(categ.slug, topic.slug, 1))
            }
          }
        )
      }
    }
  }

  def show(categSlug: String, slug: String, page: Int) = Open { implicit ctx ⇒
    CategGrantRead(categSlug) {
      OptionFuOk(topicApi.show(categSlug, slug, page)) {
        case (categ, topic, posts) ⇒ isGrantedWrite(categSlug) flatMap { granted ⇒
          (!posts.hasNextPage && granted && topic.open) ?? forms.postWithCaptcha.map(_.some) map { form ⇒
            html.forum.topic.show(categ, topic, posts, form)
          }
        }
      }
    }
  }

  def close(categSlug: String, slug: String) =
    Secure(_.ModerateForum) { implicit ctx ⇒
      me ⇒
        OptionFuRedirect(topicApi.show(categSlug, slug, 1)) {
          case (categ, topic, pag) ⇒ topicApi.toggleClose(categ, topic, me) inject
            routes.ForumTopic.show(categSlug, slug, pag.nbPages)
        }
    }
}
