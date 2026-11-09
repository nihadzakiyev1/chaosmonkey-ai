package dev.chaosmonkey.worker;

import dev.chaosmonkey.api.Entities.*;
import dev.chaosmonkey.demo.DemoApplication;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.nio.file.*;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@EnabledIfSystemProperty(named="chaos.browser",matches="true")
@SpringBootTest(classes=WorkerApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:fullstack;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver",
 "spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=validate",
 "chaos.admin-password=integration-password","spring.rabbitmq.password=unused","spring.rabbitmq.connection-timeout=100ms",
 "spring.rabbitmq.listener.simple.auto-startup=false","management.health.rabbit.enabled=false","management.health.redis.enabled=false",
 "chaos.evidence-directory=./target/fullstack-evidence"})
class FullStackTest {
 static ConfigurableApplicationContext shop;
 @DynamicPropertySource static void demo(DynamicPropertyRegistry registry){
  shop=new SpringApplicationBuilder(DemoApplication.class).run("--server.port=0","--demo.allow-defects=true",
   "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration");
  int port=((ServletWebServerApplicationContext)shop).getWebServer().getPort();registry.add("chaos.demo-url",()->"http://localhost:"+port);
 }
 @Autowired TestRestTemplate http; @Autowired ObjectMapper json;
 @LocalServerPort int apiPort;
 private TestRestTemplate client(){return http.withBasicAuth("admin","integration-password");}
 @AfterAll static void close(){if(shop!=null)shop.close();}
 @Test void persistedQueueToEvidenceToReportAndReplay() throws Exception {
  var client=client();Project p=client.postForObject("/api/v1/projects",Map.of("name","Integration checkout"),Project.class);assertThat(p.id).isNotNull();
  String url="http://localhost:"+((ServletWebServerApplicationContext)shop).getWebServer().getPort();
  Target t=client.postForObject("/api/v1/projects/"+p.id+"/targets",Map.of("name","Local demo","baseUrl",url,"environment","LOCAL_DEMO","authorized",true),Target.class);
  Scenario s=client.postForObject("/api/v1/scenarios/generate",Map.of("targetId",t.id,"category","RAPID","seed",42,"clicks",15,"concurrency",2,"fault","NONE","delayMs",0),Scenario.class);
  for(String defect:List.of("NONE","DUPLICATE_ORDER")){
   Execution e=client.postForObject("/api/v1/executions",Map.of("scenarioId",s.id,"defect",defect,"confirmed",true),Execution.class);
   await().atMost(Duration.ofSeconds(100)).pollInterval(Duration.ofMillis(500)).until(()->{String status=client.getForObject("/api/v1/executions/"+e.id,Execution.class).status;return !Set.of("QUEUED","RUNNING").contains(status);});
   Execution finished=client.getForObject("/api/v1/executions/"+e.id,Execution.class);
   assertThat(finished.status).withFailMessage("%s",finished.result).isEqualTo(defect.equals("NONE")?"PASSED":"FAILED");
   assertThat(json.readTree(finished.result).path("artifacts").has("trace")).isTrue();
   assertThat(client.getForEntity("/api/v1/executions/"+e.id+"/evidence/state.json",String.class).getStatusCode().value()).isEqualTo(200);
   Report report=client.postForObject("/api/v1/reports",Map.of("executionId",e.id),Report.class);
   assertThat(client.getForObject("/api/v1/reports/"+report.id+"?format=html",String.class)).contains("Evidence report");
  }
  FindingRow[] findings=client.getForObject("/api/v1/findings",FindingRow[].class);
  assertThat(findings).anyMatch(f->f.title.equals("No duplicate orders")&&f.confidence.equals("CONFIRMED"));
 }
 @Test void dashboardRunsAgainstRealApiAndWorker() throws Exception {
  Path dist=Path.of("../chaosmonkey-web/dist");Assumptions.assumeTrue(Files.isRegularFile(dist.resolve("index.html")),"Build frontend to enable full dashboard acceptance test");
  List<String> errors=new java.util.concurrent.CopyOnWriteArrayList<>();
  try(DashboardServer host=new DashboardServer(dist,apiPort);Playwright pw=Playwright.create();Browser browser=pw.chromium().launch();BrowserContext context=browser.newContext(new Browser.NewContextOptions().setViewportSize(1440,1000))){
   Page page=context.newPage();page.setDefaultTimeout(15000);page.onPageError(errors::add);page.navigate(host.url());
   page.getByLabel("Username",new Page.GetByLabelOptions().setExact(true)).fill("admin");page.getByLabel("Password",new Page.GetByLabelOptions().setExact(true)).fill("integration-password");page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Sign in").setExact(true)).click();
   page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Projects").setExact(true)).click();
   page.getByLabel("Project name",new Page.GetByLabelOptions().setExact(true)).fill("Dashboard checkout");page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Create project").setExact(true)).click();
   page.getByLabel("I am authorized to test this isolated local demo.").check();page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Save authorized target")).click();
   page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Autonomous Testing").setExact(true)).click();
   page.getByLabel("I confirm state-changing tests against this sandbox fixture.").check();page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Generate & execute")).click();
   page.getByRole(AriaRole.HEADING,new Page.GetByRoleOptions().setName("Business assertions")).waitFor(new Locator.WaitForOptions().setTimeout(100000));
   assertThat(page.locator(".assertion .badge.failed").count()).isZero();assertThat(page.locator(".assertion").count()).isGreaterThan(5);
   page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName("Overview").setExact(true)).click();page.waitForTimeout(600);
   page.screenshot(new Page.ScreenshotOptions().setPath(Path.of("target/dashboard-desktop.png")).setFullPage(true));
   page.setViewportSize(390,844);page.waitForTimeout(300);page.screenshot(new Page.ScreenshotOptions().setPath(Path.of("target/dashboard-mobile.png")).setFullPage(true));
   assertThat((Boolean)page.evaluate("document.documentElement.scrollWidth <= innerWidth")).isTrue();assertThat(errors).isEmpty();
  }
 }
}

