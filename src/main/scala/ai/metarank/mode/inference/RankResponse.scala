package ai.metarank.mode.inference

import ai.metarank.mode.inference.RankResponse.{ItemScore, StateValues}
import ai.metarank.model.FeatureScope.{ItemScope, SessionScope, TenantScope, UserScope}
import ai.metarank.model.{FeatureScope, ItemId, MValue}
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import io.findify.featury.model.FeatureValue

case class RankResponse(state: StateValues, items: List[ItemScore])

object RankResponse {
  case class StateValues(
      session: List[FeatureValue],
      user: List[FeatureValue],
      tenant: List[FeatureValue],
      item: List[FeatureValue]
  )
  object StateValues {
    def apply(values: List[FeatureValue]) = {
      new StateValues(
        session = values.filter(_.key.tag.scope == SessionScope.scope),
        user = values.filter(_.key.tag.scope == UserScope.scope),
        tenant = values.filter(_.key.tag.scope == TenantScope.scope),
        item = values.filter(_.key.tag.scope == ItemScope.scope)
      )
    }
  }

  import io.findify.featury.model.json.FeatureValueJson._
  case class ItemScore(item: ItemId, score: Double, features: List[MValue])
  implicit val itemScoreCodec: Codec[ItemScore]       = deriveCodec
  implicit val stateValuesCodec: Codec[StateValues]   = deriveCodec
  implicit val rankResponseCodec: Codec[RankResponse] = deriveCodec
}
