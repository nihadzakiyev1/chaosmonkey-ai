package dev.chaosmonkey.api;

import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import dev.chaosmonkey.api.Entities.*;

@Repository
public class Store {
    @PersistenceContext private EntityManager em;
    @Transactional public <T extends Row> T save(T row) { return em.merge(row); }
    public <T> T get(Class<T> type,UUID id) { T row=em.find(type,id); if(row==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND); return row; }
    public <T> List<T> list(Class<T> type,int page,int size) { return em.createQuery("from "+type.getSimpleName()+" order by createdAt desc",type).setFirstResult(page*size).setMaxResults(size).getResultList(); }
    public List<Project> projects(String owner) { return em.createQuery("from Project where owner=:owner order by createdAt desc",Project.class).setParameter("owner",owner).setMaxResults(200).getResultList(); }
    @Transactional public boolean claim(UUID id,String entity) {
        if(!Set.of("Execution","Discovery").contains(entity))throw new IllegalArgumentException();
        String fields=entity.equals("Execution")?", startedAt=:now":"";
        var query=em.createQuery("update "+entity+" set status='RUNNING'"+fields+" where id=:id and status='QUEUED'").setParameter("id",id);
        if(entity.equals("Execution"))query.setParameter("now",java.time.Instant.now());
        return query.executeUpdate()==1;
    }
    @Transactional public void cancel(UUID id) { em.createQuery("update Execution set cancelRequested=true where id=:id and status in ('QUEUED','RUNNING')").setParameter("id",id).executeUpdate(); }
    @Transactional public void finish(Execution execution,List<FindingRow> findings) {
        em.merge(execution);for(FindingRow finding:findings)em.merge(finding);
    }
    @Transactional public void recover() {
        em.createQuery("update Execution set status='INCONCLUSIVE', endedAt=:now where status='RUNNING' and startedAt<:cutoff")
          .setParameter("now",java.time.Instant.now()).setParameter("cutoff",java.time.Instant.now().minusSeconds(180)).executeUpdate();
        em.createQuery("update Discovery set status='INCONCLUSIVE' where status='RUNNING' and createdAt<:cutoff")
          .setParameter("cutoff",java.time.Instant.now().minusSeconds(180)).executeUpdate();
    }
    public <T> List<T> queued(Class<T> type) { return em.createQuery("from "+type.getSimpleName()+" where status='QUEUED' order by createdAt",type).setMaxResults(5).getResultList(); }
    public List<Target> targets(UUID project) { return em.createQuery("from Target where projectId=:id order by createdAt",Target.class).setParameter("id",project).getResultList(); }
}
