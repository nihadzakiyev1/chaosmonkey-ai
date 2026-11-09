package dev.chaosmonkey.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ShopController {
    public enum Defect { NONE, DUPLICATE_ORDER, DOUBLE_COUPON, INCORRECT_TOTAL, INVENTORY_RACE, MISSING_IDEMPOTENCY, TIMEOUT_INCONSISTENCY }
    public record Fixture(@NotNull Defect defect) {}
    public record Cart(@Min(1) @Max(10) int quantity) {}
    public record Coupon(@NotBlank @Size(max=30) String code) {}
    public record Checkout(@NotBlank @Size(max=100) String idempotencyKey) {}
    public record Order(UUID id, int total, String status) {}
    static final class State {
        int quantity, coupons, inventory=100, captures; boolean authenticated=true;
        Defect defect; List<Order> orders=new ArrayList<>(); Map<String,Order> keys=new HashMap<>();
        State(Defect d) { defect=d; }
    }
    private final Map<UUID,State> fixtures=new ConcurrentHashMap<>();
    private final boolean defectsEnabled;
    public ShopController(@Value("${demo.allow-defects:false}") boolean defectsEnabled) { this.defectsEnabled=defectsEnabled; }
    private State state(UUID id) { State s=fixtures.get(id); if(s==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return s; }
    @PostMapping("/fixtures") public Map<String,UUID> create(@Valid @RequestBody Fixture f) {
        if(f.defect()!=Defect.NONE && !defectsEnabled) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Defect demonstrations disabled");
        if(fixtures.size()>=1000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        UUID id=UUID.randomUUID(); fixtures.put(id,new State(f.defect())); return Map.of("id",id);
    }
    @DeleteMapping("/fixtures/{id}") public void remove(@PathVariable UUID id) { fixtures.remove(id); }
    @GetMapping("/products") public List<Map<String,Object>> products() { return List.of(Map.of("id","p1","name","Mechanical keyboard","price",10000)); }
    @PostMapping("/fixtures/{id}/cart") public Map<String,Object> cart(@PathVariable UUID id,@Valid @RequestBody Cart cart) {
        State s=state(id); synchronized(s) { s.quantity=cart.quantity(); return snapshot(s); }
    }
    @PostMapping("/fixtures/{id}/coupon") public Map<String,Object> coupon(@PathVariable UUID id,@Valid @RequestBody Coupon c) {
        if(!"CHAOS10".equals(c.code())) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Invalid coupon");
        State s=state(id); synchronized(s) { s.coupons=s.defect==Defect.DOUBLE_COUPON ? s.coupons+1 : 1; return snapshot(s); }
    }
    @PostMapping("/fixtures/{id}/checkout") public ResponseEntity<Order> checkout(@PathVariable UUID id,@Valid @RequestBody Checkout c) {
        State s=state(id); synchronized(s) {
            if(!s.authenticated) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            boolean duplicate=s.defect==Defect.DUPLICATE_ORDER || s.defect==Defect.MISSING_IDEMPOTENCY || s.defect==Defect.TIMEOUT_INCONSISTENCY;
            if(!duplicate && s.keys.containsKey(c.idempotencyKey())) return ResponseEntity.ok(s.keys.get(c.idempotencyKey()));
            if(s.quantity<1 || s.inventory<s.quantity) throw new ResponseStatusException(HttpStatus.CONFLICT,"Cart or inventory unavailable");
            int total=s.quantity*10000-s.coupons*1000+(s.defect==Defect.INCORRECT_TOTAL?501:0);
            Order o=new Order(UUID.randomUUID(),total,"CAPTURED_MOCK"); s.orders.add(o); s.keys.put(c.idempotencyKey(),o);
            s.captures++; if(s.defect!=Defect.INVENTORY_RACE) s.inventory-=s.quantity;
            return ResponseEntity.status(s.defect==Defect.TIMEOUT_INCONSISTENCY?504:200).body(o);
        }
    }
    @PostMapping("/fixtures/{id}/logout") public void logout(@PathVariable UUID id) { State s=state(id); synchronized(s) { s.authenticated=false; } }
    @PostMapping("/fixtures/{id}/cancel") public Map<String,Object> cancel(@PathVariable UUID id) {
        State s=state(id); synchronized(s) { for(int i=0;i<s.orders.size();i++) { Order o=s.orders.get(i); s.orders.set(i,new Order(o.id(),o.total(),"CANCELLED")); } return snapshot(s); }
    }
    @GetMapping("/fixtures/{id}/state") public Map<String,Object> get(@PathVariable UUID id) { State s=state(id); synchronized(s) { return snapshot(s); } }
    private Map<String,Object> snapshot(State s) { return Map.of("quantity",s.quantity,"couponApplications",s.coupons,"inventory",s.inventory,"captures",s.captures,"orders",List.copyOf(s.orders),"total",s.orders.isEmpty()?s.quantity*10000-s.coupons*1000:s.orders.getLast().total()); }
}
