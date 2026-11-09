package dev.chaosmonkey.demo;

import dev.chaosmonkey.ai.ScenarioGenerator;
import dev.chaosmonkey.engine.*;
import dev.chaosmonkey.engine.Model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named="chaos.browser",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="demo.allow-defects=true")
class BrowserAcceptanceTest {
 @LocalServerPort int port;
 @TempDir Path artifacts;
 private Result run(Category category,Fault fault,String defect){String url="http://localhost:"+port;return new CheckoutExecutor(new Policy(url)).execute(url,new ScenarioGenerator().generate(category,42,15,2,fault,100),defect,artifacts.resolve(category+"-"+fault+"-"+defect),()->false);}
 @Test void rapidClicksPassOnCorrectShop(){Result r=run(Category.RAPID,Fault.NONE,"NONE");assertThat(r.status()).withFailMessage("%s",r.timeline()).isEqualTo(Status.PASSED);assertThat(r.assertions()).allMatch(Check::passed);assertThat(r.artifacts()).containsKeys("trace","screenshot","state","scenario");}
 @Test void duplicateDefectIsConfirmedWithStateEvidence(){Result r=run(Category.RAPID,Fault.NONE,"DUPLICATE_ORDER");assertThat(r.status()).withFailMessage("%s",r.timeline()).isEqualTo(Status.FAILED);assertThat(r.findings()).anyMatch(f->f.title().equals("No duplicate orders")&&f.confidence().equals("CONFIRMED"));}
 @Test void overlappingCouponsRemainCorrect(){Result r=run(Category.CONCURRENCY,Fault.NONE,"NONE");assertThat(r.status()).withFailMessage("%s",r.timeline()).isEqualTo(Status.PASSED);}
 @Test void interruptedResponseThenRetryIsSafe(){Result r=run(Category.NETWORK,Fault.LOST_RESPONSE,"NONE");assertThat(r.status()).withFailMessage("%s",r.timeline()).isEqualTo(Status.PASSED);assertThat(r.timeline()).anyMatch(e->e.kind().equals("FAULT"));}
 @Test void interruptedResponseDetectsMissingIdempotency(){Result r=run(Category.NETWORK,Fault.LOST_RESPONSE,"MISSING_IDEMPOTENCY");assertThat(r.status()).withFailMessage("%s",r.timeline()).isEqualTo(Status.FAILED);}
 @Test void injected503IsRecordedAndNotMisclassified(){Result r=run(Category.NETWORK,Fault.HTTP_503,"NONE");assertThat(r.status()).withFailMessage("%s",r.timeline()).isEqualTo(Status.PASSED);assertThat(r.timeline()).anyMatch(e->e.kind().equals("FAULT"));}
 @Test void discoversCheckoutControls(){String url="http://localhost:"+port;var model=new DiscoveryEngine(new Policy(url)).discover(url,3,1);assertThat(model.pages()).isNotEmpty();assertThat(model.pages().getFirst().elements()).anyMatch(e->e.name().equals("Place sandbox order"));}
 @ParameterizedTest @ValueSource(strings={"DOUBLE_COUPON","INCORRECT_TOTAL","INVENTORY_RACE","TIMEOUT_INCONSISTENCY"})
 void independentlyDetectsOtherDemoDefects(String defect){Result r=run(Category.CONCURRENCY,Fault.NONE,defect);assertThat(r.status()).withFailMessage("%s",r.timeline()).isEqualTo(Status.FAILED);assertThat(r.findings()).anyMatch(f->f.confidence().equals("CONFIRMED"));}
 @ParameterizedTest @EnumSource(value=Fault.class,names={"BLOCK","TIMEOUT","HTTP_500","OFFLINE","DELAY"})
 void faultThenRetryPreservesCorrectState(Fault fault){Result r=run(Category.NETWORK,fault,"NONE");assertThat(r.status()).withFailMessage("%s",r.timeline()).isEqualTo(Status.PASSED);}
 @ParameterizedTest @EnumSource(value=Category.class,names={"BOUNDARY","CONTRACT","SESSION","STATE"})
 void categoryChecksPassOnCorrectShop(Category category){Result r=run(category,Fault.NONE,"NONE");assertThat(r.status()).withFailMessage("%s",r.timeline()).isEqualTo(Status.PASSED);}
}
