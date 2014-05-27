
import java.io.File
import scala.io.Source
import scala.concurrent.duration._
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
import twitter4j.auth._
import java.util.concurrent._

// args: <consumerkey> <consumersecret> <token> <tokensecret> [<run mins>]
object Test extends App {

  val lastIDFilename = "/tmp/twitter.lastid.txt"
  val handles = List("WIRED","NYTimes","mrbalky","mrbalkytivo","pourmecoffee","TheEconomist","WSJ")
  var lastStatusID = loadLastStatusID

  def loadLastStatusID = {
    if ( new File(lastIDFilename).exists )
      Source.fromFile(lastIDFilename).getLines.next.toLong
    else
      0L
  }

  def updateLastStatusID( status: Status ) {
    if ( status.getId > lastStatusID ) {
      val p = new java.io.PrintWriter(lastIDFilename)
      try { p.println(status.getId) } finally { p.close() }
      lastStatusID = status.getId
    }
  }

  def lookUpUserIDs( handles: List[String], twitter: Twitter ): List[java.lang.Long] = {
    handles.map( h => Long.box(twitter.showUser(h).getId) )
  }

  def dd( status: Status, indent: String = "" ) {
    if ( status != null ) {
      println(s"\n$indent [${status.getId}] ${status.getUser.getName}: ${status.getText}")
      println(s"$indent user id ${status.getUser.getId}")
      println(s"$indent in reply to ${status.getInReplyToScreenName}")
      println(s"$indent in reply to status id ${status.getInReplyToStatusId}")
      println(s"$indent is retweet ${status.isRetweet}")
      println(s"$indent source ${status.getSource}")
      println("is followed user: " + follow.contains(status.getUser.getId) )
      dd( status.getRetweetedStatus, indent+"--" )
    }
  }

  def dispStatus( status: Status ) {
    if( follow.contains(status.getUser.getId) ) {
      println(s"\n[${status.getId}] ${status.getUser.getName}: ${status.getText}")
    } else {
      print(".")
    }
    updateLastStatusID(status)
  }

  def simpleStatusListener = new com.twitter.hbc.twitter4j.handler.StatusStreamHandler () {
    def onStatus(status: Status) {
      dispStatus(status)
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

  def backfill( twitter: Twitter, userID: Long, since: Long ) {
    val paging = new Paging(since)
    for ( status <- twitter.getUserTimeline(userID, paging).asScala.reverse ) {
      dispStatus(status)
    }
  }

  val twitter: Twitter = TwitterFactory.getSingleton
  twitter.setOAuthConsumer(args(0),args(1))
  twitter.setOAuthAccessToken(new AccessToken(args(2),args(3)))

  val follow = lookUpUserIDs(handles, twitter)

  if ( lastStatusID > 0 ) {
    println(s"backfilling from $lastStatusID")
    val minID = lastStatusID
    for ( id <- follow ) {
      backfill(twitter, id, minID)
    }
  }

  val oauth = new OAuth1(args(0),args(1),args(2),args(3))
  val runTime = (if (args.length>4) args(4).toInt else 1).minutes

  val msgQueue = new LinkedBlockingQueue[String](100000)
  val endpoint = new StatusesFilterEndpoint().followings(follow.asJava)

  val client = new ClientBuilder()
            .hosts(Constants.USERSTREAM_HOST)
            .authentication(oauth)
            .processor(new StringDelimitedProcessor(msgQueue))
            .endpoint(endpoint)
            .build()

  println(s"Running for $runTime...")
  val t4jClient = new Twitter4jStatusClient( client, msgQueue, List(simpleStatusListener).asJava, Executors.newFixedThreadPool(5) )
  t4jClient.connect()
  t4jClient.process()

  Thread.sleep(runTime.toMillis)

  println("Stopping...")
  t4jClient.stop

}
