package ch.openolitor.core.proxy

import spray.routing._
import akka.actor._
import spray.can.websocket
import spray.can.websocket.frame.{ BinaryFrame, TextFrame }
import spray.http._
import spray.can.websocket._
import spray.can.websocket.{ Send, SendStream, UpgradedToWebSocket }
import akka.util.ByteString
import ch.openolitor.core.Boot.MandantSystem
import org.jfarcand.wcs._
import com.ning.http.client._
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig
import com.ning.http.client.ws.WebSocketListener
import scala.collection.mutable.ListBuffer

object ProxyWorker {
  case class Push(msg: String)
  case class BinaryPush(msg: Array[Byte])

  def props(serverConnection: ActorRef, routeMap:Map[String, MandantSystem]) = Props(classOf[ProxyWorker], serverConnection, routeMap)
}

/**
 * This Proxy Worker proxies either websocket messages to a websocket server or
 * normal httprequest to a httpserver
 **/
class ProxyWorker(val serverConnection: ActorRef, val routeMap:Map[String, MandantSystem]) 
  extends HttpServiceActor 
  with websocket.WebSocketServerWorker
  with Proxy{

  import ProxyWorker._
  val wsOptions = new Options
  wsOptions.idleTimeout = 24 * 60 * 60000 // request timeout to whole day
  var wsClient:WebSocket = createWebsocket(wsOptions)
    
  def createWebsocket(o: Options): WebSocket = {
    val nettyConfig: NettyAsyncHttpProviderConfig = new NettyAsyncHttpProviderConfig
    var asyncHttpClient: AsyncHttpClient = null
    val listeners: ListBuffer[WebSocketListener] = ListBuffer[WebSocketListener]()
    var config = new AsyncHttpClientConfig.Builder
    
    if (o != null) {
      nettyConfig.setWebSocketMaxBufferSize(o.maxMessageSize)
      nettyConfig.setWebSocketMaxFrameSize(o.maxMessageSize)
      config.setRequestTimeout(o.idleTimeout)
        .setConnectTimeout(o.idleTimeout)
        .setConnectionTTL(o.idleTimeout)
        .setPooledConnectionIdleTimeout(o.idleTimeout)
        .setReadTimeout(o.idleTimeout)
        .setWebSocketTimeout(o.idleTimeout)
        .setUserAgent(o.userAgent).setAsyncHttpClientProviderConfig(nettyConfig)
    }

    try {
      config.setFollowRedirect(true)
      asyncHttpClient = new AsyncHttpClient(config.build)
    } catch {
      case t: IllegalStateException => {
        config = new AsyncHttpClientConfig.Builder
      }
    }
    new WebSocket(o, None, false, asyncHttpClient, listeners)  
  }
  
  var url:Option[String]=None
  
  override def receive = businessLogicNoUpgrade orElse closeLogic orElse other
    
  override def postStop() {
    log.debug(s"onPostStop")
    if (wsClient.isOpen){ 
      wsClient.close
    }
    super.postStop()
  }  
  
  val other: Receive = {
    case x =>      
      log.error(s"Unmatched reuqest: $x")
  }
  
  val requestContextHandshake: Receive = {
    case ctx: RequestContext =>
      handshaking(ctx.request)    
  }
  
  lazy val textMessageListener = new TextListener {
    override def onMessage(message: String) {
      log.debug(s"Got message from server, process locally:$message")
      self ! Push(message)
    }    
     /**
	   * Called when the {@link WebSocket} is opened
  	 */
    override def onOpen {
      log.debug(s"messageListener:onOpen")
    }
    
    /**
	   * Called when the {@link WebSocket} is closed
   	*/
    override def onClose {
      log.debug(s"messageListener:onClose")
      openWsClient
    }
    
    /**
	   * Called when the {@link WebSocket} is closed with its assic
  	 */
    override def onClose(code: Int, reason : String) {
      log.debug(s"messageListener:onClose:$code -> $reason")
      openWsClient
    }
    
    /**
	   * Called when an unexpected error occurd on a {@link WebSocket}
  	 */
    override def onError(t: Throwable) {
      log.error(s"messageListener:onError", t)
      openWsClient
    }
  }
  
  lazy val binaryMessageListener = new BinaryListener {
    
    override def onMessage(message:Array[Byte]) {
      log.debug(s"Got binary message from server, process locally:$message")     
      self ! BinaryPush(message)
    }
    
     /**
	   * Called when the {@link WebSocket} is opened
  	 */
    override def onOpen {
      log.debug(s"binaryMessageListener:onOpen")
    }
    
    /**
	   * Called when the {@link WebSocket} is closed
   	*/
    override def onClose {
      log.debug(s"binaryMessageListener:onClose")
    }
    
    /**
	   * Called when the {@link WebSocket} is closed with its assic
  	 */
    override def onClose(code: Int, reason : String) {
      log.debug(s"binaryMessageListener:onClose:$code -> $reason")
    }
    
    /**
	   * Called when an unexpected error occurd on a {@link WebSocket}
  	 */
    override def onError(t: Throwable) {
      log.error(s"binaryMessageListener:onError", t)
    }
  }
  
  val proxyRoute: Route = {
    path(routeMap / "ws"){ mandant =>
      log.debug(s"Got request to websocket resource, create proxy client for $mandant to ${mandant.config.wsUri}")
      url = Some(mandant.config.wsUri)
      openWsClient
      context become (handshaking orElse closeLogic)
      //grab request from already instanciated requestcontext to perform handshake
      requestContextHandshake      
    }~    
    pathPrefix(routeMap){ mandant =>        
      log.debug(s"proxy service request of mandant:$mandant to ${mandant.config.uri}")
      proxyToUnmatchedPath(mandant.config.uri)(mandant.system)      
    }
  }
  
  def openWsClient:Option[WebSocket] = {    
    url.map{ url => 
      log.debug("Reopen ws connection to server")
      wsClient = wsClient.open(url).listener(textMessageListener).listener(binaryMessageListener)
      wsClient
    }  
  }

  /**
   * In case we did get a websocket upgrade
   * Websocket handling logic
   */
  def businessLogic: Receive = {

    case Push(msg) =>
      log.debug(s"Got message from websocket server, Push to client:$msg")
      send(TextFrame(msg))
      // just bounce frames back for Autobahn testsuite
    case BinaryPush(msg) =>
      log.debug(s"Got message from websocket server, Push to client:$msg")
      send(BinaryFrame(ByteString(msg)))
      // just bounce frames back for Autobahn testsuite
    case x: BinaryFrame =>
      log.debug(s"Got from binary data:$x")
    case x: TextFrame =>
      val msg = x.payload.decodeString("UTF-8")
      log.debug(s"Got message from client, send to websocket service:$msg -> $x")
      wsClient.send(msg)
    case x: FrameCommandFailed =>
      log.error("frame command failed", x)
    case x: HttpRequest => // do something
      log.debug(s"Got http request:$x")      
    case UpgradedToWebSocket => 
      log.debug(s"Upgradet to websocket,isOpen:${wsClient.isOpen}")
    case x =>
      log.warning(s"Got unmatched message:$x")
  }

  /**
   * In case we didn't get a websocket upgrade notification
   **/
  def businessLogicNoUpgrade: Receive = runRoute(proxyRoute) orElse other
}