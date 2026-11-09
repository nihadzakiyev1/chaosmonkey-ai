package dev.chaosmonkey.api;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import dev.chaosmonkey.api.Entities.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:qa;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=create-drop","chaos.admin-password=test-password-long","spring.rabbitmq.password=unused","chaos.demo-url=http://localhost:8090"})
@AutoConfigureMockMvc
class ApiTest {
 @Autowired MockMvc mvc; @Autowired Store db;
 @Test void rejectsUnauthenticatedAccess() throws Exception {mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());}
 @Test void basicAuthenticationUsesBcryptCorrectly() throws Exception {mvc.perform(post("/api/v1/auth/login").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("admin","test-password-long"))).andExpect(status().isOk()).andExpect(jsonPath("username").value("admin"));mvc.perform(get("/api/v1/projects").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("admin","wrong"))).andExpect(status().isUnauthorized());}
 @Test @WithMockUser(username="alice") void createsAndListsProject() throws Exception {mvc.perform(post("/api/v1/projects").contentType("application/json").content("{\"name\":\"Checkout\"}")).andExpect(status().isOk()).andExpect(jsonPath("owner").value("alice"));mvc.perform(get("/api/v1/projects")).andExpect(status().isOk());}
 @Test @WithMockUser(username="alice") void blocksCrossProjectAccess() throws Exception {Project p=new Project();p.name="Private";p.owner="bob";p=db.save(p);mvc.perform(get("/api/v1/projects/"+p.id+"/targets")).andExpect(status().isForbidden());}
 @Test @WithMockUser(username="alice") void rejectsUnauthorizedTargetBeforeAnyTraffic() throws Exception {Project p=new Project();p.name="Test";p.owner="alice";p=db.save(p);mvc.perform(post("/api/v1/projects/"+p.id+"/targets").contentType("application/json").content("{\"name\":\"bad\",\"baseUrl\":\"http://169.254.169.254\",\"environment\":\"LOCAL_DEMO\",\"authorized\":true}")).andExpect(status().isForbidden());}
 @Test void atomicClaimPreventsDuplicateProcessing(){Project p=new Project();p.name="Queue";p.owner="alice";p=db.save(p);Target t=new Target();t.name="Demo";t.projectId=p.id;t.baseUrl="http://localhost:8090";t.authorized=true;t=db.save(t);Scenario s=new Scenario();s.targetId=t.id;s.plan="{}";s=db.save(s);Execution e=new Execution();e.scenarioId=s.id;e=db.save(e);assertThat(db.claim(e.id,"Execution")).isTrue();assertThat(db.claim(e.id,"Execution")).isFalse();assertThat(db.get(Execution.class,e.id).status).isEqualTo("RUNNING");}
}
