import akka.actor._
import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http
import spray.httpx._
import spray.http.{HttpMethods, HttpRequest, HttpResponse, Uri, HttpEntity, ContentTypes}
import akka.pattern.ask
import scala.concurrent.Future
import spray.json._
import DefaultJsonProtocol._
import scala.collection.mutable.ArrayBuffer
import scala.collection._
import scala.collection.convert.decorateAsScala._
import java.util.concurrent.ConcurrentHashMap
import scala.util.Random
import spray.client.pipelining.{ Get, sendReceive }
import spray.client.pipelining.{ Post, sendReceive }
import spray.json.DefaultJsonProtocol
import spray.httpx.SprayJsonSupport._
import spray.client.pipelining._
import scala.concurrent.Future
import scala.util.{ Success, Failure }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import akka.util.Timeout
import scala.language.postfixOps
import scala.collection.mutable.HashMap
import java.util.concurrent.{Executors, ExecutorService}

//JAVA - Security RSA

import java.math.BigInteger
import java.security.KeyFactory
import java.security.Key
import java.security.KeyPair
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec

import javax.crypto.Cipher;

import java.util.Base64.{Encoder, Decoder}

//Shared case classes
case class userProfileIN (id:Int, name:String, gender:Int, birthday:String, email:String, key:String)
case class postIN(userID:Int, content:String, isPersonalMessage:Boolean, privacyLevel:Int, ivStr:String, RSAencryptedAESKeyMap:scala.collection.immutable.Map[String, String])
case class postOUT(content:String, timeStamp:Long, numLikes:Int, numComments:Int, wrappedKey:String, ivStr:String)
case class profileOUT(name:String, gender:Int, email:String, birthday:String, isUser:Boolean)
case class pageOUT(userProf:profileOUT, postCount:Int, postList:List[postOUT])
case class postREQ(userID:Int, postID:Int, requestingUserID:Int)
case class pageREQ(userID:Int, requestingUserID:Int)
case class friendListIN (id:Int, listOfFriends:List[Int], publicKeyMap:scala.collection.immutable.Map[String, String])

//Case Classes local to this file
case class register(clientStartID:Int, name:String)
case class notifyFriends (notifyingUserID:Int, postID:Int)

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val PersonFormat = jsonFormat6(userProfileIN)
  implicit val postFormat = jsonFormat6(postIN)
  implicit val postOutFormat = jsonFormat6(postOUT)
  implicit val profileOutFormat = jsonFormat5(profileOUT)
  implicit val pageOutFormat = jsonFormat3(pageOUT)
  implicit val postREQFormat = jsonFormat3(postREQ)
  implicit val intlistFormat = jsonFormat3(friendListIN)
}

import MyJsonProtocol._
import spray.httpx.SprayJsonSupport._
import spray.util._

class clientActor extends Actor {

  val blockingExecutionContext = {
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2000))
  }

  implicit val timeout = Timeout(50000 seconds)
  var selfID:Int = -1
  var numUsers:Int = 0
  var myFriendList:List[Int] = Nil
  var friendMap = new HashMap[Int, String]
  var myFriendActors:List[ActorSelection] = Nil
  var publicKey:Key = null
  var privateKey:Key = null
  var publicKeyMap = new HashMap[String, String]
  var aesKey:Key = null
  var RSAwrapMap = new HashMap[String, String]
  val decoder = java.util.Base64.getDecoder()
  var postContent:String = "Ash nazg durbatuluk, ash nazg gimbatul, ash nazg thrakatuluk, agh burzum-ishi krimpatul.Ash nazg durbatuluk, ash nazg gimbatul, ash nazg thrakatuluk, agh burzum-ishi krimpatul.Ash nazg durbatuluk, ash nazg gimbatul, ash nazg thrakatuluk, agh burzum-ishi krimpatul.Ash nazg durbatuluk, ash nazg gimbatul, ash nazg thrakatuluk, agh burzum-ishi krimpatul.Ash nazg durbatuluk, ash nazg gimbatul, ash nazg thrakatuluk, agh burzum-ishi krimpatul.Ash nazg durbatuluk, ash nazg gimbatul, ash nazg thrakatuluk, agh burzum-ishi krimpatul.Ash nazg durbatuluk, ash nazg gimbatul, ash nazg thrakatuluk, agh burzum-ishi krimpatul.Ash nazg durbatuluk, ash nazg gimbatul, ash nazg thrakatuluk, agh burzum-ishi krimpatul.Ash nazg durbatuluk, ash nazg gimbatul, ash nazg thrakatuluk, agh burzum-ishi krimpatul.Ash nazg durbatuluk, ash nazg gimbatul, ash nazg thrakatuluk, agh burzum-ishi krimpatul."

  val logRequest: HttpRequest => HttpRequest = { r => println("\n" + r.toString + "\n"); r }

  val logResponse: HttpResponse => HttpResponse = { r => println("\n" + r.toString + "\n"); r }


  def getPage(userID:Int){
    println ("Get User Page ID = " + userID)
    val pipeline: HttpRequest => Future[pageOUT] = (
                      sendReceive
                      ~> unmarshal[pageOUT]
        )
    val futureResponse:Future[pageOUT] = pipeline(HttpRequest(HttpMethods.GET, "http://127.0.0.1:8080/page/" + userID))
    futureResponse.onComplete {
          case Success(response:pageOUT) => {
            if (response.postCount == -1)
            {
              println ("User doesnt exist")
            }
            else
            {
              println ("Got User Page Username = " + response.userProf.name)
              println ("Got User Page Post Count = " + response.postCount)
              if (response.postCount > 0)
                response.postList.foreach(x => println("Got User Page Post = " + x.content))
            }
          
          }
          case Failure (error) => println (" PAGE Request Timed out")
        }(blockingExecutionContext)
  }
  
  def getProfile (userID:Int){
    println ("Get User Profile ID = " + userID)
    val pipeline: HttpRequest => Future[profileOUT] = (
                      sendReceive
                      ~> unmarshal[profileOUT]
        )
    val futureResponse:Future[profileOUT] = pipeline(HttpRequest(HttpMethods.GET, "http://127.0.0.1:8080/profile/" + userID))
    futureResponse.onComplete {
          case Success(response:profileOUT) => {
            if ((response.name).equals("Not Found"))
            {
              println ("User doesnt exist")
            }
            else
            {
              println ("Got User Profile Username = " + response.name)
            }
          
          }
          case Failure (error) => println (" PROFILE Request Timed out")
        }(blockingExecutionContext)
  }

  def getFriendList(userID:Int)
  {
    var i = 0
    val pipeline: HttpRequest => Future[friendListIN] = (
                      sendReceive
                      ~> unmarshal[friendListIN]
        )
    val futureResponse:Future[friendListIN] = pipeline(HttpRequest(HttpMethods.GET, "http://127.0.0.1:8080/friendlist/" + userID))
    futureResponse.onComplete {
          case Success(response:friendListIN) => {
            if (response.listOfFriends.size  == 0 && response.publicKeyMap.size == 0)
            {
              println ("Not Found")
            }
            else
            {
              myFriendList = myFriendList ++ response.listOfFriends
              publicKeyMap = publicKeyMap ++ response.publicKeyMap

              myFriendList.foreach (x => {
                  myFriendActors ::= context.system.actorSelection("/user/Sauron" + x)  
              })
              
              context.system.scheduler.schedule(20 seconds, .0010 seconds)(makePost(selfID, postContent + selfID, false, Random.nextInt(3-1)))   
              context.system.scheduler.schedule(20 seconds, .0100 seconds)(makePrivateMessage(selfID, postContent, true, Random.nextInt(3-1), myFriendList.head.toString))   
            }
          }
          case Failure (error) => println (" FRIEND LIST Request Timed out")
        }(blockingExecutionContext)
  }

  def getPost(userID:Int, postID:Int) {
    val pipeline: HttpRequest => Future[postOUT] = (
                      sendReceive
                      ~> logResponse
                      ~> unmarshal[postOUT]
        )
    var result = HttpEntity(ContentTypes.`application/json`, (postREQ(userID, postID, selfID).toJson).prettyPrint)
    val futureResponse:Future[postOUT] = pipeline(HttpRequest(HttpMethods.GET, "http://127.0.0.1:8080/post/", Nil, result))
    futureResponse.onComplete {
          case Success(response:postOUT) => {
            if (response.timeStamp  == -1)
            {
              println ("Not Found")
            }
            else
            {
              var base64Decoder = java.util.Base64.getDecoder()
              var cipherRSA:Cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING")
              cipherRSA.init(Cipher.UNWRAP_MODE, privateKey);
              var aesKey = cipherRSA.unwrap(base64Decoder.decode(response.wrappedKey), "AES", Cipher.SECRET_KEY)
              var iv:IvParameterSpec = new IvParameterSpec(base64Decoder.decode(response.ivStr))
              var cipherAES:Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
              cipherAES.init(Cipher.DECRYPT_MODE, aesKey, iv)
              var finalContent:String = new String ((cipherAES.doFinal(base64Decoder.decode(response.content))).map(_.toChar))
              println ("\n--->Decrypted Post: " + finalContent + " Timestamp = " + response.timeStamp + "\n")
            }
          }
          case Failure (error) => println  (error)
        }(blockingExecutionContext)
  }

  def getRSAKeyEncryptedMap(aesKey:Key): HashMap[String, String]  = {
    val base64Encoder = java.util.Base64.getEncoder()
    val base64Decoder = java.util.Base64.getDecoder()
    var outMap = new HashMap[String, String]
    for ((k,v) <- publicKeyMap)
    { 
      var friendsPublicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(base64Decoder.decode(v)))
      var cipherRSA:Cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING")
      cipherRSA.init(Cipher.WRAP_MODE, friendsPublicKey);
      outMap.put(k, base64Encoder.encodeToString(cipherRSA.wrap(aesKey)))
    }     
    outMap
  }

  def makePost(userID:Int, content:String, isPersonalMessage:Boolean, privacyLevel:Int) {

      var rand = Random.nextInt(250)
      val encoder = java.util.Base64.getEncoder()
      var randomIV:SecureRandom = new SecureRandom()
      var iv:IvParameterSpec = new IvParameterSpec(randomIV.generateSeed(16))
      var cipherAES:Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipherAES.init(Cipher.ENCRYPT_MODE,aesKey,iv)
      var encryptedContent = encoder.encodeToString(cipherAES.doFinal((content.substring(rand, rand+30)).getBytes()))
      var RSAwrapMap = getRSAKeyEncryptedMap(aesKey)
    val pipeline: HttpRequest => Future[String] = (
                      logRequest
                      ~> sendReceive
                      ~> unmarshal[String]
        )
    var ivString:String = encoder.encodeToString(iv.getIV())
    var result = HttpEntity(ContentTypes.`application/json`, (postIN
                                                              (userID, 
                                                              encryptedContent, 
                                                              isPersonalMessage, 
                                                              privacyLevel, 
                                                              ivString, 
                                                              RSAwrapMap.toMap
                                                              ).toJson).prettyPrint)
    val futureResponse:Future[String] = pipeline(HttpRequest(HttpMethods.POST, "http://127.0.0.1:8080/post", Nil,result))
    futureResponse.onComplete {
          case Success(response:String) => {
          //Notify friends

          myFriendActors.foreach(x => {

            x ! notifyFriends(selfID, response.toInt)
          }
            )
          println ("Notification sent to friends")
          }
          case Failure (error) => println  ("MAKE POST Request Timed out")
        }(blockingExecutionContext)
  }

  //This should return a map with ONLY ONE entry as this is meant for PM
  def getRSAKeyEncryptedMapForPrivateMessage(aesKey:Key, toUserID:String): HashMap[String, String]  = {
    val base64Encoder = java.util.Base64.getEncoder()
    val base64Decoder = java.util.Base64.getDecoder()
    var outMap = new HashMap[String, String]
    var keyVal = publicKeyMap.getOrElse(toUserID, "?")
    if (!keyVal.equals("?"))
    {
        var friendsPublicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(base64Decoder.decode(keyVal)))
        var cipherRSA:Cipher = Cipher.getInstance("RSA/ECB/PKCS1PADDING")
        cipherRSA.init(Cipher.WRAP_MODE, friendsPublicKey);
        outMap.put(toUserID, base64Encoder.encodeToString(cipherRSA.wrap(aesKey)))
    }
    outMap
  }

  //Similar to post, however, this is only meant for one user and only 1 user should be notified about this
  def makePrivateMessage(userID:Int, content:String, isPersonalMessage:Boolean, privacyLevel:Int, toUserID:String) {

      var rand = Random.nextInt(250)
      val encoder = java.util.Base64.getEncoder()
      var randomIV:SecureRandom = new SecureRandom()
      var iv:IvParameterSpec = new IvParameterSpec(randomIV.generateSeed(16))
      var cipherAES:Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipherAES.init(Cipher.ENCRYPT_MODE,aesKey,iv)
      var encryptedContent = encoder.encodeToString(cipherAES.doFinal((content.substring(rand, rand + 30)).getBytes()))
      var RSAwrapMap = getRSAKeyEncryptedMapForPrivateMessage(aesKey, "" + toUserID)
    val pipeline: HttpRequest => Future[String] = (
                      logRequest
                      ~> sendReceive
                      ~> unmarshal[String]
        )
    var ivString:String = encoder.encodeToString(iv.getIV())
    var result = HttpEntity(ContentTypes.`application/json`, (postIN
                                                              (userID, 
                                                              encryptedContent, 
                                                              true,  //This is a personal message
                                                              2, //Private
                                                              ivString, 
                                                              RSAwrapMap.toMap
                                                              ).toJson).prettyPrint)
    val futureResponse:Future[String] = pipeline(HttpRequest(HttpMethods.POST, "http://127.0.0.1:8080/post", Nil,result))
    futureResponse.onComplete {
          case Success(response:String) => {
          val friendActor = context.system.actorSelection("/user/Sauron" + myFriendList.head)
          friendActor ! notifyFriends(selfID, response.toInt)
          println ("Notification sent to friends")
          }
          case Failure (error) => println  ("MAKE POST Request Timed out")
        }(blockingExecutionContext)
  }

  def registerToServer (id:Int, name:String) {
    val encoder = java.util.Base64.getEncoder()
    val pipeline: HttpRequest => Future[String] = (
                      logRequest
                      ~> sendReceive
                      ~> unmarshal[String]
        )
    var result = HttpEntity(ContentTypes.`application/json`, ((userProfileIN (id, name, 1, "02/29/1900", "foo.bar@foobar.com", encoder.encodeToString(publicKey.getEncoded())).toJson).prettyPrint))

    val futureResponse:Future[String] = pipeline(HttpRequest(HttpMethods.POST, "http://127.0.0.1:8080/register", Nil,result))
    futureResponse.onComplete {
          case Success(response:String) => {
          context.system.scheduler.scheduleOnce(10 seconds)(getFriendList (selfID))
          }
          case Failure (error) => println ("REGISTER Request Timed out")
        }(blockingExecutionContext)
  }

  def generatePubPrivKeys() {

      var starttime = System.currentTimeMillis()
      var kpg:KeyPairGenerator = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(1024);
      var kp:KeyPair = kpg.genKeyPair();
      publicKey = kp.getPublic();
      privateKey = kp.getPrivate();

      var kg:KeyGenerator = KeyGenerator.getInstance("AES")
      var randomKey:SecureRandom = new SecureRandom()
      kg.init(128, randomKey)
      aesKey = kg.generateKey()
      RSAwrapMap = getRSAKeyEncryptedMap(aesKey)
  }

  def receive = {
    case register(clientStartID:Int, name:String) =>{
      selfID = clientStartID
      generatePubPrivKeys()
      registerToServer(selfID, name)
    }
    case notifyFriends(notifyingUserID:Int, postID:Int) => {
      getPost(notifyingUserID, postID)
      //getPost(100,100) //This will generate a fail output
    }
  }
}

object client extends App {
  val clientActorSys = ActorSystem("ClientActorSys")
  val TotalNumClients = 1000
  var i = 0
  while (i < TotalNumClients)
  {
    val client = clientActorSys.actorOf(Props[clientActor], "Sauron" + i)
    client ! register(i, "Sauron" + i)
    i = i + 1
  }
} 