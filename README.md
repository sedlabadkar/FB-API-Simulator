# FB-API-Simulator

Distributed graph api simulator engine in Scala, akka, and spray

## What is it?

* In this project I have implemented a significant part of Facebook's graph API and a client tester/simulator. 
* Supports the following APIs:
  * Page
  * User
  * FriendList
  * Post - When made private can act as a private message
  * Notifications
* All the data is stored in memory. No database operations
* Supports End-To-End Encryption
* Also includes a client simulator that simulates ~10000 users. 

---

* There are three sub parts to this project.
	* Server - Server is the actual back end server with all user data. There are 64 instances of this and the clients are divided equally. 
	* fbAPI - fbAPI is the application level listener for the HTTP messages from the client. It also forms HTTP response messages based on server's reply.
	* Client - Client is the front end simulator that makes HTTP requests to user the fbAPI's provided by the server. The simulator tries to simulate 10000 users

* The above three sub parts are arranged in three folders namely Server, fbApi and Client. Each component has its own build.sbt file. 

* To execute the components need to be started in the following order:
	* First the server needs to start
	* Followed by fbapi
	* And last is the client

### Security ###

* Posts are of two kinds: Public posts and Private messages. Public posts are meant for all friends and Private messages are meant for a single friend. These restrictions are enforced cryptographically as well as operationally. A posts can only be decrypted by its intended recipients. 

* At the time of registration, users update their public keys in the server. 

* Upon successful registration, users request friend list from the server, this includes all the public keys for all the friends. A small timeout has been added between registration and requesting friend list, this is done to make sure that all users have updated their keys with the server.

* Users start making posts(Both public and private posts) once they have the friend list. The posts are made at varying rates. 

* For making post, user first encrypts the post content with AES-128 (IV is changed for every post). The AES encryption key is then wrapped using the public keys of post's intended recipients. The intended recipients are notified when the post is successfully sent to the server. 

* When a user receives a notification, it can choose to view the post. If it does so, then it will request the server for the post. Server checks whether the requesting user is part of the post's intended users. If that is the case, then server sends the encrypted post along with the RSA encrypted key to decrypt it. 
  * If the requesting user is not the intended recepient of the post, the server could still send the post back. However, the user won't be able to read it.
