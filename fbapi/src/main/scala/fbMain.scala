
import akka.actor._
import akka.io.IO
import spray.can.Http
import spray.http.{HttpMethods, HttpRequest, HttpResponse, Uri}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import java.io.File
import com.typesafe.config.ConfigFactory


object fbMain{

def main(args: Array[String]){

        implicit val system = ActorSystem("webServer")
        var serverActors:Array[ActorSelection] = new Array[ActorSelection](64)
        for (i <- 0 to 63)
        {
            serverActors(i) = system.actorSelection("akka.tcp://ServerSystem@127.0.0.1:2015/user/server" + i)
        }

        val appListener = system.actorOf(Props(new fbAPI(serverActors)), name = "appListener")

        implicit val timeout = Timeout(100.seconds)
        IO(Http) ? Http.Bind(appListener, interface = "127.0.0.1", port = 8080) //Should the localhost be replaced by "127.0.0.1". Why?

    }
}