
import java.io.File
import scala.io.Source
import com.twitter.hbc.ClientBuilder
import com.twitter.hbc.core._
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.OAuth1
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint ;
import scala.collection.JavaConverters._
import java.util.concurrent.LinkedBlockingQueue
import org.apache.commons.lang3.StringEscapeUtils
import com.twitter.hbc.twitter4j._
import com.twitter.hbc.twitter4j.message._
import twitter4j._
import java.util.concurrent._

object Test extends App {

  def simpleStatusListener = new com.twitter.hbc.twitter4j.handler.StatusStreamHandler () {
    def onStatus(status: Status) {
      if ( !status.isRetweet )
        println(s"""
          |${status.getUser.getName}: ${status.getText}""".stripMargin)
      else
        print(".")
    }

    def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) { println("onDeletionNotice") }
    def onTrackLimitationNotice(numberOfLimitedStatuses: Int) { println("onTrackLimitationNotice") }
    def onException(ex: Exception) { ex.printStackTrace }
    def onScrubGeo(arg0: Long, arg1: Long) { println("onScrubGeo") }
    def onStallWarning(warning: StallWarning) { println("onStallWarning") }


   /**
     * See documentation on disconnect messages here: https://dev.twitter.com/docs/streaming-apis/messages#Disconnect_messages_disconnect
     */
    def onDisconnectMessage(message: DisconnectMessage) { println("onDisconnectMessage") }

    /**
     * See documentation on stall warnings here:
     * See https://dev.twitter.com/docs/streaming-apis/parameters#stall_warnings
     *
     * Ideally, twitter4j would make it's StallWarning's constructor public and we could remove this.
     */
    def onStallWarningMessage(warning: StallWarningMessage) { println("onStallWarningMessage") }

    /**
     * Any message we receive that isn't handled by the other methods
     */
    def onUnknownMessageType(msg: String){ println("onUnknownMessageType") }
  }


  override def main(args: Array[String]) {
    import scala.concurrent.duration._

    // args: <consumerkey> <consumersecret> <token> <tokensecret> [<run mins>]
    val oauth = new OAuth1(args(0),args(1),args(2),args(3))
    val runTime = (if (args.length>4) args(4).toInt else 1).minutes

    val msgQueue = new LinkedBlockingQueue[String](100000)
    val follow: List[java.lang.Long] = List(1344951,5988062,807095,3108351,3243351,20245577,16906137)
    val endpoint = new StatusesFilterEndpoint().followings(follow.asJava)

    val client = new ClientBuilder()
              .hosts(Constants.USERSTREAM_HOST)
              .authentication(oauth)
              .processor(new StringDelimitedProcessor(msgQueue))
              .endpoint(endpoint)
              .build()

    val t4jClient = new Twitter4jStatusClient( client, msgQueue, List(simpleStatusListener).asJava, Executors.newFixedThreadPool(5) )
    t4jClient.connect()
    t4jClient.process()

    println(s"Running for $runTime...")

    Thread.sleep(runTime.toMillis)

    println("Stopping...")
    t4jClient.stop
  }
}
