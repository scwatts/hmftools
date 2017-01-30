package com.hartwig.hmftools.patientreporter.report;

import static net.sf.dynamicreports.report.builder.DynamicReports.cmp;
import static net.sf.dynamicreports.report.builder.DynamicReports.col;
import static net.sf.dynamicreports.report.builder.DynamicReports.hyperLink;
import static net.sf.dynamicreports.report.builder.DynamicReports.report;
import static net.sf.dynamicreports.report.builder.DynamicReports.stl;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Collection;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.patientreporter.PatientReport;
import com.hartwig.hmftools.patientreporter.slicing.GenomeRegion;
import com.hartwig.hmftools.patientreporter.slicing.HMFSlicingAnnotation;
import com.hartwig.hmftools.patientreporter.slicing.Slicer;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import net.sf.dynamicreports.jasper.builder.JasperReportBuilder;
import net.sf.dynamicreports.report.builder.column.TextColumnBuilder;
import net.sf.dynamicreports.report.builder.component.ComponentBuilder;
import net.sf.dynamicreports.report.builder.component.HorizontalListBuilder;
import net.sf.dynamicreports.report.builder.component.VerticalListBuilder;
import net.sf.dynamicreports.report.builder.style.StyleBuilder;
import net.sf.dynamicreports.report.constant.HorizontalTextAlignment;
import net.sf.dynamicreports.report.constant.VerticalTextAlignment;
import net.sf.dynamicreports.report.exception.DRException;

public class PDFWriter {

    private static final String FONT = "Times New Roman";

    private static final int TEXT_HEADER_INDENT = 30;
    private static final int TEXT_DETAIL_INDENT = 40;
    private static final int LIST_INDENT = 5;
    private static final int HEADER_TO_DETAIL_VERTICAL_GAP = 8;
    private static final int DETAIL_TO_DETAIL_VERTICAL_GAP = 4;
    private static final int SECTION_VERTICAL_GAP = 25;

    @NotNull
    private final String outputDirectory;
    @NotNull
    private final String hmfLogo;
    @NotNull
    private final Slicer hmfSlicingRegion;

    public PDFWriter(@NotNull final String outputDirectory, @NotNull final String hmfLogo,
            @NotNull final Slicer hmfSlicingRegion) {
        this.outputDirectory = outputDirectory;
        this.hmfLogo = hmfLogo;
        this.hmfSlicingRegion = hmfSlicingRegion;
    }

    @NotNull
    public String write(@NotNull final PatientReport report) throws FileNotFoundException, DRException {
        final String fileName = outputDirectory + File.separator + report.sample() + "_hmf_report.pdf";
        final JasperReportBuilder jasperReportBuilder = generatePatientReport(report, hmfLogo, hmfSlicingRegion);

        jasperReportBuilder.toPdf(new FileOutputStream(fileName));

        return fileName;
    }

    @VisibleForTesting
    @NotNull
    static JasperReportBuilder generatePatientReport(@NotNull final PatientReport report,
            @NotNull final String hmfLogoPath, @NotNull final Slicer hmfSlicingRegion) {
        // @formatter:off
        final ComponentBuilder<?, ?> reportMainPage =
                cmp.verticalList(
                        mainPageTopSection(report, hmfLogoPath),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        mainPageAboutSection(),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        variantReport(report),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        copyNumberReport(report));

        final ComponentBuilder<?, ?> helpPage =
                cmp.verticalList(
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        cmp.text("HMF Sequencing Report - Additional Information").setStyle(sectionHeaderStyle()),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        geneSection(hmfSlicingRegion),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        variantFieldExplanationSection(),
                        cmp.verticalGap(SECTION_VERTICAL_GAP),
                        copyNumberExplanationSection()
                );

        final ComponentBuilder<?, ?> totalReport =
                cmp.verticalList(
                        reportMainPage,
                        cmp.pageBreak(),
                        helpPage
                );
        // @formatter:on

        return report().noData(totalReport);
    }

    @NotNull
    private static ComponentBuilder<?, ?> mainPageTopSection(@NotNull final PatientReport report,
            @NotNull final String hmfLogoPath) {
        // @formatter:off
        final ComponentBuilder<?, ?> mainDiagnosisInfo = cmp.horizontalList(
                cmp.verticalList(
                        cmp.text("Report Date").setStyle(tableHeaderStyle()),
                        cmp.currentDate().setPattern("dd-MMM-yyyy").setStyle(dataTableStyle())),
                cmp.verticalList(
                        cmp.text("Tumor Type").setStyle(tableHeaderStyle()),
                        cmp.text(report.tumorType()).setStyle(dataTableStyle()))
        );

        return cmp.horizontalList(
                cmp.image(hmfLogoPath),
                cmp.verticalList(
                        cmp.text("HMF Sequencing Report - " + report.sample()).
                                setStyle(fontStyle().bold().setFontSize(14)
                                        .setVerticalTextAlignment(VerticalTextAlignment.MIDDLE))
                                .setHorizontalTextAlignment(HorizontalTextAlignment.CENTER)
                                .setHeight(50),
                        mainDiagnosisInfo)
        );
        // @formatter:on
    }

    @NotNull
    private static ComponentBuilder<?, ?> mainPageAboutSection() {
        return toList("About this report", Lists.newArrayList(
                "This test is performed for research purpose and is not meant to be used for "
                        + "clinical decision making without further validation of findings.",
                "Additional information on the various fields can be found on the final page of this report.",
                "For additional questions, please contact us via info@hartwigmedicalfoundation.nl."));
    }

    @NotNull
    private static ComponentBuilder<?, ?> variantReport(@NotNull final PatientReport report) {
        // @formatter:off
        final ComponentBuilder<?, ?> table = report.variants().size() > 0 ?
                cmp.subreport(baseTable().fields(PatientDataSource.variantFields())
                        .columns(
                            col.column("Gene", PatientDataSource.GENE_FIELD),
                            col.column("Position", PatientDataSource.POSITION_FIELD),
                            col.column("Variant", PatientDataSource.VARIANT_FIELD),
                            transcriptColumn(),
                            col.componentColumn("Effect", effectColumn()),
                            col.column("Cosmic", PatientDataSource.COSMIC_FIELD)
                                    .setHyperLink(hyperLink(new COSMICLinkExpression())).setStyle(linkStyle()),
                            col.column("VAF", PatientDataSource.ALLELE_FREQUENCY_FIELD)))
                        .setDataSource(PatientDataSource.fromVariants(report.variants())) :
                cmp.text("None").setStyle(fontStyle().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER));

        return cmp.verticalList(
                cmp.text("Somatic Variants").setStyle(sectionHeaderStyle()),
                cmp.verticalGap(6),
                table,
                cmp.verticalGap(15),
                cmp.text("Mutational Load: " + Integer.toString(report.mutationalLoad())).setStyle(tableHeaderStyle())
        );
        // @formatter:on
    }

    @NotNull
    private static ComponentBuilder<?, ?> copyNumberReport(@NotNull final PatientReport report) {
        // @formatter:off
        final ComponentBuilder<?, ?> table = report.copyNumbers().size() > 0 ?
                cmp.subreport(baseTable().fields(PatientDataSource.copyNumberFields())
                        .columns(
                            col.column("Gene", PatientDataSource.GENE_FIELD),
                            transcriptColumn(),
                            col.column("Type", PatientDataSource.COPY_NUMBER_TYPE_FIELD),
                            col.column("Copies", PatientDataSource.COPY_NUMBER_FIELD))
                        .setDataSource(PatientDataSource.fromCopyNumbers(report.copyNumbers()))) :
                cmp.text("None").setStyle(fontStyle().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER));

        return cmp.verticalList(
                cmp.text("Somatic Copy Numbers").setStyle(sectionHeaderStyle()),
                cmp.verticalGap(6),
                table);
        // @formatter:on
    }

    @NotNull
    private static ComponentBuilder<?, ?> geneSection(@NotNull final Slicer hmfSlicingRegion) {
        final long coverage = Math.round(hmfSlicingRegion.numberOfBases() / 1E6);
        final VerticalListBuilder section = toList("Details on filtering", Lists.newArrayList(
                "The findings in this report are generated from whole-genome-sequencing analysis, "
                        + "filtered on the following " + hmfSlicingRegion.numberOfRegions() + " genes.",
                "The canonical transcripts used for the filtering cover " + coverage + " MBases."));

        return section.add(cmp.verticalGap(HEADER_TO_DETAIL_VERTICAL_GAP),
                createGenePanel(hmfSlicingRegion.regions()));
    }

    @NotNull
    private static ComponentBuilder<?, ?> createGenePanel(@NotNull final Collection<GenomeRegion> regions) {
        final Collection<String> genes = Sets.newTreeSet();
        for (final GenomeRegion region : regions) {
            final HMFSlicingAnnotation annotation = HMFSlicingAnnotation.fromGenomeRegion(region);
            // KODU: The annotation should always be present on the HMF slicing regions!
            assert annotation != null;
            genes.add(annotation.gene());
        }
        final VerticalListBuilder table = cmp.verticalList();
        final int nrOfGenesPerRow = 10;

        long nrOfRowsNeeded = Math.round((double) genes.size() / nrOfGenesPerRow);
        nrOfRowsNeeded = (nrOfRowsNeeded * nrOfGenesPerRow < genes.size()) ? nrOfRowsNeeded + 1 : nrOfRowsNeeded;

        for (int i = 0; i < nrOfRowsNeeded; i++) {
            final HorizontalListBuilder row = cmp.horizontalList();
            for (int j = 0; j < nrOfGenesPerRow; j++) {
                int index = i * nrOfGenesPerRow + j + 1;
                final String gene = index > genes.size() ? Strings.EMPTY : (String) genes.toArray()[index - 1];
                row.add(cmp.text(gene).setStyle(dataTableStyle()));
            }
            table.add(row);
        }
        return table;
    }

    @NotNull
    private static ComponentBuilder<?, ?> variantFieldExplanationSection() {
        return toList("Details on reported variant fields",
                Lists.newArrayList("The analysis is based on reference genome version GRCh37.",
                        "The 'position' refers to the chromosome and start base of the variant with "
                                + "respect to the reference genome used.",
                        "The 'variant' displays what was expected as reference base and what "
                                + "was found instead ('ref' > 'alt')",
                        "The 'transcript' provides a link to the ensembl definition of the transcript "
                                + "used for filtering",
                        "The 'effect' provides additional information on the variant, including "
                                + "the change in coding sequence ('c.'), the change in amino acid ('a.') and "
                                + "the predicted impact on the final protein on the second line of this field",
                        "The 'cosmic' fields display a link to the COSMIC database which contains "
                                + "additional information on the variant. If the variant could not be found in the "
                                + "COSMIC database, this field will be left blank.",
                        "The 'VAF' fields displays the variant allele frequency. The first number is "
                                + "the number of observations of the variant, and the second number is the total "
                                + "number of observations on this position. The number within parentheses is the "
                                + "allele frequency (the two numbers divided by each other)",
                        "The mutational load is the total number of missense variants found in the genome."));
    }

    @NotNull
    private static ComponentBuilder<?, ?> copyNumberExplanationSection() {
        return toList("Details on reported copy numbers",
                Lists.newArrayList("Copy numbers are determined for all genes filtered for in this report.",
                        "The lowest copy number across the entire canonical transcript is determined.",
                        "Any gene with a value of 0 or >3 is included in the list of findings"));
    }

    @NotNull
    private static VerticalListBuilder toList(@NotNull final String title, @NotNull final Iterable<String> lines) {
        final VerticalListBuilder list = cmp.verticalList();
        list.add(cmp.horizontalList(cmp.horizontalGap(TEXT_HEADER_INDENT),
                cmp.text(title).setStyle(fontStyle().bold().setFontSize(11))),
                cmp.verticalGap(HEADER_TO_DETAIL_VERTICAL_GAP));
        boolean isFirst = true;
        for (final String line : lines) {
            if (!isFirst) {
                list.add(cmp.verticalGap(DETAIL_TO_DETAIL_VERTICAL_GAP));
            }
            list.add(cmp.horizontalList(cmp.horizontalGap(TEXT_DETAIL_INDENT),
                    cmp.text("- ").setStyle(fontStyle()).setWidth(LIST_INDENT), cmp.text(line).setStyle(fontStyle())));

            isFirst = false;
        }
        return list;
    }

    @NotNull
    private static JasperReportBuilder baseTable() {
        return report().setColumnStyle(dataStyle()).setColumnTitleStyle(tableHeaderStyle()).highlightDetailEvenRows();
    }

    @NotNull
    private static StyleBuilder sectionHeaderStyle() {
        return fontStyle().bold().setFontSize(12).setHorizontalTextAlignment(HorizontalTextAlignment.CENTER);
    }

    @NotNull
    private static StyleBuilder tableHeaderStyle() {
        return fontStyle().bold().setHorizontalTextAlignment(HorizontalTextAlignment.CENTER).setFontSize(10).setBorder(
                stl.pen1Point()).setBackgroundColor(new Color(210, 210, 210));
    }

    @NotNull
    private static StyleBuilder dataStyle() {
        return fontStyle().setFontSize(8).setHorizontalTextAlignment(
                HorizontalTextAlignment.CENTER).setVerticalTextAlignment(VerticalTextAlignment.MIDDLE);
    }

    @NotNull
    private static StyleBuilder dataTableStyle() {
        return dataStyle().setBorder(stl.pen1Point());
    }

    @NotNull
    private static StyleBuilder linkStyle() {
        return dataStyle().setForegroundColor(Color.BLUE);
    }

    @NotNull
    private static StyleBuilder fontStyle() {
        return stl.style().setFontName(FONT);
    }

    @NotNull
    private static TextColumnBuilder<?> transcriptColumn() {
        return col.column("Transcript", PatientDataSource.TRANSCRIPT_FIELD).setWidth(150).setHyperLink(
                hyperLink(new TranscriptLinkExpression())).setStyle(linkStyle());
    }

    @NotNull
    private static ComponentBuilder<?, ?> effectColumn() {
        return cmp.verticalList(
                cmp.horizontalList(cmp.text(DataExpression.fromField(PatientDataSource.HGVS_CODING_FIELD)),
                        cmp.text(DataExpression.fromField(PatientDataSource.HGVS_PROTEIN_FIELD))),
                cmp.text(DataExpression.fromField(PatientDataSource.EFFECT_FIELD)));
    }
}
