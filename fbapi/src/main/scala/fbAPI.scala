import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.ActorSelection.toScala
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
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
import spray.httpx.unmarshalling._
import spray.client.pipelining._
import scala.concurrent.Future
import scala.util.{ Success, Failure }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext



case class userProfileIN (id:Int, name:String, gender:Int, birthday:String, email:String, key:String)
case class postIN(userID:Int, content:String, isPersonalMessage:Boolean, privacyLevel:Int, ivStr:String, RSAencryptedAESKeyMap:scala.collection.immutable.Map[String, String])
case class postOUT(content:String, timeStamp:Long, numLikes:Int, numComments:Int, wrappedKey:String, ivStr:String)
case class profileOUT(name:String, gender:Int, email:String, birthday:String, isUser:Boolean)
case class pageOUT(userProf:profileOUT, postCount:Int, postList:List[postOUT])
case class postREQ(userID:Int, postID:Int, requestingUserID:Int)
case class pageREQ(userID:Int, requestingUserID:Int)
//case class friendListIN (id:Int, a:List[Int], b:List[String])
case class friendListIN (id:Int, listOfFriends:List[Int], publicKeyMap:scala.collection.immutable.Map[String, String])

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


class fbAPI(serverActors:Array[ActorSelection]) extends Actor { //Actor logging
  implicit val timeout: Timeout = 20.second 
  //import context.dispatcher
 def receive = {
    
	case _: Http.Connected => //Will be received when a new connection is connected
		println ("Http.Connected")
		println (self)
		sender ! Http.Register(self)

	//Test API
	case HttpRequest(HttpMethods.GET, Uri.Path("/ping"), _, _, _) => {

		sender ! HttpResponse(entity = "PONG ")
	}

	/**
	* Profile - User's/Page's basic information
	* 1. ProfileID
	* 2. Name - Both First and Last.
	* 3. PageID - Each User/Page has a timeline. This ID corresponds to that timeline. Timelines show posts.
	*/
	case HttpRequest(HttpMethods.GET, Uri.Path(path), _, _, _) if path startsWith "/profile" => {
		var userid= path.split("/").last.toInt //What if no or incorrect value is received?
		val a = sender
		var msg:String = "profile:" + userid
		//var future: Future[profileOUT] = ((serverActors(userid % 64)) ? msg).mapTo[profileOUT]
		var future: Future[profileOUT] = ((serverActors(0)) ? msg).mapTo[profileOUT]
		future onComplete
     	{
	       case Success(response:profileOUT) =>{
	       		val body = HttpEntity(ContentTypes.`application/json`, response.toJson.prettyPrint)
	         	a ! HttpResponse(entity = body)
	       }
	       case Failure (error) => println ("Request Timed out")
     	} 
	}

	/**
	* Page - Equaivalent to FB Timeline. Posts show up on this. Can be thought of as a collection of posts.
	* 1. PageID 
	* 2. OwnerID - The owner "profile" of this page
	* 3. PostsList - A FIXED SIZE list with the posts to show on this page (Timeline/ Newsfeed)
	*/

	case HttpRequest(HttpMethods.GET, Uri.Path(path), _, _, _) if path startsWith "/page" => {
		var userid= path.split("/").last.toInt //What if no or incorrect value is received?
		val a = sender
		var msg:String = "page:" + userid
		//var future: Future[pageOUT] = ((serverActors(userid % 64)) ? msg).mapTo[pageOUT]
		var future: Future[pageOUT] = ((serverActors(0)) ? msg).mapTo[pageOUT]
		future onComplete
     	{
	       case Success(response:pageOUT) =>{
	       		val body = HttpEntity(ContentTypes.`application/json`, response.toJson.prettyPrint)
	         	a ! HttpResponse(entity = body)
	       }
	       case Failure (error) => println ("Request Timed out")
     	} 
	}

	/**
	* Post
	* 1. PostID 
	* 2. OwnerID - The owner "profile" of this post
	* 3. Text - Actual Post content
	* 4. Timestamp - Time at which the post was made (ms)
	* 5. Tagged Users - These users get direct notifications. In a way, this post can then show up on their timeline
	* 6. PrivacySettings - PUBLIC(0:Default), FRIENDS(1), PRIVATE(2)
	* 7. Comments - List of comments. Maybe with User, Time and like count information
	* 8. Likes - Count of Likes on post. To extend, a list of user names that liked this post
	* 9. Type - To differentiate between a private message and a public post
	*/
	case HttpRequest(HttpMethods.GET, Uri.Path(path), headers, entity, _) if path startsWith "/post" => {
		var input = entity.asString.parseJson
     	val x = input.convertTo[postREQ]
		val a = sender
		var msg:String = "post:" + x.userID + "." + x.postID
		//var future: Future[postOUT] = ((serverActors(x.userID % 64)) ? msg).mapTo[postOUT]
		var future: Future[postOUT] = ((serverActors(0)) ? x).mapTo[postOUT]
		future onComplete
     	{
	       case Success(response:postOUT) =>{
	       		val body = HttpEntity(ContentTypes.`application/json`, response.toJson.prettyPrint)
	         	a ! HttpResponse(entity = body)
	       }
	       case Failure (error) => println ("Request Timed out")
     	} 
	
	}

	/**
	* Friend List - For user it is the list of friends. For Page, this is the list of members
	* 1. ListID
	* 2. OwnerID - The owner "profile" of this friendList
	* 3. List - Actual List
	*/ 

	case HttpRequest(HttpMethods.GET, Uri.Path(path), _, _, _) if path startsWith "/friendlist" =>{
		var userid= path.split("/").last.toInt //What if no or incorrect value is received?
		val a = sender
		var msg:String = "friendlist:" + userid
		//var future: Future[friendListIN] = ((serverActors(userid % 64)) ? msg).mapTo[friendListIN]
		var future: Future[friendListIN] = ((serverActors(0)) ? msg).mapTo[friendListIN]
		future onComplete
     	{
	       case Success(response:friendListIN) =>{
	       		val body = HttpEntity(ContentTypes.`application/json`, response.toJson.prettyPrint)
	         	a ! HttpResponse(entity = body)
	       }
	       case Failure (error) => println ("Request Timed out")
     	} 
	
	} 
	
	case HttpRequest(HttpMethods.POST, Uri.Path(path), headers, entity, _) if path startsWith "/register" => {
     var input = entity.asString.parseJson
     val x = input.convertTo[userProfileIN]
     //println ("register" + x.name)
     val a = sender
     //var future: Future[String] = ((serverActors(x.id % 64)) ? x).mapTo[String]
     var future: Future[String] = ((serverActors(0)) ? x).mapTo[String]
     
     future onComplete
     {
       case Success(response:String) =>{
         //val body = HttpEntity(ContentTypes.`application/json`, response.toJson.prettyPrint)
         a ! HttpResponse(entity = response)
       }
       case Failure (error) => println ("Request Timed out")
     } 
	}

	case HttpRequest(HttpMethods.POST, Uri.Path(path), headers, entity, _) if path startsWith "/post" => {
     var input = entity.asString.parseJson
     val x = input.convertTo[postIN]
     //println ("makepost" + x.content)
     val a = sender
     //var future: Future[String] = ((serverActors(x.userID % 64)) ? x).mapTo[String]
     var future: Future[String] = ((serverActors(0)) ? x).mapTo[String]
     future onComplete
     {
       case Success(response:String) =>{
         //val body = HttpEntity(ContentTypes.`application/json`, response)
         a ! HttpResponse(entity = response)
       }
       case Failure (error) => println ("Request Timed out")
     }
     
    
	}
  }
}