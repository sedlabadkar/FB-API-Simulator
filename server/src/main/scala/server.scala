import akka.actor._
import akka.actor
import akka.io.IO
import spray.can.Http
import spray.httpx._
import spray.http.{HttpMethods, HttpRequest, HttpResponse, Uri, HttpEntity, ContentTypes}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import java.io.File
import com.typesafe.config.ConfigFactory
import spray.json._
import DefaultJsonProtocol._
import scala.collection.mutable.ArrayBuffer
import scala.collection._
import scala.collection.convert.decorateAsScala._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.TreeMap
import scala.util.Random
import scala.collection.mutable.HashMap
import java.util.concurrent.atomic.AtomicReference

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

//Shared classes to define the format of data sent
case class userProfileIN (id:Int, name:String, gender:Int, birthday:String, email:String,key:String)
case class postIN(userID:Int, content:String, isPersonalMessage:Boolean, privacyLevel:Int, ivStr:String, RSAencryptedAESKeyMap:scala.collection.immutable.Map[String, String])
case class postOUT(content:String, timeStamp:Long, numLikes:Int, numComments:Int, wrappedKey:String, ivStr:String)
case class profileOUT(name:String, gender:Int, email:String, birthday:String, isUser:Boolean)
case class pageOUT(userProf:profileOUT, postCount:Int, postList:List[postOUT])
case class postREQ(userID:Int, postID:Int, requestingUserID:Int)
case class pageREQ(userID:Int, requestingUserID:Int)
//case class friendListIN (id:Int, a:List[Int], b:List[String])
case class friendListIN (id:Int, listOfFriends:List[Int], publicKeyMap:scala.collection.immutable.Map[String, String])

//The Four Case Classes - The actual FB Graph API
case class friendList (id:Int, 					//ID - Should be same as the owner ID 
						ownerID:Int, 			//Whose friend list is this?
						actualList:List[Int]	//Ze list.
						)

case class profile (id:Int, 					//Profile ID
					name:String, 				//User/Page name
					pageID:Int, 				//Corresponding page - this gives the posts by this user
					friends:List[Int], 			//Friends list for this user
					gender:Int, 				//Gender - 1:Male, 2:Female. Hardcoded to 1
					birthday:String, 			//Birthday - Hardcoded to 29th Feb 1900
					email:String,				//Email - Hardcoded to 'foo.bar@foobar.com'
					isUser:Boolean,				//Boolean to differentiate whether this is user profile or a promotional FB page
					key:String			        //Public key of this user
					)

case class page (id:Int,						//PageID - All ID's are kept the same as userID
				 isUser:Boolean,				//Boolean to differentiate whether this is user profile or a promotional FB page
				 postIDList:List[Int]			//List of postID's created by this user/promotional page
				 )

case class post (id:Int,						//PostID - This is not going to be same as the userID (Duh)
				 content:String, 				//Post content
				 numLikes:Int, 					//Number of likes
				 numComments:Int, 				//Number of Comments. Can be modified to actually store comments as posts
				 timeInMillis:Long,				//When was the post made
				 isPersonalMessage:Boolean, 	//Boolean to separate personal message from a post. NEEDED?
				 privacyLevel:Int, 				//0:Public, 1:Friends, 2:Private
				 ivStr:String,
				 publicKeyMap:HashMap[String,String]
				 )

class serverActor extends Actor {
	var userProfile = new ConcurrentHashMap[Int, profile]
	var pageMap = new ConcurrentHashMap[Int, page]
	var postMap = new ConcurrentHashMap[Float, post]
	var postID:AtomicInteger = new AtomicInteger(0)
 	var clientPublicKeyMap = new HashMap[Int, String]

	def generateFriendList(userID:Int): List[Int]  = {
		var friendList:List[Int] = Nil
		if (userID < 750)
		{
			//This is a heavy user - 600 friends
			for (i <- 0 to 60)
			{
				friendList ::= Random.nextInt(1000 - 0)
			}
		}
		else if (userID >= 750 && userID < 900)
		{
			//This is a moderate user - 300 Friends
			for (i <- 0 to 30)
			{
				friendList ::= Random.nextInt(1000 - 0)
			}
		}
		else if (userID >= 900)
		{
			//This is a light user - 100 friends
			for (i <- 0 to 10)
			{
				friendList ::= Random.nextInt(1000 - 0)
			}
		}
		return friendList
	}

	def getPublicKey(x: Option[String]) = x match {
      case Some(s) => s
      case None => "?"
   }

	def receive = {
		case a:userProfileIN => {
			//println (a.id + " " + a.name + " " + 1 + "02/29/1900" + " " + "foo.bar@foobar.com")
			val friendList = generateFriendList(a.id)
			var postList:List[Int] = Nil
			clientPublicKeyMap.put(a.id, a.key)
			//print ("a.key = " + a.key)
			//println ("Key = " + (a.key).size)
			userProfile.put(a.id, profile(a.id, a.name, a.id, friendList, 1, "02/29/1900", a.name + a.id + "@foobar.com", true, a.key)) //<key, value>
      		pageMap.put (a.id, page(a.id, true, postList))
      		sender ! "Registered"
		}
		case b: postIN => {
			var keyMap = new HashMap[String, String]
			var starttime = System.currentTimeMillis()
			var user = userProfile.get (b.userID)
			var userPage = pageMap.get(user.pageID)
			var postidlist:List[Int] = userPage.postIDList
			postidlist ::= postID.intValue()
			pageMap.replace(user.pageID, page(user.pageID, true, postidlist))
			println (b.content + " " + (b.userID + "." + postID.intValue()).toFloat)
			keyMap = keyMap ++ b.RSAencryptedAESKeyMap
			postMap.put((b.userID + "." +postID.intValue()).toFloat, post(postID.intValue(), b.content, Random.nextInt(100), Random.nextInt(20), System.currentTimeMillis(), b.isPersonalMessage, b.privacyLevel, b.ivStr, keyMap))
			sender ! "" + postID.intValue()
			postID.getAndIncrement() 
		}
		case d:postREQ => {
			//val x:Int = c.lastIndexOf(":")
			val postid:Float = (d.userID + "." + d.postID).toFloat
			var requestedPost:post = postMap.get(postid)
			if (requestedPost != null)
				{
					if (requestedPost.publicKeyMap.contains(d.requestingUserID.toString))
					{
						var wrappedKeyString = requestedPost.publicKeyMap.getOrElse(d.requestingUserID.toString, "?")
						var requestedPostOUT:postOUT = postOUT(requestedPost.content, requestedPost.timeInMillis, requestedPost.numLikes, requestedPost.numComments, wrappedKeyString, requestedPost.ivStr)
						sender ! requestedPostOUT
					}
					else
					{
						println ("Proper Decryption key was not found. This post is not meant for the requesting user")
						sender ! postOUT("Not Found", -1, 0, 0, "Not Found", "NF")				
					}
				}
				else
				{
					println ("Post Not Found")
					sender ! postOUT("Not Found", -1, 0, 0, "Not Found", "NF")
				}
			
		}
		case e:pageREQ => {
			val userid:Int = e.userID
			val requestedProfile:profile = userProfile.get(userid)
			val userPage = pageMap.get(requestedProfile.pageID)
			val postidlist = userPage.postIDList
			var postList:List[postOUT] = Nil
			if (userPage != null)
			{
				var i = 0
				while (i < postidlist.size && i < 10)
				{
					var userpost = postMap.get(postidlist(i))
					if (userpost.publicKeyMap.contains(e.requestingUserID.toString))
					{	
						var wrappedKeyString = userpost.publicKeyMap.getOrElse(e.requestingUserID.toString, "?")
						var requestedPostOUT:postOUT = postOUT(userpost.content, userpost.timeInMillis, userpost.numLikes, userpost.numComments, wrappedKeyString, userpost.ivStr)
						//sender ! requestedPostOUT
						postList ::= requestedPostOUT
					}
					i = i + 1
				}
				var requestedProfileOUT:profileOUT = profileOUT(requestedProfile.name, requestedProfile.gender,
																	requestedProfile.email, requestedProfile.birthday,
																	requestedProfile.isUser)
				
				sender ! pageOUT (requestedProfileOUT, postList.size, postList)
			}
			else
			{
				sender ! pageOUT (null, -1, null)
			}
		}
		case c:String => {
			/*if (c.startsWith("post:"))
			{
				val x:Int = c.lastIndexOf(":")
				val postid:Float = (c.substring(x+1)).toFloat
				var requestedPost:post = postMap.get(postid)
				println ("PostRequested " + postid)

				if (requestedPost != null)
				{
					var requestedPostOUT:postOUT = postOUT(requestedPost.content, requestedPost.timeInMillis, requestedPost.numLikes, requestedPost.numComments)
					sender ! requestedPostOUT				
				}
				else 
					sender ! postOUT("Not Found", -1, 0, 0)
				//If the post of the given id does not exist, we let the request timeout and fail
			}*/
			if (c.startsWith("profile:"))
			{
				val x:Int = c.lastIndexOf(":")
				val userid:Int = (c.substring(x+1)).toInt
				println ("Profile Requested for " + userid)
				var requestedProfile:profile = userProfile.get(userid)
				if (requestedProfile != null)
				{
					var requestedProfileOUT:profileOUT = profileOUT(requestedProfile.name, requestedProfile.gender,
																	requestedProfile.email, requestedProfile.birthday,
																	requestedProfile.isUser)
					sender ! requestedProfileOUT				
				}
				else 
				{
					sender ! profileOUT("Not Found", 1, "notfound", "0", false)
				}	
			}
			if (c.startsWith("page"))
			{
				val x:Int = c.lastIndexOf(":")
				val userid:Int = (c.substring(x+1)).toInt
				println ("Page Requested for " + userid)
				val requestedProfile:profile = userProfile.get(userid)
				val userPage = pageMap.get(requestedProfile.pageID)
				val postidlist = userPage.postIDList
				var postList:List[postOUT] = Nil
				if (userPage != null)
				{
					var i = 0
					while (i < postidlist.size && i < 10)
					{
						var userpost = postMap.get(postidlist(i))
						if (userpost.publicKeyMap.contains(userid.toString))
						{	
							var wrappedKeyString = userpost.publicKeyMap.getOrElse(userid.toString, "?")
							var requestedPostOUT:postOUT = postOUT(userpost.content, userpost.timeInMillis, userpost.numLikes, userpost.numComments, wrappedKeyString, userpost.ivStr)
							//sender ! requestedPostOUT
							postList ::= requestedPostOUT
						}
						i = i + 1
					}
					var requestedProfileOUT:profileOUT = profileOUT(requestedProfile.name, requestedProfile.gender,
																		requestedProfile.email, requestedProfile.birthday,
																		requestedProfile.isUser)
					
					sender ! pageOUT (requestedProfileOUT, postList.size, postList)
				}
				else
				{
					sender ! pageOUT (null, -1, null)
				}
			}
			if (c.startsWith("friendlist"))
			{
				val x:Int = c.lastIndexOf(":")
				val userid:Int = (c.substring(x+1)).toInt
				val requestedProfile:profile = userProfile.get(userid)
				val friendList = requestedProfile.friends
				var friendsKeyHash = new HashMap[String, String]
				friendList.foreach(x=>
				{
					var temp = getPublicKey(clientPublicKeyMap.get(x))
					//println ("temp = " + temp + "Size = " + temp.size)
					friendsKeyHash.put(x.toString, temp)
					})
				var outmsg = friendListIN(userid, friendList, friendsKeyHash.toMap)
				sender ! outmsg
			}
		}
	}
}

object server extends App{
    val serverSys = ActorSystem("ServerSystem")
	var serverActors:List[ActorRef] = Nil
	var i:Int = 0
	var numServerActors:Int = 1
	while (i < numServerActors)
	{
    	serverActors ::= serverSys.actorOf(Props[serverActor], name ="server" + i)
    	i = i + 1
	}
}

