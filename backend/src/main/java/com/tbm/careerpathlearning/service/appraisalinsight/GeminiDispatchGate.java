package com.tbm.careerpathlearning.service.appraisalinsight;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.service.GeminiClient.AppraisalProviderException;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.*;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/** Shared metadata-only budgets and leases. Database time is authoritative across instances. */
@Component @MetadataOnlyLogging
public class GeminiDispatchGate {
    public record Permit(UUID id,UUID workId,String model,long tokenBound) {}
    public record Admission(Permit permit,OffsetDateTime queuedAt) {}
    public record Decision(Permit permit,String reason,OffsetDateTime retryAt) {}
    public record Counts(long active,long queued,long dispatches,long cacheHits,long throttled,long failures,long waitMillis) {}
    public static class Rejected extends RejectedExecutionException {
        private final String code;
        private final OffsetDateTime retryAt;
        public Rejected(String code,OffsetDateTime retryAt) { super("AI generation admission unavailable: "+code);this.code=code;this.retryAt=retryAt; }
        public String code() { return code; }
        public OffsetDateTime retryAt() { return retryAt; }
    }
    private record Budget(String reason,OffsetDateTime retryAt) {}
    private static final Logger log=LoggerFactory.getLogger(GeminiDispatchGate.class);
    private static final ZoneId PROVIDER_DAY=ZoneId.of("America/Los_Angeles");
    private final JdbcTemplate db;
    private final GeminiDispatchSettings settings;
    private final TransactionTemplate transaction;
    private final Supplier<OffsetDateTime> time;
    private final LongAdder dispatches=new LongAdder(),cacheHits=new LongAdder(),throttled=new LongAdder(),failures=new LongAdder(),waitMillis=new LongAdder();

    @Autowired
    public GeminiDispatchGate(JdbcTemplate db,PlatformTransactionManager manager,GeminiDispatchSettings settings) {
        this(db,manager,settings,()->db.queryForObject("select clock_timestamp()",OffsetDateTime.class));
    }
    public GeminiDispatchGate(JdbcTemplate db,PlatformTransactionManager manager,GeminiDispatchSettings settings,Supplier<OffsetDateTime> time) {
        this.db=db;this.settings=settings;this.time=time;transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public boolean enabled() { return settings.isEnabled(); }
    public int workers() { return settings.getWorkers(); }
    public int queueCapacity() { return settings.getQueueCapacity(); }
    public long queueLeaseSeconds() { return settings.getQueueWaitSeconds()+180L; }
    public void cacheHit() { cacheHits.increment(); }
    public void providerFailure() { failures.increment(); }

    public Admission enqueue(AppraisalGenerationTask task,UUID instance) {
        configured(task.model());
        if (task.tokenBound()<=0) throw new Rejected("NOT_CONFIGURED",null);
        return transaction.execute(s->{
            lock(task.model());var now=now();recover(task.model(),now);
            if (count("select count(*) from gemini_appraisal_work where id=?",task.id())!=0)
                throw new Rejected("BUSY",now.plusSeconds(1));
            var budget=budget(task.model(),task.tokenBound(),null,now);
            if (budget!=null && ("DAILY_LIMIT".equals(budget.reason()) || "TOKEN_LIMIT".equals(budget.reason())))
                throw new Rejected("RATE_LIMITED",budget.retryAt());
            boolean ready=budget==null && active(task.model(),now)<settings.getWorkers();
            if (!ready && queued(task.model())>=settings.getQueueCapacity()) { throttled.increment();throw new Rejected("BUSY",now.plusSeconds(1)); }
            db.update("insert into gemini_appraisal_work(id,project_key,model,instance_id,kind,token_bound,phase,enqueued_at,expires_at) values(?,?,?,?,?,?,'QUEUED',?,?)",
                    task.id(),key(),task.model(),instance,task.kind(),task.tokenBound(),now,now.plusSeconds(settings.getQueueWaitSeconds()));
            Permit permit=ready?reserveWork(task.id(),task.model(),task.tokenBound(),now):null;
            return new Admission(permit,now);
        });
    }
    public Decision claim(UUID workId,UUID instance,String model) {
        configured(model);
        return transaction.execute(s->{
            lock(model);var now=now();recover(model,now);
            var work=db.queryForList("select phase,token_bound from gemini_appraisal_work where id=? and instance_id=? and project_key=? and model=?",workId,instance,key(),model);
            if (work.isEmpty() || !"QUEUED".equals(work.get(0).get("phase"))) return new Decision(null,"CANCELLED",null);
            if (active(model,now)>=settings.getWorkers()) return new Decision(null,"CAPACITY",now.plusSeconds(1));
            // Preserve queue order across instances without transferring executable closures between processes.
            UUID first=db.queryForObject("select id from gemini_appraisal_work where project_key=? and model=? and phase='QUEUED' order by enqueued_at,id limit 1",UUID.class,key(),model);
            if (!workId.equals(first)) return new Decision(null,"CAPACITY",now.plusSeconds(1));
            long tokens=((Number)work.get(0).get("token_bound")).longValue();var budget=budget(model,tokens,null,now);
            if (budget!=null) return new Decision(null,budget.reason(),budget.retryAt());
            return new Decision(reserveWork(workId,model,tokens,now),null,null);
        });
    }
    private Permit reserveWork(UUID id,String model,long tokens,OffsetDateTime now) {
        var permit=reserve(id,model,tokens,now);
        db.update("update gemini_appraisal_work set phase='RESERVED',permit_id=?,lease_until=? where id=?",permit.id(),now.plusSeconds(180),id);
        var queuedAt=db.queryForObject("select enqueued_at from gemini_appraisal_work where id=?",OffsetDateTime.class,id);
        waitMillis.add(Math.max(0,Duration.between(queuedAt,now).toMillis()));
        log.info("Appraisal dispatch reserved: active={} queued={}",active(model,now),queued(model));
        return permit;
    }
    private Permit reserve(UUID work,String model,long tokens,OffsetDateTime now) {
        var id=UUID.randomUUID();
        db.update("insert into gemini_request_permit(id,project_key,model,work_id,phase,reserved_tokens,charged_tokens,reserved_at,lease_until) values(?,?,?,?,'RESERVED',?,?,?,?)",
                id,key(),model,work,tokens,tokens,now,now.plusSeconds(180));
        return new Permit(id,work,model,tokens);
    }

    /** SOP retains its dedicated execution queue. Delays use Reactor timers, never appraisal workers. */
    public Permit awaitSopPermit(String model,long tokens) {
        configured(model);
        return sopPermit(model,tokens).timeout(Duration.ofSeconds(settings.getQueueWaitSeconds()))
                .onErrorMap(error->error instanceof AppraisalProviderException?error:new AppraisalProviderException("RATE_LIMITED")) .block();
    }
    private Mono<Permit> sopPermit(String model,long tokens) {
        return Mono.fromCallable(()->transaction.execute(s->{
            lock(model);var now=now();recover(model,now);var budget=budget(model,tokens,null,now);
            if (budget==null) return new Decision(reserve(null,model,tokens,now),null,null);
            if (Set.of("DAILY_LIMIT","TOKEN_LIMIT").contains(budget.reason())) throw providerRefused(budget.retryAt(),now);
            return new Decision(null,budget.reason(),budget.retryAt());
        })).subscribeOn(Schedulers.boundedElastic()).flatMap(decision->{
            if (decision.permit()!=null) return Mono.just(decision.permit());
            return Mono.delay(Duration.ofSeconds(1)).then(Mono.defer(()->sopPermit(model,tokens)));
        });
    }

    /** Callback and provider charge commit together immediately before the external request. */
    public void begin(Permit permit,long actualBound,Runnable beforeDispatch) {
        configured(permit.model());
        transaction.executeWithoutResult(s->{
            lock(permit.model());var now=now();
            var found=db.queryForList("select phase,lease_until from gemini_request_permit where id=? and project_key=? and model=? for update",permit.id(),key(),permit.model());
            if (found.isEmpty() || !"RESERVED".equals(found.get(0).get("phase"))
                    || !date(found.get(0).get("lease_until")).isAfter(now) || actualBound>permit.tokenBound())
                throw new AppraisalProviderException("NOT_CONFIGURED");
            var budget=budget(permit.model(),permit.tokenBound(),permit.id(),now);
            if (budget!=null) throw providerRefused(budget.retryAt(),now);
            beforeDispatch.run();
            db.update("update gemini_request_permit set phase='DISPATCHED',dispatched_at=?,lease_until=? where id=?",now,now.plusSeconds(150),permit.id());
            if (permit.workId()!=null) db.update("update gemini_appraisal_work set phase='DISPATCHED',lease_until=? where id=? and phase='RESERVED'",now.plusSeconds(150),permit.workId());
            dispatches.increment();
        });
    }
    public void finishPermit(Permit permit,Long inputTokens,boolean responseKnown) {
        transaction.executeWithoutResult(s->{
            lock(permit.model());
            db.update("update gemini_request_permit set phase='RELEASED' where id=? and phase='RESERVED'",permit.id());
            if (inputTokens!=null && inputTokens>=0)
                db.update("update gemini_request_permit set charged_tokens=? where id=? and phase='DISPATCHED'",inputTokens,permit.id());
            db.update("update gemini_request_permit set phase=? where id=? and phase='DISPATCHED'",responseKnown?"FINISHED":"UNCERTAIN",permit.id());
        });
    }
    public void finishWork(UUID id,String model) {
        transaction.executeWithoutResult(s->{
            lock(model);
            db.update("update gemini_request_permit set phase='RELEASED' where work_id=? and phase='RESERVED'",id);
            db.update("update gemini_appraisal_work set phase='FINISHED',lease_until=null where id=? and phase in ('QUEUED','RESERVED','DISPATCHED')",id);
        });
    }
    public void cancelQueued(UUID id,String model) {
        transaction.executeWithoutResult(s->{
            lock(model);var now=now();
            if (count("select count(*) from gemini_appraisal_work where id=? and phase in ('QUEUED','RESERVED')",id)>0) cancel(id,now);
        });
    }
    public void providerRateLimited(String model,AppraisalProviderException failure) {
        transaction.executeWithoutResult(s->{
            lock(model);var retry=failure.retryAt(now());
            db.update("update gemini_quota_scope set retry_until=greatest(coalesce(retry_until,?),?) where project_key=? and model=?",retry,retry,key(),model);
        });
    }
    public OffsetDateTime retryAt(String model) {
        if (!settings.configured(model)) return null;
        var values=db.query("select retry_until from gemini_quota_scope where project_key=? and model=?",(rs,row)->rs.getObject(1,OffsetDateTime.class),key(),model);
        return values.isEmpty()?null:values.get(0);
    }
    public void recover(String model) {
        if (!settings.configured(model)) return;
        transaction.executeWithoutResult(s->{lock(model);recover(model,now());});
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=30000,initialDelay=30000)
    public void recoverConfiguredScope() { if (settings.configured(settings.getModel())) recover(settings.getModel()); }
    public Counts counts(String model) {
        return transaction.execute(s->{var now=now();return new Counts(active(model,now),queued(model),dispatches.sum(),cacheHits.sum(),throttled.sum(),failures.sum(),waitMillis.sum());});
    }
    public boolean waiting(UUID id) { return count("select count(*) from gemini_appraisal_work where id=? and phase='QUEUED'",id)>0; }

    private void configured(String model) { if (!settings.configured(model)) throw new Rejected("NOT_CONFIGURED",null); }
    private void lock(String model) {
        configured(model);
        db.execute((org.springframework.jdbc.core.PreparedStatementCreator)connection->{var statement=connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,54))");statement.setString(1,key());return statement;},statement->statement.execute());
        long revision=count("select coalesce(max(config_revision),0) from gemini_quota_scope where project_key=?",key());
        if (settings.getConfigRevision()<revision) throw new Rejected("NOT_CONFIGURED",null);
        if (settings.getConfigRevision()>revision && revision>0) {
            var now=now();
            if (active(model,now)>0 || queued(model)>0 || count("select count(*) from gemini_request_permit where project_key=? and phase in ('RESERVED','DISPATCHED') and lease_until>?",key(),now)>0)
                throw new Rejected("NOT_CONFIGURED",null);
            db.update("update gemini_quota_scope set workers=?,queue_capacity=?,config_revision=? where project_key=?",settings.getWorkers(),settings.getQueueCapacity(),settings.getConfigRevision(),key());
            db.update("update gemini_quota_scope set requests_per_minute=?,tokens_per_minute=?,requests_per_day=?,queue_wait_seconds=? where project_key=? and model=?",
                    settings.getRequestsPerMinute(),settings.getTokensPerMinute(),settings.getRequestsPerDay(),settings.getQueueWaitSeconds(),key(),model);
        }
        db.update("insert into gemini_quota_scope(project_key,model,config_revision,workers,queue_capacity,requests_per_minute,tokens_per_minute,requests_per_day,queue_wait_seconds) values(?,?,?,?,?,?,?,?,?) on conflict do nothing",
                key(),model,settings.getConfigRevision(),settings.getWorkers(),settings.getQueueCapacity(),settings.getRequestsPerMinute(),settings.getTokensPerMinute(),settings.getRequestsPerDay(),settings.getQueueWaitSeconds());
        var config=db.queryForMap("select * from gemini_quota_scope where project_key=? and model=? for update",key(),model);
        if (count("select count(*) from gemini_quota_scope where project_key=? and (workers<>? or queue_capacity<>?)",key(),settings.getWorkers(),settings.getQueueCapacity())>0)
            throw new Rejected("NOT_CONFIGURED",null);
        if (number(config,"workers")!=settings.getWorkers() || number(config,"queue_capacity")!=settings.getQueueCapacity()
                || number(config,"requests_per_minute")!=settings.getRequestsPerMinute() || number(config,"tokens_per_minute")!=settings.getTokensPerMinute()
                || number(config,"requests_per_day")!=settings.getRequestsPerDay() || number(config,"queue_wait_seconds")!=settings.getQueueWaitSeconds())
            throw new Rejected("NOT_CONFIGURED",null);
    }
    private Budget budget(String model,long tokens,UUID excluding,OffsetDateTime now) {
        if (tokens<=0 || tokens>settings.getTokensPerMinute()) return new Budget("TOKEN_LIMIT",now.plusSeconds(60));
        var backoff=db.queryForObject("select retry_until from gemini_quota_scope where project_key=? and model=?",OffsetDateTime.class,key(),model);
        if (backoff!=null && backoff.isAfter(now)) return new Budget("PROVIDER_BACKOFF",backoff);
        var day=now.atZoneSameInstant(PROVIDER_DAY).toLocalDate().atStartOfDay(PROVIDER_DAY).toOffsetDateTime();
        var entries=db.queryForList("select phase,charged_tokens,dispatched_at,lease_until from gemini_request_permit where project_key=? and model=? and id<>? and ((phase='RESERVED' and lease_until>?) or dispatched_at>=?)",
                key(),model,excluding==null?new UUID(0,0):excluding,now,day.isBefore(now.minusSeconds(60))?day:now.minusSeconds(60));
        long rpm=0,tpm=0,rpd=0;OffsetDateTime minuteRetry=null;
        for (var row:entries) {
            boolean reserved="RESERVED".equals(row.get("phase"));var dispatched=date(row.get("dispatched_at"));
            if (reserved || dispatched!=null && !dispatched.isBefore(day)) rpd++;
            if (reserved || dispatched!=null && dispatched.isAfter(now.minusSeconds(60))) {
                rpm++;tpm+=number(row,"charged_tokens");
                var expiry=reserved?date(row.get("lease_until")):dispatched.plusSeconds(60);
                if (minuteRetry==null || expiry.isBefore(minuteRetry)) minuteRetry=expiry;
            }
        }
        if (rpd>=settings.getRequestsPerDay()) return new Budget("DAILY_LIMIT",day.atZoneSameInstant(PROVIDER_DAY).toLocalDate().plusDays(1).atStartOfDay(PROVIDER_DAY).toOffsetDateTime());
        if (rpm>=settings.getRequestsPerMinute() || tpm+tokens>settings.getTokensPerMinute()) {
            throttled.increment();return new Budget("MINUTE_LIMIT",minuteRetry==null?now.plusSeconds(1):minuteRetry);
        }
        return null;
    }
    private void recover(String model,OffsetDateTime now) {
        db.update("update gemini_request_permit set phase='RELEASED' where project_key=? and phase='RESERVED' and lease_until<=?",key(),now);
        db.update("update gemini_request_permit set phase='UNCERTAIN' where project_key=? and phase='DISPATCHED' and lease_until<=?",key(),now);
        for (var row:db.queryForList("select id from gemini_appraisal_work where project_key=? and ((phase='QUEUED' and expires_at<=?) or (phase in ('RESERVED','DISPATCHED') and lease_until<=?))",key(),now,now))
            cancel((UUID)row.get("id"),now);
        db.update("delete from gemini_request_permit where project_key=? and phase in ('FINISHED','RELEASED','UNCERTAIN') and reserved_at<?",key(),now.minusDays(7));
        db.update("delete from gemini_appraisal_work w where project_key=? and phase in ('FINISHED','CANCELLED') and enqueued_at<? and not exists(select 1 from gemini_request_permit p where p.work_id=w.id)",key(),now.minusDays(7));
    }
    private void cancel(UUID id,OffsetDateTime now) {
        var work=db.queryForMap("select kind from gemini_appraisal_work where id=?",id);
        db.update("update gemini_request_permit set phase='RELEASED' where work_id=? and phase='RESERVED'",id);
        db.update("update gemini_appraisal_work set phase='CANCELLED',lease_until=null where id=?",id);
        if ("EMPLOYEE".equals(work.get("kind")))
            db.update("update appraisal_employee_insight_generation set status='FAILED',error_code='INTERRUPTED',active_context=null,lease_until=null,completed_at=greatest(?,coalesce(started_at,created_at)),lock_version=lock_version+1 where id=? and status in ('QUEUED','RUNNING')",now,id);
        else db.update("update appraisal_insight_generation set status='FAILED',error_code='INTERRUPTED',active_key=null,completed_at=greatest(?,coalesce(started_at,created_at)),lock_version=lock_version+1 where id=? and status in ('QUEUED','RUNNING')",now,id);
    }
    private long active(String model,OffsetDateTime now) { return count("select count(*) from gemini_appraisal_work where project_key=? and phase in ('RESERVED','DISPATCHED') and lease_until>?",key(),now); }
    private long queued(String model) { return count("select count(*) from gemini_appraisal_work where project_key=? and phase='QUEUED'",key()); }
    private long count(String sql,Object... args) { return Objects.requireNonNull(db.queryForObject(sql,Long.class,args)); }
    private String key() { return settings.getProjectKey(); }
    private OffsetDateTime now() { return time.get(); }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static OffsetDateTime date(Object value) { return value==null?null:value instanceof OffsetDateTime d?d:((java.sql.Timestamp)value).toInstant().atOffset(ZoneOffset.UTC); }
    private AppraisalProviderException providerRefused(OffsetDateTime retry,OffsetDateTime now) {
        return new AppraisalProviderException("RATE_LIMITED",Long.toString(Math.max(1,Duration.between(now,retry==null?now.plusSeconds(60):retry).toSeconds())));
    }
}
