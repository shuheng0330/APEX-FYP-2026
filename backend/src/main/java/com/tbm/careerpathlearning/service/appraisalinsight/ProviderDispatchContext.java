package com.tbm.careerpathlearning.service.appraisalinsight;

/** A worker carries a reserved permit, never a prompt, across its synchronous provider call. */
public final class ProviderDispatchContext implements AutoCloseable {
    private static final ThreadLocal<ProviderDispatchContext> CURRENT=new ThreadLocal<>();
    private final ProviderDispatchContext previous;
    private final GeminiDispatchGate.Permit permit;
    private Runnable beforeDispatch=()->{};
    private boolean used;
    private ProviderDispatchContext(GeminiDispatchGate.Permit permit) {
        this.permit=permit;previous=CURRENT.get();CURRENT.set(this);
    }
    public static ProviderDispatchContext enter(GeminiDispatchGate.Permit permit) { return new ProviderDispatchContext(permit); }
    public static ProviderDispatchContext current() { return CURRENT.get(); }
    public GeminiDispatchGate.Permit permit() { return permit; }
    public void beforeDispatch(Runnable callback) { beforeDispatch=callback; }
    public void dispatched() {
        if (used) throw new IllegalStateException("An appraisal provider permit cannot be reused");
        beforeDispatch.run();used=true;
    }
    @Override public void close() { if(previous==null) CURRENT.remove();else CURRENT.set(previous); }
}
