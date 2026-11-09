package dev.chaosmonkey.ai;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chaosmonkey.engine.*;
import static dev.chaosmonkey.engine.Model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
class ProviderTest {
 private Plan suggest(String response) throws Exception {
  WireMockServer server=new WireMockServer(0);server.start();
  try{
   var envelope=Map.of("id","test","object","chat.completion","model","test-model","choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",response),"finish_reason","stop")),"usage",Map.of("prompt_tokens",100,"completion_tokens",30,"total_tokens",130));
   server.stubFor(post("/v1/chat/completions").willReturn(okJson(new ObjectMapper().writeValueAsString(envelope))));
   var generator=new ScenarioGenerator();var plan=generator.generate(Category.RAPID,42,15,2,Fault.NONE,0);
   var suggested=generator.suggest(plan,new Policy("http://localhost:8090"),"http://localhost:"+server.port()+"/v1","test-key","test-model");
   server.verify(1,postRequestedFor(urlEqualTo("/v1/chat/completions")));return suggested;
  }finally{server.stop();}
 }
 @Test void acceptsOnlyBoundedTypedSuggestion() throws Exception {var p=suggest("{\"category\":\"CONCURRENCY\",\"clicks\":12,\"concurrency\":3}");assertThat(p.category()).isEqualTo(Category.CONCURRENCY);assertThat(p.replay().get("generator")).isEqualTo("langchain4j-v1");}
 @Test void hostileProviderCannotIncreaseBounds() throws Exception {var p=suggest("{\"category\":\"RAPID\",\"clicks\":999999,\"concurrency\":200}");assertThat(p.clicks()).isEqualTo(15);assertThat(p.concurrency()).isEqualTo(2);assertThat(p.replay().get("generator")).isEqualTo("rules-v1");}
 @Test void doesNotAcceptExecutableActionFields() throws Exception {var p=suggest("{\"category\":\"RAPID\",\"clicks\":20,\"concurrency\":3,\"script\":\"process.exit()\"}");assertThat(p.clicks()).isEqualTo(15);assertThat(p.replay().get("generator")).isEqualTo("rules-v1");}
}
