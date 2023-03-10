package com.hartwig.hmftools.orange.report.chapters;

import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.genome.chromosome.GermlineAberration;
import com.hartwig.hmftools.common.peach.PeachGenotype;
import com.hartwig.hmftools.datamodel.linx.HomozygousDisruption;
import com.hartwig.hmftools.datamodel.linx.LinxBreakend;
import com.hartwig.hmftools.datamodel.linx.LinxSvAnnotation;
import com.hartwig.hmftools.datamodel.purple.PurpleGainLoss;
import com.hartwig.hmftools.orange.algo.OrangeReport;
import com.hartwig.hmftools.orange.algo.purple.PurpleVariant;
import com.hartwig.hmftools.orange.report.ReportResources;
import com.hartwig.hmftools.orange.report.datamodel.BreakendEntry;
import com.hartwig.hmftools.orange.report.datamodel.BreakendEntryFactory;
import com.hartwig.hmftools.orange.report.datamodel.VariantEntry;
import com.hartwig.hmftools.orange.report.datamodel.VariantEntryFactory;
import com.hartwig.hmftools.orange.report.interpretation.VariantDedup;
import com.hartwig.hmftools.orange.report.tables.*;
import com.hartwig.hmftools.orange.report.util.Cells;
import com.hartwig.hmftools.orange.report.util.Tables;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.UnitValue;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.*;

public class GermlineFindingsChapter implements ReportChapter {

    private static final DecimalFormat PERCENTAGE_FORMAT = ReportResources.decimalFormat("#.0'%'");

    @NotNull
    private final OrangeReport report;

    public GermlineFindingsChapter(@NotNull final OrangeReport report) {
        this.report = report;
    }

    @NotNull
    @Override
    public String name() {
        return "Germline Findings";
    }

    @NotNull
    @Override
    public PageSize pageSize() {
        return PageSize.A4;
    }

    @Override
    public void render(@NotNull final Document document) {
        document.add(new Paragraph(name()).addStyle(ReportResources.chapterTitleStyle()));

        if (report.refSample() != null) {
            addGermlineVariants(document);
            addGermlineDeletions(document);
            addGermlineHomozygousDisruptions(document);
            addGermlineBreakends(document);
            addMVLHAnalysis(document);
            addGermlineCNAberrations(document);
            addPharmacogenetics(document);
        } else {
            document.add(new Paragraph(ReportResources.NOT_AVAILABLE).addStyle(ReportResources.tableContentStyle()));
        }
    }

    private void addGermlineVariants(@NotNull Document document) {
        List<DriverCatalog> drivers = report.purple().germlineDrivers();

        List<PurpleVariant> reportableVariants = report.purple().reportableGermlineVariants();
        if (drivers != null && reportableVariants != null) {
            List<VariantEntry> reportableEntries = VariantEntryFactory.create(VariantDedup.apply(reportableVariants), drivers);
            String titleDrivers = "Driver variants (" + reportableEntries.size() + ")";
            document.add(GermlineVariantTable.build(titleDrivers, contentWidth(), reportableEntries));
        }

        List<PurpleVariant> additionalSuspectVariants = report.purple().additionalSuspectGermlineVariants();
        if (drivers != null && additionalSuspectVariants != null) {
            List<VariantEntry> additionalSuspectEntries =
                    VariantEntryFactory.create(VariantDedup.apply(additionalSuspectVariants), drivers);
            String titleNonDrivers = "Other potentially relevant variants (" + additionalSuspectEntries.size() + ")";
            document.add(GermlineVariantTable.build(titleNonDrivers, contentWidth(), additionalSuspectEntries));
        }
    }

    private void addGermlineDeletions(@NotNull Document document) {
        List<PurpleGainLoss> reportableGermlineGainsLosses = report.purple().reportableGermlineFullLosses();
        if (reportableGermlineGainsLosses != null) {
            String title = "Potentially pathogenic germline deletions (" + reportableGermlineGainsLosses.size() + ")";
            document.add(GainLossTable.build(title, contentWidth(), reportableGermlineGainsLosses, report.isofox()));
        }
    }

    private void addGermlineHomozygousDisruptions(@NotNull Document document) {
        List<HomozygousDisruption> germlineHomozygousDisruptions = report.linx().germlineHomozygousDisruptions();
        if (germlineHomozygousDisruptions != null) {
            String title = "Potentially pathogenic germline homozygous disruptions (" + germlineHomozygousDisruptions.size() + ")";
            document.add(HomozygousDisruptionTable.build(title, contentWidth(), germlineHomozygousDisruptions));
        }
    }

    private void addGermlineBreakends(@NotNull Document document) {
        List<LinxSvAnnotation> allGermlineStructuralVariants = report.linx().allGermlineStructuralVariants();
        List<LinxBreakend> reportableGermlineBreakends = report.linx().reportableGermlineBreakends();

        if (allGermlineStructuralVariants != null && reportableGermlineBreakends != null) {
            List<BreakendEntry> reportableBreakends =
                    BreakendEntryFactory.create(reportableGermlineBreakends, allGermlineStructuralVariants);

            String title = "Potentially pathogenic germline gene disruptions (" + reportableBreakends.size() + ")";
            document.add(BreakendTable.build(title, contentWidth(), reportableBreakends));
        }
    }

    private void addMVLHAnalysis(@NotNull Document document) {
        Map<String, Double> germlineMVLHPerGene = report.germlineMVLHPerGene();
        if (germlineMVLHPerGene != null) {
            Table table = Tables.createContent(contentWidth(),
                    new float[] { 2, 2, 1, 2, 2, 1, 2, 2, 1, 2, 2, 1 },
                    new Cell[] { Cells.createHeader("Gene"), Cells.createHeader("MVLH"), Cells.createHeader(Strings.EMPTY),
                            Cells.createHeader("Gene"), Cells.createHeader("MVLH"), Cells.createHeader(Strings.EMPTY),
                            Cells.createHeader("Gene"), Cells.createHeader("MVLH"), Cells.createHeader(Strings.EMPTY),
                            Cells.createHeader("Gene"), Cells.createHeader("MVLH"), Cells.createHeader(Strings.EMPTY) });

            int count = 0;
            Set<String> genes = Sets.newTreeSet(Comparator.naturalOrder());
            genes.addAll(germlineMVLHPerGene.keySet());
            for (String gene : genes) {
                double mvlh = germlineMVLHPerGene.get(gene);
                if (mvlh > 0.01) {
                    count++;
                    table.addCell(Cells.createContent(gene));
                    table.addCell(Cells.createContent(PERCENTAGE_FORMAT.format(mvlh * 100)));
                    table.addCell(Cells.createContent(Strings.EMPTY));
                }
            }

            // Make sure all rows are properly filled in case table is sparse.
            if (count % 4 != 0) {
                for (int i = 0; i < 12 - 3 * (count % 4); i++) {
                    table.addCell(Cells.createContent(Strings.EMPTY));
                }
            }

            String title = "Genes with missed variant likelihood > 1% (" + count + ")";
            if (count == 0) {
                document.add(Tables.createEmpty(title, contentWidth()));
            } else {
                document.add(Tables.createWrapping(table, title));
            }
        }
    }

    private void addGermlineCNAberrations(@NotNull Document document) {
        Set<GermlineAberration> germlineAberrations = report.purple().fit().qc().germlineAberrations();
        if (!germlineAberrations.isEmpty()) {
            int count = 0;
            StringJoiner germlineAberrationJoiner = new StringJoiner(", ");
            for (GermlineAberration germlineAberration : germlineAberrations) {
                if (germlineAberration != GermlineAberration.NONE) {
                    count++;
                }
                germlineAberrationJoiner.add(germlineAberration.toString());
            }
            Table table = new Table(UnitValue.createPercentArray(new float[] { 1 })).setWidth(contentWidth());
            table.addCell(Cells.createContent(germlineAberrationJoiner.toString()));
            document.add(Tables.createWrapping(table, "Germline CN aberrations (" + count + ")"));
        }
    }

    private void addPharmacogenetics(@NotNull Document document) {
        var peach = report.peach();
        if (peach != null) {
            String titlePharmacogenetics = "Pharmacogenetics (" + peach.entries().size() + ")";
            document.add(PharmacogeneticsTable.build(titlePharmacogenetics, contentWidth(), peach.entries()));
        }
    }
}
