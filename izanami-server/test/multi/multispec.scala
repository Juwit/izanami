package multi

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import akka.testkit.SocketUtil._
import com.typesafe.config.ConfigFactory
import controllers.{UserControllerSpec, _}
import elastic.client.ElasticClient
import libs.IdGenerator
import org.iq80.leveldb.util.FileUtils
import org.scalatest._
import play.api.Configuration
import play.api.libs.json.JsValue

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationLong
import scala.util.{Random, Try}
import Configs.idGenerator

object Configs {

  val idGenerator = IdGenerator(0L)

  val redisPort: Int = 6380

  val cassandraPort: Int = 9042

  val elasticHttpPort: Int = 9210

  val elasticConfiguration: Configuration = Configuration(
    ConfigFactory.parseString(s"""
      |izanami.db.default="Elastic"
      |izanami.patchEnabled = false
      |izanami.mode= "test"
      |izanami.config.db.type=$${izanami.db.default}
      |izanami.features.db.type=$${izanami.db.default}
      |izanami.globalScript.db.type=$${izanami.db.default}
      |izanami.experiment.db.type=$${izanami.db.default}
      |izanami.experimentEvent.db.type=$${izanami.db.default}
      |izanami.webhook.db.type=$${izanami.db.default}
      |izanami.user.db.type=$${izanami.db.default}
      |izanami.apikey.db.type=$${izanami.db.default}
      |izanami.patch.db.type=$${izanami.db.default}
      |
      |izanami {
      |  db {
      |    elastic {
      |      host = "localhost"
      |      port = $elasticHttpPort
      |      automaticRefresh = true
      |    }
      |  }
      |}
      """.stripMargin).resolve()
  )

  def redisConfiguration: Configuration = Configuration(
    ConfigFactory.parseString(s"""
         |izanami.db.default="Redis"
         |izanami.patchEnabled = false
         |izanami.mode= "test"
         |izanami.namespace="izanami-${Random.nextInt(1000)}"
         |izanami.config.db.type=$${izanami.db.default}
         |izanami.config.db.conf.namespace="izanami-${Random.nextInt(1000)}:configuration"
         |izanami.features.db.type=$${izanami.db.default}
         |izanami.features.db.conf.namespace="izanami-${Random.nextInt(1000)}:feature"
         |izanami.globalScript.db.type=$${izanami.db.default}
         |izanami.globalScript.db.conf.namespace="izanami-${Random.nextInt(1000)}:script"
         |izanami.experiment.db.type=$${izanami.db.default}
         |izanami.experiment.db.conf.namespace="izanami-${Random.nextInt(1000)}:experiment"
         |izanami.experimentEvent.db.type=$${izanami.db.default}
         |izanami.experimentEvent.db.conf.namespace="izanami-${Random.nextInt(1000)}:events"
         |izanami.webhook.db.type=$${izanami.db.default}
         |izanami.webhook.db.conf.namespace="izanami-${Random.nextInt(1000)}:hooks"
         |izanami.user.db.type=$${izanami.db.default}
         |izanami.user.db.conf.namespace="izanami-${Random.nextInt(1000)}:user"
         |izanami.apikey.db.type=$${izanami.db.default}
         |izanami.apikey.db.conf.namespace="izanami-${Random.nextInt(1000)}:apikey"
         |izanami.patch.db.type=$${izanami.db.default}
         |
         |izanami {
         |  db {
         |    redis {
         |      host = "localhost"
         |      port = $redisPort
         |      windowSize = 99
         |      transaction = false
         |      fastLookupTTL = 60000
         |    }
         |  }
         |}
      """.stripMargin).resolve()
  )

  def cassandraConfiguration(keyspace: String): Configuration = Configuration(
    ConfigFactory.parseString(s"""
         |izanami.db.default="Cassandra"
         |izanami.patchEnabled = false
         |izanami.mode= "test"
         |izanami.config.db.type=$${izanami.db.default}
         |izanami.features.db.type=$${izanami.db.default}
         |izanami.globalScript.db.type=$${izanami.db.default}
         |izanami.experiment.db.type=$${izanami.db.default}
         |izanami.experimentEvent.db.type=$${izanami.db.default}
         |izanami.webhook.db.type=$${izanami.db.default}
         |izanami.user.db.type=$${izanami.db.default}
         |izanami.apikey.db.type=$${izanami.db.default}
         |izanami.patch.db.type=$${izanami.db.default}
         |
         |izanami {
         |  db {
         |    cassandra {
         |      addresses = ["127.0.0.1:$cassandraPort"]
         |      replicationFactor = 1
         |      keyspace: "$keyspace"
         |    }
         |  }
         |}
      """.stripMargin).resolve()
  )

  def levelDBConfiguration(folder: String): Configuration = Configuration(
    ConfigFactory.parseString(s"""
         |izanami.db.default="LevelDB"
         |izanami.patchEnabled = false
         |izanami.mode= "test"
         |izanami.config.db.type=$${izanami.db.default}
         |izanami.features.db.type=$${izanami.db.default}
         |izanami.globalScript.db.type=$${izanami.db.default}
         |izanami.experiment.db.type=$${izanami.db.default}
         |izanami.experimentEvent.db.type=$${izanami.db.default}
         |izanami.webhook.db.type=$${izanami.db.default}
         |izanami.user.db.type=$${izanami.db.default}
         |izanami.apikey.db.type=$${izanami.db.default}
         |izanami.patch.db.type=$${izanami.db.default}
         |
         |izanami {
         |  db {
         |    leveldb {
         |      parentPath = "./target/leveldb/$folder"
         |    }
         |  }
         |}
      """.stripMargin).resolve()
  )

  val inMemoryConfiguration: Configuration = Configuration(
    ConfigFactory
      .parseString("""
        |izanami.db.default="InMemory"
        |izanami.patchEnabled = false
        |
        |izanami.mode= "test"
        |izanami.config.db.type=${izanami.db.default}
        |izanami.features.db.type=${izanami.db.default}
        |izanami.globalScript.db.type=${izanami.db.default}
        |izanami.experiment.db.type=${izanami.db.default}
        |izanami.variantBinding.db.type=${izanami.db.default}
        |izanami.experimentEvent.db.type=${izanami.db.default}
        |izanami.webhook.db.type=${izanami.db.default}
        |izanami.user.db.type=${izanami.db.default}
        |izanami.apikey.db.type=${izanami.db.default}
        |izanami.patch.db.type=${izanami.db.default}
        |
      """.stripMargin)
      .resolve()
  )

  def folderConfig = s"data-${Random.nextInt(1000)}"

  def mongoConfig(test: String): Configuration = Configuration(
    ConfigFactory
      .parseString(s"""
       |izanami.db.default="Mongo"
       |izanami.patchEnabled = false
       |
       |izanami.mode= "test"
       |izanami.config.db.type=$${izanami.db.default}
       |izanami.features.db.type=$${izanami.db.default}
       |izanami.globalScript.db.type=$${izanami.db.default}
       |izanami.experiment.db.type=$${izanami.db.default}
       |izanami.variantBinding.db.type=$${izanami.db.default}
       |izanami.experimentEvent.db.type=$${izanami.db.default}
       |izanami.webhook.db.type=$${izanami.db.default}
       |izanami.user.db.type=$${izanami.db.default}
       |izanami.apikey.db.type=$${izanami.db.default}
       |izanami.patch.db.type=$${izanami.db.default}
       |
       |izanami {
       |  db {
       |    mongo {
       |      url = "mongodb://localhost:27017/$test-${Random.nextInt(1000)}"
       |    }
       |  }
       |}
      """.stripMargin)
      .resolve()
  )

  val inMemoryWithDbConfiguration: Configuration = Configuration(
    ConfigFactory
      .parseString("""
                     |izanami.db.default="InMemoryWithDb"
                     |izanami.db.inMemoryWithDb.db="InMemory"
                     |izanami.patchEnabled = false
                     |
                     |izanami.mode= "test"
                     |izanami.config.db.type=${izanami.db.default}
                     |izanami.features.db.type=${izanami.db.default}
                     |izanami.globalScript.db.type=${izanami.db.default}
                     |izanami.experiment.db.type=${izanami.db.default}
                     |izanami.experimentEvent.db.type=${izanami.db.default}
                     |izanami.webhook.db.type=${izanami.db.default}
                     |izanami.user.db.type=${izanami.db.default}
                     |izanami.apikey.db.type=${izanami.db.default}
                     |izanami.patch.db.type=${izanami.db.default}
                     |
      """.stripMargin)
      .resolve()
  )

  def dynamoDbConfig(id: Long): Configuration = Configuration(
    ConfigFactory
      .parseString(s"""
                     |izanami.db.default="Dynamo"
                     |izanami.patchEnabled = false
                     |
                     |izanami.mode= "test"
                     |izanami.config.db.type=$${izanami.db.default}
                     |izanami.db.dynamo.tableName=izanami_$id
                     |izanami.db.dynamo.eventsTableName=izanami_experimentevents_$id
                     |izanami.db.dynamo.host=localhost
                     |izanami.db.dynamo.port=8001
                     |izanami.config.db.type=$${izanami.db.default}
                     |izanami.features.db.type=$${izanami.db.default}
                     |izanami.globalScript.db.type=$${izanami.db.default}
                     |izanami.experiment.db.type=$${izanami.db.default}
                     |izanami.experimentEvent.db.type=$${izanami.db.default}
                     |izanami.webhook.db.type=$${izanami.db.default}
                     |izanami.user.db.type=$${izanami.db.default}
                     |izanami.apikey.db.type=$${izanami.db.default}
                     |izanami.patch.db.type=$${izanami.db.default}
      """.stripMargin)
      .resolve()
  )

  val inc = new AtomicInteger(0)

  def pgConfig(id: Long): Configuration = Configuration(
    ConfigFactory
      .parseString(s"""
                      |izanami.db.default="Postgresql"
                      |izanami.patchEnabled = false
                      |
                      |izanami.mode= "test"
                      |izanami.config.db.type=$${izanami.db.default}
                      |izanami.db.postgresql.url="jdbc:postgresql://localhost:5556/izanami"
                      |izanami.config.db.type=$${izanami.db.default}
                      |izanami.config.db.conf.namespace="izanami$id:config"
                      |izanami.features.db.type=$${izanami.db.default}
                      |izanami.features.db.conf.namespace="izanami$id:feature"
                      |izanami.globalScript.db.type=$${izanami.db.default}
                      |izanami.globalScript.db.conf.namespace="izanami$id:globalscript"
                      |izanami.experiment.db.type=$${izanami.db.default}
                      |izanami.experiment.db.conf.namespace="izanami$id:experiment"
                      |izanami.experimentEvent.db.type=$${izanami.db.default}
                      |izanami.experimentEvent.db.conf.namespace="izanami$id:experimentevent"
                      |izanami.webhook.db.type=$${izanami.db.default}
                      |izanami.webhook.db.conf.namespace="izanami$id:webhook"
                      |izanami.user.db.type=$${izanami.db.default}
                      |izanami.user.db.conf.namespace="izanami$id:user"
                      |izanami.apikey.db.type=$${izanami.db.default}
                      |izanami.apikey.db.conf.namespace="izanami$id:apikey"
                      |izanami.patch.db.type=$${izanami.db.default}
                      |izanami.patch.db.conf.namespace="izanami$id:patch"
      """.stripMargin)
      .resolve()
  )

}

object Tests {
  def getSuite(name: String, conf: () => Configuration, strict: Boolean = true): Seq[Suite] =
    Seq(
      new ConfigControllerSpec(name, conf()),
      new ExperimentControllerSpec(name, conf(), strict),
      new FeatureControllerSpec(name, conf()),
      new FeatureControllerStrictAccessSpec(name, conf()),
      new FeatureControllerWildcardAccessSpec(name, conf()),
      new GlobalScriptControllerSpec(name, conf()),
      new WebhookControllerSpec(name, conf()),
      new UserControllerSpec(name, conf()),
      new ApikeyControllerSpec(name, conf())
    )

  def getSuites(): Seq[Suite] =
    if (Try(Option(System.getenv("CI"))).toOption.flatten.exists(!_.isEmpty)) {
      getSuite("InMemory", () => Configs.inMemoryConfiguration, false) ++
      getSuite("InMemoryWithDb", () => Configs.inMemoryWithDbConfiguration, false) ++
      getSuite("Redis", () => Configs.redisConfiguration, false) ++
      getSuite("Elastic", () => Configs.elasticConfiguration, false) ++
      getSuite("Cassandra", () => Configs.cassandraConfiguration(s"config${idGenerator.nextId()}"), false) ++
      getSuite("LevelDb", () => Configs.levelDBConfiguration(Configs.folderConfig), false) ++
      getSuite("Mongo", () => Configs.mongoConfig("config"), false) ++
      //getSuite("Dynamo", () => Configs.dynamoDbConfig(Random.nextInt(1000)), false) ++
      getSuite("Postgresql", () => Configs.pgConfig(Random.nextInt(1000)), false)
    } else {
      getSuite("InMemory", () => Configs.inMemoryConfiguration, false) ++
      getSuite("LevelDb", () => Configs.levelDBConfiguration(Configs.folderConfig), false)
    }
}

class IzanamiIntegrationTests extends Suites(Tests.getSuites(): _*) with BeforeAndAfterAll {
  override protected def beforeAll(): Unit =
    if (Try(Option(System.getenv("CI"))).toOption.flatten.exists(!_.isEmpty)) {
      import elastic.codec.PlayJson._
      val client = ElasticClient[JsValue](port = Configs.elasticHttpPort)
      println("Cleaning ES indices")
      Await.result(client.deleteIndex("izanami_*"), 5.seconds)

      System.setProperty("aws.accessKeyId", "someKeyId")
      System.setProperty("aws.secretKey", "someSecretKey")

      Thread.sleep(10000)
    }

  override protected def afterAll(): Unit =
    Try {
      FileUtils.deleteRecursively(new File("./target/leveldb"))
    }
}
