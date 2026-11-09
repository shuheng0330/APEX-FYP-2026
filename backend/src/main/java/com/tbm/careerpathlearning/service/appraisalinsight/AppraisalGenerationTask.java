package com.tbm.careerpathlearning.service.appraisalinsight;

import java.util.UUID;
import java.util.function.Consumer;

/** Only scheduling metadata is shared; the operation and its evidence remain local/server-owned. */
public record AppraisalGenerationTask(UUID id,String kind,String model,long tokenBound,
        Runnable operation,Consumer<String> cancelled) implements Runnable {
    @Override public void run() { operation.run(); }
}
