package dev.chaosmonkey.api;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class ReportRendererTest {
 @Test void escapesUntrustedEvidenceAndHandlesExpiredScreenshot() throws Exception {
  var json=new ObjectMapper();String result=json.writeValueAsString(Map.of("assertions",java.util.List.of(),"findings",java.util.List.of(),"timeline",java.util.List.of(Map.of("message","<script>alert('x')</script>"))));
  String document=json.writeValueAsString(Map.of("execution",Map.of("id","test","status","INCONCLUSIVE","result",result),"scenario",Map.of("title","<unsafe>"),"target",Map.of("name","sandbox")));
  String html=new ReportRenderer(json).html(document,Path.of("missing-screenshot.png"));
  assertThat(html).contains("Executive summary","expired","&lt;script&gt;","&lt;unsafe&gt;").doesNotContain("<script>","<unsafe>");
 }
}
