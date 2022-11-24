package com.hartwig.hmftools.peach;

import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GeneHaplotypePanel
{
    @NotNull
    private final WildTypeHaplotype wildTypeHaplotype;
    @NotNull
    private final List<NonWildTypeHaplotype> nonWildTypeHaplotypes;

    public GeneHaplotypePanel(
            @NotNull WildTypeHaplotype wildTypeHaplotype,
            @NotNull List<NonWildTypeHaplotype> nonWildTypeHaplotypes
    )
    {
        this.wildTypeHaplotype = wildTypeHaplotype;
        this.nonWildTypeHaplotypes = List.copyOf(nonWildTypeHaplotypes);
    }

    public Map<Chromosome, Set<Integer>> getRelevantVariantPositions()
    {
        Stream<VariantHaplotypeEvent> variantHaplotypeEvents = nonWildTypeHaplotypes.stream()
                .map(h -> h.events)
                .flatMap(Collection::stream)
                .filter(e -> e instanceof VariantHaplotypeEvent)
                .map(VariantHaplotypeEvent.class::cast);
        return variantHaplotypeEvents.collect(
                Collectors.groupingBy(
                        e -> e.chromosome,
                        Collectors.mapping(VariantHaplotypeEvent::getCoveredPositions, Collectors.flatMapping(Collection::stream, Collectors.toSet()))
                )
        );
    }

    public boolean isRelevantFor(HaplotypeEvent event)
    {
        return nonWildTypeHaplotypes.stream().anyMatch(h -> h.isRelevantFor(event));
    }
}
