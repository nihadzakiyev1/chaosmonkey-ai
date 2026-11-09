package dev.chaosmonkey.engine;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
class TransportTest {
    @Test void doesNotFollowOutOfScopeRedirect(){WireMockServer server=new WireMockServer(0);server.start();try{server.stubFor(get("/api/start").willReturn(aResponse().withStatus(302).withHeader("Location","http://169.254.169.254/latest/meta-data")));String url="http://localhost:"+server.port();var client=new SandboxClient(url,new Policy(url),()->false,System.nanoTime()+10_000_000_000L);assertThat(client.request("GET","/api/start",null).statusCode()).isEqualTo(302);server.verify(1,getRequestedFor(urlEqualTo("/api/start")));}finally{server.stop();}}
}
