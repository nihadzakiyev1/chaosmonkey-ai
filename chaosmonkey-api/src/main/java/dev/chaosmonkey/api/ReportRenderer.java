package dev.chaosmonkey.api;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

public final class ReportRenderer {
    private final ObjectMapper json;
    public ReportRenderer(ObjectMapper json){this.json=json;}
    public String html(String document,Path screenshot) throws Exception {
        JsonNode doc=json.readTree(document), execution=doc.path("execution"), result=json.readTree(execution.path("result").asText("{}"));
        StringBuilder h=new StringBuilder("<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>ChaosMonkey QA · Evidence report</title><style>body{font:14px system-ui;color:#263047;background:#f4f5fa;max-width:1050px;margin:40px auto;padding:24px}header,section{background:white;border:1px solid #dae0ed;border-radius:12px;padding:28px;margin-bottom:22px}h1{font-size:30px}h2{font-size:19px;color:#483c80}p,li{line-height:1.7}table{border-collapse:collapse;width:100%;font-size:12px}td,th{padding:12px;border-bottom:1px solid #e0e5ef;text-align:left;overflow-wrap:anywhere}pre{white-space:pre-wrap;background:#f1f3f9;padding:16px;border-radius:8px}img{width:100%;border-radius:9px}.pass{color:#137458}.fail{color:#ad294e}.meta{color:#758098}small{color:#69748a}</style><header><small>CHAOSMONKEY QA · EVIDENCE REPORT</small><h1>Checkout exploration</h1>");
        h.append("<p>Break the expected. Discover the unexpected.</p><p class=\"meta\">Execution ").append(escape(execution.path("id").asText())).append(" · ").append(escape(execution.path("createdAt").asText())).append("</p></header>");
        int confirmed=0;Map<String,Integer> severities=new TreeMap<>();
        for(JsonNode f:result.path("findings")){if(f.path("confidence").asText().equals("CONFIRMED"))confirmed++;severities.merge(f.path("severity").asText(),1,Integer::sum);}
        h.append("<section><h2>Executive summary</h2><p>Outcome: <strong>").append(escape(execution.path("status").asText())).append("</strong>. ").append(confirmed).append(" independently confirmed findings across ").append(result.path("assertions").size()).append(" business assertions.</p><p>AI explanations are not used as proof. Expected injected HTTP failures are recorded as fault evidence.</p><p>Severity distribution: ").append(escape(severities.toString())).append("</p></section>");
        h.append("<section><h2>Target and scope</h2><pre>").append(escape(json.writerWithDefaultPrettyPrinter().writeValueAsString(doc.path("target")))).append("</pre><p>One synthetic cart fixture, local mock payments, bounded checkout actions. No real payment provider is used.</p></section>");
        h.append("<section><h2>Assertions</h2><table><thead><tr><th>Invariant</th><th>Expected</th><th>Actual</th><th>Outcome</th></tr></thead><tbody>");
        for(JsonNode c:result.path("assertions"))h.append("<tr><td>").append(escape(c.path("invariant").asText())).append("</td><td>").append(escape(c.path("expected").toString())).append("</td><td>").append(escape(c.path("actual").toString())).append("</td><td class=\"").append(c.path("passed").asBoolean()?"pass":"fail").append("\">").append(c.path("verified").asBoolean()?(c.path("passed").asBoolean()?"PASSED":"FAILED"):"UNVERIFIED").append("</td></tr>");
        h.append("</tbody></table></section><section><h2>Findings and reproduction</h2>");
        if(result.path("findings").isEmpty())h.append("<p>No findings were produced. A passing run does not prove the absence of defects.</p>");
        for(JsonNode f:result.path("findings")){h.append("<h3>").append(escape(f.path("title").asText())).append("</h3><p>").append(escape(f.path("severity").asText())).append(" · ").append(escape(f.path("confidence").asText())).append("</p><p>Expected: ").append(escape(f.path("expected").asText())).append("<br>Actual: ").append(escape(f.path("actual").asText())).append("</p><ol>");for(JsonNode step:f.path("reproduction"))h.append("<li>").append(escape(step.asText())).append("</li>");h.append("</ol>");}
        h.append("</section><section><h2>Final browser state</h2>");
        if(Files.isRegularFile(screenshot)&&Files.size(screenshot)<=4_000_000)h.append("<img alt=\"Final checkout browser state\" src=\"data:image/png;base64,").append(Base64.getEncoder().encodeToString(Files.readAllBytes(screenshot))).append("\">");
        else h.append("<p>Screenshot unavailable or expired under the evidence retention policy.</p>");
        h.append("</section><section><h2>Replay configuration</h2><pre>").append(escape(json.writerWithDefaultPrettyPrinter().writeValueAsString(doc.path("scenario")))).append("</pre><p>Use the saved scenario in the dashboard with a fresh fixture. The seed reproduces the action plan and selected fault; concurrent race timing can vary.</p></section><section><h2>Execution timeline</h2><table><thead><tr><th>Time</th><th>Type</th><th>Observation</th></tr></thead><tbody>");
        for(JsonNode e:result.path("timeline"))h.append("<tr><td>").append(escape(e.path("at").asText())).append("</td><td>").append(escape(e.path("kind").asText())).append("</td><td>").append(escape(e.path("message").asText())).append("</td></tr>");
        return h.append("</tbody></table></section><section><h2>Recommended next actions</h2><p>").append(escape(doc.path("recommendedAction").asText())).append("</p></section></html>").toString();
    }
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
