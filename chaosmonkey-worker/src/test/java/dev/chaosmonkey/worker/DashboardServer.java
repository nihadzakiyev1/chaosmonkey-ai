package dev.chaosmonkey.worker;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.concurrent.*;

/** Test-only same-origin static host and API proxy. No simulated API responses. */
final class DashboardServer implements AutoCloseable {
    final HttpServer server;
    private final ExecutorService pool=Executors.newFixedThreadPool(4);
    DashboardServer(Path dist,int apiPort) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(pool);
        HttpClient client=HttpClient.newHttpClient();Path base=dist.toAbsolutePath().normalize();
        server.createContext("/",exchange->{try{
            String path=exchange.getRequestURI().getPath();byte[] bytes;String type;int status=200;
            if(path.startsWith("/api/")){
                HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("http://localhost:"+apiPort+exchange.getRequestURI()));
                for(String header:new String[]{"Authorization","Content-Type"})if(exchange.getRequestHeaders().getFirst(header)!=null)request.header(header,exchange.getRequestHeaders().getFirst(header));
                byte[] body=exchange.getRequestBody().readAllBytes();request.method(exchange.getRequestMethod(),body.length==0?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
                var response=client.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());bytes=response.body();status=response.statusCode();type=response.headers().firstValue("Content-Type").orElse("application/json");
            }else{
                Path file=base.resolve(path.equals("/")?"index.html":path.substring(1)).normalize();
                if(!file.startsWith(base)||!Files.isRegularFile(file)){status=404;bytes=new byte[0];type="text/plain";}
                else{bytes=Files.readAllBytes(file);type=file.toString().endsWith(".js")?"application/javascript":file.toString().endsWith(".css")?"text/css":"text/html";}
            }
            exchange.getResponseHeaders().set("Content-Type",type);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
        }catch(Exception e){exchange.sendResponseHeaders(500,0);}finally{exchange.close();}});
        server.start();
    }
    String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}
    public void close(){server.stop(0);pool.shutdownNow();}
}
