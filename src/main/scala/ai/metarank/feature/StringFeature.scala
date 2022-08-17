package ai.metarank.feature

import ai.metarank.feature.BaseFeature.ItemFeature
import ai.metarank.feature.StringFeature.EncoderName.{IndexEncoderName, OnehotEncoderName}
import ai.metarank.feature.StringFeature.{IndexCategoricalEncoder, OnehotCategoricalEncoder, StringFeatureSchema}
import ai.metarank.fstore.Persistence
import ai.metarank.model.Event.ItemRelevancy
import ai.metarank.model.Feature.FeatureConfig
import ai.metarank.model.Feature.ScalarFeature.ScalarConfig
import ai.metarank.model.FeatureValue.ScalarValue
import ai.metarank.model.Field.{NumberField, StringField, StringListField}
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.MValue.{CategoryValue, SingleValue, VectorValue}
import ai.metarank.model.Scalar.SStringList
import ai.metarank.model.Write.Put
import ai.metarank.model.{Event, FeatureSchema, FeatureValue, FieldName, Key, MValue, ScopeType}
import ai.metarank.util.{Logging, OneHotEncoder}
import cats.data.NonEmptyList
import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._

import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class StringFeature(schema: StringFeatureSchema) extends ItemFeature with Logging {
  val encoder = schema.encode match {
    case OnehotEncoderName =>
      OnehotCategoricalEncoder(
        name = schema.name,
        possibleValues = schema.values.toList,
        dim = schema.values.size
      )
    case IndexEncoderName =>
      IndexCategoricalEncoder(
        name = schema.name,
        possibleValues = schema.values.toList
      )
  }
  override def dim: Int = encoder.dim

  private val conf = ScalarConfig(
    scope = schema.scope,
    name = schema.name,
    refresh = schema.refresh.getOrElse(0.seconds),
    ttl = schema.ttl.getOrElse(90.days)
  )
  override def states: List[FeatureConfig] = List(conf)

  override def writes(event: Event, fields: Persistence): IO[Iterable[Put]] = IO {
    for {
      key   <- writeKey(event, conf)
      field <- event.fields.find(_.name == schema.source.field)
      fieldValue <- field match {
        case StringField(_, value)     => Some(SStringList(List(value)))
        case StringListField(_, value) => Some(SStringList(value))
        case other =>
          logger.warn(s"field extractor ${schema.name} expects a string or string[], but got $other in event $event")
          None
      }
    } yield {
      Put(key, event.timestamp, fieldValue)
    }
  }

  override def valueKeys(event: Event.RankingEvent): Iterable[Key] = conf.readKeys(event)

  // todo: should load field directly from ranking
  override def value(
      request: Event.RankingEvent,
      features: Map[Key, FeatureValue],
      id: ItemRelevancy
  ): MValue = {
    readKey(request, conf, id.id).flatMap(features.get) match {
      case Some(ScalarValue(_, _, SStringList(values))) => encoder.encode(values)
      case _                                            => encoder.encode(Nil)
    }
  }

}

object StringFeature {
  import ai.metarank.util.DurationJson._

  sealed trait CategoricalEncoder {
    def dim: Int
    def encode(values: Seq[String]): MValue
  }

  case class OnehotCategoricalEncoder(name: FeatureName, possibleValues: List[String], dim: Int)
      extends CategoricalEncoder {
    override def encode(values: Seq[String]): VectorValue =
      VectorValue(name, OneHotEncoder.fromValues(values, possibleValues, dim), dim)
  }
  case class IndexCategoricalEncoder(name: FeatureName, possibleValues: List[String]) extends CategoricalEncoder {
    override val dim = 1
    override def encode(values: Seq[String]): CategoryValue = {
      values.headOption match {
        case Some(first) =>
          val index = possibleValues.indexOf(first)
          CategoryValue(name, first, index + 1) // zero is
        case None =>
          CategoryValue(name, "nil", 0)
      }
    }
  }

  sealed trait EncoderName
  object EncoderName {
    case object OnehotEncoderName extends EncoderName
    case object IndexEncoderName  extends EncoderName
    implicit val methodNameDecoder: Decoder[EncoderName] = Decoder.decodeString.emapTry {
      case "index"  => Success(IndexEncoderName)
      case "onehot" => Success(OnehotEncoderName)
      case other    => Failure(new Exception(s"string encoding method $other is not supported"))
    }
  }

  case class StringFeatureSchema(
      name: FeatureName,
      source: FieldName,
      scope: ScopeType,
      encode: EncoderName = IndexEncoderName,
      values: NonEmptyList[String],
      refresh: Option[FiniteDuration] = None,
      ttl: Option[FiniteDuration] = None
  ) extends FeatureSchema

  object StringFeatureSchema {
    implicit val conf: Configuration = Configuration.default.withDefaults
    implicit val stringSchemaDecoder: Decoder[StringFeatureSchema] =
      deriveConfiguredDecoder[StringFeatureSchema].withErrorMessage(
        "cannot parse a feature definition of type 'string'"
      )
  }

}
