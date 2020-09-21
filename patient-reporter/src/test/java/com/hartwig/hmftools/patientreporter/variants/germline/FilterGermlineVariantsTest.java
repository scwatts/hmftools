package com.hartwig.hmftools.patientreporter.variants.germline;

import static com.hartwig.hmftools.patientreporter.PatientReporterTestFactory.ONCOGENE;
import static com.hartwig.hmftools.patientreporter.PatientReporterTestFactory.TSG;
import static com.hartwig.hmftools.patientreporter.PatientReporterTestFactory.createTestCopyNumberBuilder;
import static com.hartwig.hmftools.patientreporter.PatientReporterTestFactory.createTestGermlineGenesReporting;
import static com.hartwig.hmftools.patientreporter.PatientReporterTestFactory.createTestGermlineVariantBuilder;
import static com.hartwig.hmftools.patientreporter.PatientReporterTestFactory.createTestSomaticVariantBuilder;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.chord.ChordStatus;
import com.hartwig.hmftools.common.purple.gene.GeneCopyNumber;
import com.hartwig.hmftools.common.variant.germline.ReportableGermlineVariant;
import com.hartwig.hmftools.patientreporter.variants.somatic.DriverSomaticVariant;
import com.hartwig.hmftools.patientreporter.variants.somatic.ImmutableDriverSomaticVariant;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class FilterGermlineVariantsTest {

    @Test
    public void checkForGermlineGenesReportedONCO() {
        GermlineReportingModel germlineReportingModel = createTestGermlineGenesReporting();

        List<GeneCopyNumber> geneCopyNumbers = Lists.newArrayList();
        List<DriverSomaticVariant> somaticVariants = Lists.newArrayList();

        List<ReportableGermlineVariant> germlineVariantsNotPresentInTumor = createTestGermlineVariantsONCOGeneNotPresentInTumor();
        List<DriverGermlineVariant> filteredGermlineVariantMatchNotPresentInTumor =
                FilterGermlineVariants.filterGermlineVariantsForReporting(germlineVariantsNotPresentInTumor,
                        germlineReportingModel,
                        geneCopyNumbers,
                        somaticVariants,
                        ChordStatus.HRP);
        assertEquals(0, filteredGermlineVariantMatchNotPresentInTumor.size());

        List<ReportableGermlineVariant> germlineVariantsPresentInTumor = createTestGermlineVariantsONCOGenePresentInTumor();
        List<DriverGermlineVariant> filteredGermlineVariantMatchPresentInTumor = FilterGermlineVariants.filterGermlineVariantsForReporting(
                germlineVariantsPresentInTumor,
                germlineReportingModel,
                geneCopyNumbers,
                somaticVariants,
                ChordStatus.HRP);
        assertEquals(1, filteredGermlineVariantMatchPresentInTumor.size());
    }

    @Test
    public void checkForGermlineGenesReportedTSG() {
        GermlineReportingModel germlineReportingModel = createTestGermlineGenesReporting();

        List<ReportableGermlineVariant> germlineVariantsMatch = createTestGermlineVariantsTSGGene(true, 1);
        List<ReportableGermlineVariant> germlineVariantsNonMatch = createTestGermlineVariantsTSGGene(false, 2);

        List<GeneCopyNumber> geneCopyNumbersMatch = createCopyNumberListForTSG(1);
        List<GeneCopyNumber> geneCopyNumbersNonMatch = createCopyNumberListForTSG(2);

        List<DriverSomaticVariant> variantsMatch = createSomaticVariantListForGene(TSG);
        List<DriverSomaticVariant> variantsNonMatch = createSomaticVariantListForGene("AAAA");

        List<DriverGermlineVariant> filteredGermlineVariantAllMatch = FilterGermlineVariants.filterGermlineVariantsForReporting(
                germlineVariantsMatch,
                germlineReportingModel,
                geneCopyNumbersMatch,
                variantsMatch,
                ChordStatus.HRP);
        assertEquals(1, filteredGermlineVariantAllMatch.size()); // all three options matched

        List<DriverGermlineVariant> filteredGermlineVariantNonMatchBiallelic = FilterGermlineVariants.filterGermlineVariantsForReporting(
                germlineVariantsNonMatch,
                germlineReportingModel,
                geneCopyNumbersMatch,
                variantsMatch,
                ChordStatus.HRP);
        assertEquals(1, filteredGermlineVariantNonMatchBiallelic.size()); // match copy number and variant

        List<DriverGermlineVariant> filteredGermlineVariantNonMatchVariant = FilterGermlineVariants.filterGermlineVariantsForReporting(
                germlineVariantsMatch,
                germlineReportingModel,
                geneCopyNumbersMatch,
                variantsNonMatch,
                ChordStatus.HRP);
        assertEquals(1, filteredGermlineVariantNonMatchVariant.size()); // match biallelic and copy number

        List<DriverGermlineVariant> filteredGermlineVariantNonMatchCopy = FilterGermlineVariants.filterGermlineVariantsForReporting(
                germlineVariantsMatch,
                germlineReportingModel,
                geneCopyNumbersNonMatch,
                variantsMatch,
                ChordStatus.HRP);
        assertEquals(1, filteredGermlineVariantNonMatchCopy.size()); // match biallelic and variant

        List<DriverGermlineVariant> filteredGermlineVariantNonMatch = FilterGermlineVariants.filterGermlineVariantsForReporting(
                germlineVariantsNonMatch,
                germlineReportingModel,
                geneCopyNumbersNonMatch,
                variantsNonMatch,
                ChordStatus.HRP);
        assertEquals(0, filteredGermlineVariantNonMatch.size()); // all option failed

        List<DriverGermlineVariant> filteredGermlineVariantOptionBiallelic = FilterGermlineVariants.filterGermlineVariantsForReporting(
                germlineVariantsMatch,
                germlineReportingModel,
                geneCopyNumbersNonMatch,
                variantsNonMatch,
                ChordStatus.HRP);
        assertEquals(1, filteredGermlineVariantOptionBiallelic.size()); // only match biallelic

        List<DriverGermlineVariant> filteredGermlineVariantOptionVariant = FilterGermlineVariants.filterGermlineVariantsForReporting(
                germlineVariantsNonMatch,
                germlineReportingModel,
                geneCopyNumbersNonMatch,
                variantsMatch,
                ChordStatus.HRP);
        assertEquals(1, filteredGermlineVariantOptionVariant.size()); // only match variant

        List<DriverGermlineVariant> filteredGermlineVariantOptionCopyNumberPartialLoss =
                FilterGermlineVariants.filterGermlineVariantsForReporting(germlineVariantsNonMatch,
                        germlineReportingModel,
                        geneCopyNumbersMatch,
                        variantsNonMatch,
                        ChordStatus.HRP);
        assertEquals(1, filteredGermlineVariantOptionCopyNumberPartialLoss.size()); // only match copy number

        List<DriverGermlineVariant> filteredGermlineVariantOptionCopyNumberHRD = FilterGermlineVariants.filterGermlineVariantsForReporting(
                germlineVariantsNonMatch,
                germlineReportingModel,
                geneCopyNumbersNonMatch,
                variantsNonMatch,
                ChordStatus.HRD);
        assertEquals(1, filteredGermlineVariantOptionCopyNumberHRD.size()); // only match HRD
    }

    @NotNull
    private static List<ReportableGermlineVariant> createTestGermlineVariantsONCOGeneNotPresentInTumor() {
        return Lists.newArrayList(createTestGermlineVariantBuilder().gene(ONCOGENE).adjustedVaf(0.1).adjustedCopyNumber(1D).build());
    }

    @NotNull
    private static List<ReportableGermlineVariant> createTestGermlineVariantsONCOGenePresentInTumor() {
        return Lists.newArrayList(createTestGermlineVariantBuilder().gene(ONCOGENE).adjustedVaf(0.6).adjustedCopyNumber(1D).build());
    }

    @NotNull
    private static List<ReportableGermlineVariant> createTestGermlineVariantsTSGGene(boolean biallelic, double adjustedCopyNumber) {
        return Lists.newArrayList(createTestGermlineVariantBuilder().gene(TSG)
                .biallelic(biallelic)
                .adjustedCopyNumber(adjustedCopyNumber)
                .adjustedVaf(0.6)
                .build());
    }

    @NotNull
    private static List<GeneCopyNumber> createCopyNumberListForTSG(int minCopyNumber) {
        return Lists.newArrayList(createTestCopyNumberBuilder().gene(TSG).minCopyNumber(minCopyNumber).build());
    }

    @NotNull
    private static List<DriverSomaticVariant> createSomaticVariantListForGene(@NotNull String gene) {
        return Lists.newArrayList(ImmutableDriverSomaticVariant.builder()
                .variant(createTestSomaticVariantBuilder().gene(gene).build())
                .driverLikelihood(0D)
                .build());
    }
}