package com.hartwig.hmftools.orange.algo.purple;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.chord.ChordData;
import com.hartwig.hmftools.common.drivercatalog.AmplificationDrivers;
import com.hartwig.hmftools.common.drivercatalog.DeletionDrivers;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalogKey;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalogMap;
import com.hartwig.hmftools.common.drivercatalog.DriverCategory;
import com.hartwig.hmftools.common.drivercatalog.DriverType;
import com.hartwig.hmftools.common.drivercatalog.panel.DriverGene;
import com.hartwig.hmftools.common.drivercatalog.panel.DriverGeneGermlineReporting;
import com.hartwig.hmftools.common.drivercatalog.panel.DriverGenePanel;
import com.hartwig.hmftools.common.drivercatalog.panel.ImmutableDriverGene;
import com.hartwig.hmftools.common.drivercatalog.panel.ImmutableDriverGenePanel;
import com.hartwig.hmftools.common.linx.LinxBreakend;
import com.hartwig.hmftools.common.linx.LinxSvAnnotation;
import com.hartwig.hmftools.common.purple.FittedPurityMethod;
import com.hartwig.hmftools.common.purple.GeneCopyNumber;
import com.hartwig.hmftools.common.purple.GermlineDeletion;
import com.hartwig.hmftools.common.purple.GermlineDetectionMethod;
import com.hartwig.hmftools.common.purple.GermlineStatus;
import com.hartwig.hmftools.common.purple.PurpleData;
import com.hartwig.hmftools.common.purple.PurpleQCStatus;
import com.hartwig.hmftools.common.sv.StructuralVariantType;
import com.hartwig.hmftools.orange.algo.linx.BreakendUtil;
import com.hartwig.hmftools.orange.algo.linx.LinxInterpretedData;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PurpleInterpreter {

    private static final Logger LOGGER = LogManager.getLogger(PurpleInterpreter.class);

    private static final int MAX_LENGTH_FOR_IMPLIED_DELS = 1500;

    @NotNull
    private final PurpleVariantFactory purpleVariantFactory;
    @NotNull
    private final GermlineGainLossFactory germlineGainLossFactory;
    @NotNull
    private final List<DriverGene> driverGenes;
    @NotNull
    private final LinxInterpretedData linx;
    @Nullable
    private final ChordData chord;

    public PurpleInterpreter(@NotNull final PurpleVariantFactory purpleVariantFactory,
            @NotNull final GermlineGainLossFactory germlineGainLossFactory, @NotNull final List<DriverGene> driverGenes,
            @NotNull final LinxInterpretedData linx, @Nullable final ChordData chord) {
        this.purpleVariantFactory = purpleVariantFactory;
        this.germlineGainLossFactory = germlineGainLossFactory;
        this.driverGenes = driverGenes;
        this.linx = linx;
        this.chord = chord;
    }

    @NotNull
    public PurpleInterpretedData interpret(@NotNull PurpleData purple) {
        LOGGER.info("Analysing purple data");
        List<PurpleVariant> allSomaticVariants = purpleVariantFactory.create(purple.allSomaticVariants());
        List<PurpleVariant> reportableSomaticVariants = purpleVariantFactory.create(purple.reportableSomaticVariants());
        List<PurpleVariant> additionalSuspectSomaticVariants =
                SomaticVariantSelector.selectInterestingUnreportedVariants(allSomaticVariants, reportableSomaticVariants, driverGenes);
        LOGGER.info(" Found an additional {} somatic variants that are potentially interesting", additionalSuspectSomaticVariants.size());

        List<PurpleVariant> allGermlineVariants = purpleVariantFactory.create(purple.allGermlineVariants());
        List<PurpleVariant> reportableGermlineVariants = purpleVariantFactory.create(purple.reportableGermlineVariants());
        List<PurpleVariant> additionalSuspectGermlineVariants =
                GermlineVariantSelector.selectInterestingUnreportedVariants(allGermlineVariants);
        if (additionalSuspectGermlineVariants != null) {
            LOGGER.info(" Found an additional {} germline variants that are potentially interesting",
                    additionalSuspectGermlineVariants.size());
        }

        List<PurpleGainLoss> allSomaticGainsLosses = extractAllGainsLosses(purple.purityContext().qc().status(),
                purple.purityContext().bestFit().ploidy(),
                purple.purityContext().targeted(),
                purple.allSomaticGeneCopyNumbers());
        List<PurpleGainLoss> reportableSomaticGainsLosses = somaticGainsLossesFromDrivers(purple.somaticDrivers());

        List<PurpleGainLoss> nearReportableSomaticGains =
                CopyNumberSelector.selectNearReportableSomaticGains(purple.allSomaticGeneCopyNumbers(),
                        purple.purityContext().bestFit().ploidy(),
                        allSomaticGainsLosses,
                        driverGenes);
        LOGGER.info(" Found an additional {} near-reportable somatic gains that are potentially interesting",
                nearReportableSomaticGains.size());

        List<PurpleGainLoss> additionalSuspectSomaticGainsLosses =
                CopyNumberSelector.selectInterestingUnreportedGainsLosses(allSomaticGainsLosses, reportableSomaticGainsLosses);
        LOGGER.info(" Found an additional {} somatic gains/losses that are potentially interesting",
                additionalSuspectSomaticGainsLosses.size());

        List<GeneCopyNumber> suspectGeneCopyNumbersWithLOH =
                LossOfHeterozygositySelector.selectHRDOrMSIGenesWithLOH(purple.allSomaticGeneCopyNumbers(),
                        purple.purityContext().microsatelliteStatus(),
                        chord != null ? chord.hrStatus() : null);
        LOGGER.info(" Found an additional {} suspect gene copy numbers with LOH", suspectGeneCopyNumbersWithLOH.size());

        List<PurpleGainLoss> allGermlineGainsLosses = null;
        List<PurpleGainLoss> reportableGermlineGainsLosses = null;
        List<GermlineDeletion> allGermlineDeletions = purple.allGermlineDeletions();

        if (allGermlineDeletions != null) {
            List<GermlineDeletion> impliedDeletions = implyDeletionsFromBreakends(allGermlineDeletions,
                    linx.reportableGermlineBreakends(),
                    linx.allGermlineStructuralVariants());
            LOGGER.info(" Implied {} additional reportable germline deletions from breakends", impliedDeletions.size());

            List<GermlineDeletion> mergedGermlineDeletions = Lists.newArrayList();
            mergedGermlineDeletions.addAll(allGermlineDeletions);
            mergedGermlineDeletions.addAll(impliedDeletions);

            Map<PurpleGainLoss, GermlineDeletion> deletionMap =
                    germlineGainLossFactory.mapDeletions(mergedGermlineDeletions, purple.allSomaticGeneCopyNumbers());

            allGermlineGainsLosses = Lists.newArrayList(deletionMap.keySet());
            reportableGermlineGainsLosses = selectReportable(deletionMap);

            LOGGER.info(" Resolved {} germline losses of which {} are reportable",
                    allGermlineGainsLosses.size(),
                    reportableGermlineGainsLosses.size());
        }

        return ImmutablePurpleInterpretedData.builder()
                .fit(createFit(purple))
                .characteristics(createCharacteristics(purple))
                .somaticDrivers(purple.somaticDrivers())
                .germlineDrivers(purple.germlineDrivers())
                .allSomaticVariants(allSomaticVariants)
                .reportableSomaticVariants(reportableSomaticVariants)
                .additionalSuspectSomaticVariants(additionalSuspectSomaticVariants)
                .allGermlineVariants(allGermlineVariants)
                .reportableGermlineVariants(reportableGermlineVariants)
                .additionalSuspectGermlineVariants(additionalSuspectGermlineVariants)
                .allSomaticCopyNumbers(purple.allSomaticCopyNumbers())
                .allSomaticGeneCopyNumbers(purple.allSomaticGeneCopyNumbers())
                .suspectGeneCopyNumbersWithLOH(suspectGeneCopyNumbersWithLOH)
                .allSomaticGainsLosses(allSomaticGainsLosses)
                .reportableSomaticGainsLosses(reportableSomaticGainsLosses)
                .nearReportableSomaticGains(nearReportableSomaticGains)
                .additionalSuspectSomaticGainsLosses(additionalSuspectSomaticGainsLosses)
                .allGermlineGainsLosses(allGermlineGainsLosses)
                .reportableGermlineGainsLosses(reportableGermlineGainsLosses)
                .build();
    }

    @NotNull
    private static List<GermlineDeletion> implyDeletionsFromBreakends(@NotNull List<GermlineDeletion> allGermlineDeletions,
            @Nullable List<LinxBreakend> reportableGermlineBreakends, @Nullable List<LinxSvAnnotation> allGermlineStructuralVariants) {
        if (reportableGermlineBreakends == null || allGermlineStructuralVariants == null) {
            LOGGER.warn("Linx germline data is missing while purple germline data is present!");
            return Lists.newArrayList();
        }

        List<GermlineDeletion> impliedDeletions = Lists.newArrayList();
        for (Pair<LinxBreakend, LinxBreakend> breakendPair : BreakendUtil.createPairsPerSvId(reportableGermlineBreakends)) {
            LinxBreakend first = breakendPair.getLeft();
            LinxBreakend second = breakendPair.getRight();

            boolean bothReported = first.reportedDisruption() && second.reportedDisruption();
            boolean bothDel = first.type() == StructuralVariantType.DEL && second.type() == StructuralVariantType.DEL;
            boolean sameGene = first.gene().equals(second.gene());
            boolean sameTranscript = first.transcriptId().equals(second.transcriptId());
            boolean noWildTypeRemaining = first.undisruptedCopyNumber() < 0.5 && second.undisruptedCopyNumber() < 0.5;

            LinxSvAnnotation sv = findBySvId(allGermlineStructuralVariants, first.svId());
            // TODO Evaluate that SV is shorter than MAX_LENGTH_FOR_IMPLIED_DELS
            boolean meetsMaxLength = false;

            // TODO Check if there is a delete already on the positions implied by the DEL sv.
            if (bothReported && bothDel && sameGene && sameTranscript && noWildTypeRemaining && meetsMaxLength) {
                impliedDeletions.add(new GermlineDeletion(first.gene(),
                        first.chromosome(),
                        first.chrBand(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        GermlineDetectionMethod.SEGMENT,
                        GermlineStatus.HET_DELETION,
                        GermlineStatus.HOM_DELETION,
                        1D,
                        0D,
                        Strings.EMPTY,
                        0,
                        true));
            }
        }
        return impliedDeletions;
    }

    @Nullable
    private static LinxSvAnnotation findBySvId(@NotNull List<LinxSvAnnotation> allGermlineStructuralVariants, int svIdToFind) {
        for (LinxSvAnnotation structuralVariant : allGermlineStructuralVariants) {
            if (structuralVariant.svId() == svIdToFind) {
                return structuralVariant;
            }
        }

        LOGGER.warn("Could not find germline structural variant with svId: {}", svIdToFind);
        return null;
    }

    @NotNull
    private static List<PurpleGainLoss> selectReportable(@NotNull Map<PurpleGainLoss, GermlineDeletion> deletionMap) {
        List<PurpleGainLoss> reportable = Lists.newArrayList();
        for (Map.Entry<PurpleGainLoss, GermlineDeletion> entry : deletionMap.entrySet()) {
            PurpleGainLoss gainLoss = entry.getKey();
            GermlineDeletion deletion = entry.getValue();
            if (deletion.Reported) {
                reportable.add(gainLoss);
            }
        }
        return reportable;
    }

    @NotNull
    private static List<PurpleGainLoss> extractAllGainsLosses(@NotNull Set<PurpleQCStatus> qcStatus, double ploidy, boolean isTargetRegions,
            @NotNull List<GeneCopyNumber> allGeneCopyNumbers) {
        List<DriverGene> allGenes = Lists.newArrayList();
        for (GeneCopyNumber geneCopyNumber : allGeneCopyNumbers) {
            allGenes.add(ImmutableDriverGene.builder()
                    .gene(geneCopyNumber.geneName())
                    .reportMissenseAndInframe(false)
                    .reportNonsenseAndFrameshift(false)
                    .reportSplice(false)
                    .reportDeletion(true)
                    .reportDisruption(false)
                    .reportAmplification(true)
                    .reportSomaticHotspot(false)
                    .reportGermlineVariant(DriverGeneGermlineReporting.NONE)
                    .reportGermlineHotspot(DriverGeneGermlineReporting.NONE)
                    .reportGermlineDisruption(DriverGeneGermlineReporting.NONE)
                    .likelihoodType(DriverCategory.ONCO)
                    .reportPGX(false)
                    .build());
        }

        DriverGenePanel allGenesPanel = ImmutableDriverGenePanel.builder().driverGenes(allGenes).build();
        AmplificationDrivers ampDrivers = new AmplificationDrivers(qcStatus, allGenesPanel);
        DeletionDrivers delDrivers = new DeletionDrivers(qcStatus, allGenesPanel);

        List<DriverCatalog> allGainLosses = Lists.newArrayList();
        allGainLosses.addAll(ampDrivers.amplifications(ploidy, allGeneCopyNumbers, isTargetRegions));
        allGainLosses.addAll(delDrivers.deletions(allGeneCopyNumbers, isTargetRegions));

        return somaticGainsLossesFromDrivers(allGainLosses);
    }

    @NotNull
    private static List<PurpleGainLoss> somaticGainsLossesFromDrivers(@NotNull List<DriverCatalog> drivers) {
        List<PurpleGainLoss> gainsLosses = Lists.newArrayList();

        Map<DriverCatalogKey, DriverCatalog> geneDriverMap = DriverCatalogMap.toDriverMap(drivers);
        for (DriverCatalogKey key : geneDriverMap.keySet()) {
            DriverCatalog geneDriver = geneDriverMap.get(key);

            if (geneDriver.driver() == DriverType.AMP || geneDriver.driver() == DriverType.PARTIAL_AMP
                    || geneDriver.driver() == DriverType.DEL) {
                gainsLosses.add(toGainLoss(geneDriver));
            }
        }
        return gainsLosses;
    }

    @NotNull
    private static PurpleGainLoss toGainLoss(@NotNull DriverCatalog driver) {
        return ImmutablePurpleGainLoss.builder()
                .chromosome(driver.chromosome())
                .chromosomeBand(driver.chromosomeBand())
                .gene(driver.gene())
                .transcript(driver.transcript())
                .isCanonical(driver.isCanonical())
                .interpretation(CopyNumberInterpretation.fromCNADriver(driver))
                .minCopies(Math.round(Math.max(0, driver.minCopyNumber())))
                .maxCopies(Math.round(Math.max(0, driver.maxCopyNumber())))
                .build();
    }

    @NotNull
    private static PurityPloidyFit createFit(@NotNull PurpleData purple) {
        return ImmutablePurityPloidyFit.builder()
                .qc(purple.purityContext().qc())
                .hasSufficientQuality(purple.purityContext().qc().pass())
                .fittedPurityMethod(purple.purityContext().method())
                .containsTumorCells(purple.purityContext().method() != FittedPurityMethod.NO_TUMOR)
                .purity(purple.purityContext().bestFit().purity())
                .minPurity(purple.purityContext().score().minPurity())
                .maxPurity(purple.purityContext().score().maxPurity())
                .ploidy(purple.purityContext().bestFit().ploidy())
                .minPloidy(purple.purityContext().score().minPloidy())
                .maxPloidy(purple.purityContext().score().maxPloidy())
                .build();
    }

    @NotNull
    private static PurpleCharacteristics createCharacteristics(@NotNull PurpleData purple) {
        return ImmutablePurpleCharacteristics.builder()
                .wholeGenomeDuplication(purple.purityContext().wholeGenomeDuplication())
                .microsatelliteIndelsPerMb(purple.purityContext().microsatelliteIndelsPerMb())
                .microsatelliteStatus(purple.purityContext().microsatelliteStatus())
                .tumorMutationalBurdenPerMb(purple.purityContext().tumorMutationalBurdenPerMb())
                .tumorMutationalBurdenStatus(purple.purityContext().tumorMutationalBurdenStatus())
                .tumorMutationalLoad(purple.purityContext().tumorMutationalLoad())
                .tumorMutationalLoadStatus(purple.purityContext().tumorMutationalLoadStatus())
                .svTumorMutationalBurden(purple.purityContext().svTumorMutationalBurden())
                .build();
    }
}
