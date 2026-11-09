package dev.chaosmonkey.demo;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="demo.allow-defects=true")
class ShopTest {
 @Autowired TestRestTemplate http;
 @SuppressWarnings("unchecked") private String fixture(String mode){return "/api/fixtures/"+http.postForObject("/api/fixtures",Map.of("defect",mode),Map.class).get("id");}
 private Map<?,?> state(String root){return http.getForObject(root+"/state",Map.class);}
 private void prepare(String r){http.postForEntity(r+"/cart",Map.of("quantity",1),String.class);http.postForEntity(r+"/coupon",Map.of("code","CHAOS10"),String.class);}
 @Test void fifteenRapidSubmissionsCreateOneOrder(){String r=fixture("NONE");prepare(r);for(int i=0;i<15;i++)http.postForEntity(r+"/checkout",Map.of("idempotencyKey","same"),String.class);assertThat((List<?>)state(r).get("orders")).hasSize(1);assertThat(state(r).get("captures")).isEqualTo(1);}
 @Test void concurrentCouponIsAppliedOnce() throws Exception {String r=fixture("NONE");prepare(r);ExecutorService pool=Executors.newFixedThreadPool(4);try{var tasks=new ArrayList<Callable<Void>>();for(int i=0;i<4;i++)tasks.add(()->{http.postForEntity(r+"/coupon",Map.of("code","CHAOS10"),String.class);return null;});for(var f:pool.invokeAll(tasks))f.get();}finally{pool.shutdownNow();}http.postForEntity(r+"/checkout",Map.of("idempotencyKey","same"),String.class);assertThat(state(r).get("total")).isEqualTo(9000);}
 @Test void duplicateDefectProducesIndependentEvidence(){String r=fixture("DUPLICATE_ORDER");prepare(r);for(int i=0;i<15;i++)http.postForEntity(r+"/checkout",Map.of("idempotencyKey","same"),String.class);assertThat((List<?>)state(r).get("orders")).hasSize(15);}
 @Test void retryAfterDiscardingResponseRemainsIdempotent(){String r=fixture("NONE");prepare(r);http.postForEntity(r+"/checkout",Map.of("idempotencyKey","same"),String.class);http.postForEntity(r+"/checkout",Map.of("idempotencyKey","same"),String.class);assertThat((List<?>)state(r).get("orders")).hasSize(1);}
 @Test void invalidInputIsClientFailure(){String r=fixture("NONE");assertThat(http.postForEntity(r+"/cart",Map.of("quantity",-1),String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);assertThat(http.postForEntity(r+"/cart",Map.of("quantity","invalid"),String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);}
}
