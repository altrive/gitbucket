package gitbucket.core

import java.sql.DriverManager

import io.github.gitbucket.solidbase.Solidbase
import io.github.gitbucket.solidbase.model.Module
import liquibase.database.core.{H2Database, MySQLDatabase, PostgresDatabase}
import org.scalatest.{FunSuite, Tag}
import com.wix.mysql.EmbeddedMysql._
import com.wix.mysql.config.Charset
import com.wix.mysql.config.MysqldConfig._
import com.wix.mysql.distribution.Version._
import ru.yandex.qatools.embed.postgresql.PostgresStarter
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig.{Credentials, Net, Storage, Timeout}
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig
import ru.yandex.qatools.embed.postgresql.distribution.Version.Main.PRODUCTION
import com.wix.mysql.EmbeddedMysql
import ru.yandex.qatools.embed.postgresql.PostgresProcess

object ExternalDBTest extends Tag("ExternalDBTest")

class GitBucketCoreModuleSpec extends FunSuite {

  test("Migration H2"){
    new Solidbase().migrate(
      DriverManager.getConnection("jdbc:h2:mem:test", "sa", "sa"),
      Thread.currentThread().getContextClassLoader(),
      new H2Database(),
      new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
    )
  }

  test("Migration MySQL", ExternalDBTest){

    val (host, port, dbName, userName, password, useEmbeddedDB) = getMySqlConnectionInfo

    var mysqld  : EmbeddedMysql = null
    if(useEmbeddedDB)
    {
      val config = aMysqldConfig(v5_7_latest)
        .withPort(port)
        .withUser(userName, password)
        .withCharset(Charset.UTF8)
        .withServerVariable("log_syslog", 0)
        .withServerVariable("bind-address", "127.0.0.1")
        .build()

      mysqld  = anEmbeddedMysql(config)
          .addSchema(dbName)
          .start()
    }

    try {
      new Solidbase().migrate(
          DriverManager.getConnection(s"jdbc:mysql://${host}:${port}/${dbName}?useSSL=false", userName, password),
          Thread.currentThread().getContextClassLoader(),
          new MySQLDatabase(),
          new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
      )
    } finally {
      if(mysqld  != null){
         mysqld .stop()
      }
    }
  }

  test("Migration PostgreSQL", ExternalDBTest){

    val (host, port, dbName, userName, password, useEmbeddedDB) = getPostgresConnectionInfo

    var process : PostgresProcess = null
    if(useEmbeddedDB)
    {
      val runtime = PostgresStarter.getDefaultInstance()
      val config = new PostgresConfig(
        PRODUCTION,
        new Net(host, port),
        new Storage(dbName),
        new Timeout(),
        new Credentials(userName, password))

        val exec = runtime.prepare(config)
        process = exec.start()
    }

    try {
      new Solidbase().migrate(
        DriverManager.getConnection(s"jdbc:postgresql://${host}:${port}/${dbName}", userName, password),
        Thread.currentThread().getContextClassLoader(),
        new PostgresDatabase(),
        new Module(GitBucketCoreModule.getModuleId, GitBucketCoreModule.getVersions)
      )
    } finally {
      if(process != null)
        process.stop()
    }
  }


  //Get executing platform name from Environment Variable
  def getEnvironmentName ={
    val isTravisCI = scala.util.Properties.envOrElse("TRAVIS"  , "false").toBoolean
    val isAppVeyor = scala.util.Properties.envOrElse("APPVEYOR", "false").toBoolean
    
    (isTravisCI, isAppVeyor) match {
      case (true , false)  => "TravisCI"
      case (false, true )  => "AppVeyor"
      case _               => "Other"
    }
  }

  def getMySqlConnectionInfo = {
    getEnvironmentName match {
//    case "TravisCI"     => ("localhost", 3306, "gitbucket", "root", ""           , false)
      case "AppVeyor"     => ("localhost", 3306, "gitbucket", "sa"  , "Password12!", false)
      case _              => ("localhost", 3306, "gitbucket", "sa"  , "sa"         , true)
    }
  }

  def getPostgresConnectionInfo = {
    getEnvironmentName match {
//    case "TravisCI"     => ("localhost", 3306, "gitbucket", "postgres", "",            false)
      case "AppVeyor"     => ("localhost", 5432, "gitbucket", "postgres", "Password12!", false)
      case _              => ("localhost", 5432, "gitbucket", "sa"      , "sa",          true)
    }
  }
}
