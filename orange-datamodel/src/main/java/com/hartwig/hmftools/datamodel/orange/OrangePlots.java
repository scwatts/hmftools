package com.hartwig.hmftools.datamodel.orange;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class OrangePlots {

    @NotNull
    public abstract String purpleFinalCircosPlot();
}
