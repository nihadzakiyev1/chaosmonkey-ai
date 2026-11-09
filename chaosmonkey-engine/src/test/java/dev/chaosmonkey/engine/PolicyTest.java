package dev.chaosmonkey.engine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PolicyTest {
    private final Policy policy=new Policy("http://localhost:8090");
    @Test void requiresExplicitAuthorization(){assertThatThrownBy(()->policy.target("http://localhost:8090",false,"LOCAL_DEMO")).isInstanceOf(SecurityException.class);}
    @Test void rejectsMetadataAndUnapprovedPorts(){for(String url:new String[]{"http://169.254.169.254","http://localhost:8080","http://evil.example","http://localhost:8090@evil.example","http://localhost:8090/api"})assertThatThrownBy(()->policy.target(url,true,"LOCAL_DEMO")).isInstanceOf(SecurityException.class);}
    @Test void rejectsProduction(){assertThatThrownBy(()->policy.target("http://localhost:8090",true,"PRODUCTION")).isInstanceOf(SecurityException.class);}
    @Test void permitsOnlySandboxOrigin(){policy.target("http://localhost:8090",true,"LOCAL_DEMO");assertThat(policy.sameOrigin(java.net.URI.create("http://localhost:8090/cart"))).isTrue();assertThat(policy.sameOrigin(java.net.URI.create("https://localhost:8090"))).isFalse();}
    @Test void redactsStructuredSecrets(){assertThat(Redactor.text("password=secret token=abc email=alice@example.com")).doesNotContain("secret","abc","alice@example.com");}
    @Test void redactsJsonAndBearerValues(){assertThat(Redactor.text("{\"password\":\"secret-value\",\"authorization\":\"Bearer private-token\"}")).doesNotContain("secret-value","private-token");}
    @Test void importsOpenApiWithoutFetchingRemoteRefs() throws Exception {assertThat(new DiscoveryEngine(policy).importOpenApi("{\"openapi\":\"3.0.0\",\"paths\":{\"/orders\":{\"post\":{},\"parameters\":[]}}}")).hasSize(1);}
}
