package eu.swdev.zioplayground

import com.zaxxer.hikari.HikariDataSource
import javax.sql.{DataSource => JDataSource}
import org.h2.jdbcx.JdbcConnectionPool
import zio._

/**
 * A module that provides a javax.sql.DataSource given its configuration
 */
object DataSource {

  type DataSource = Has[JDataSource]

  sealed trait Config

  case class H2Config(url: String, user: String, password: String) extends Config

  case class HikariConfig(
                           driverClassName: String,
                           url: String,
                           user: String,
                           password: String,
                         ) extends Config

  def h2DataSource(config: H2Config): Managed[Throwable, JDataSource] = {
    Managed.makeEffect(JdbcConnectionPool.create(config.url, config.user, config.password))(_.dispose())
  }

  def hikariDataSource(config: HikariConfig): Managed[Throwable, JDataSource] = {
    Managed.makeEffect {
      val ds = new HikariDataSource()
      ds.setDriverClassName(config.driverClassName)
      ds.setJdbcUrl(config.url)
      ds.setUsername(config.user)
      ds.setPassword(config.password)
      ds
    }(_.close())
  }

  def dataSource(config: Config): Managed[Throwable, JDataSource] = config match {
    case c: H2Config => h2DataSource(c)
    case c: HikariConfig => hikariDataSource(c)
  }

  val layer: ZLayer[Has[Config], Throwable, DataSource] = ZLayer.fromServiceManaged(dataSource)

  val getDataSource = ZIO.access[DataSource](_.get)

}
