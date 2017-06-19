package com.hartwig.hmftools.purple;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import com.hartwig.hmftools.common.chromosome.Chromosomes;
import com.hartwig.hmftools.common.circos.CircosFileWriter;
import com.hartwig.hmftools.common.circos.CircosLinkWriter;
import com.hartwig.hmftools.common.exception.HartwigException;
import com.hartwig.hmftools.common.purple.copynumber.PurpleCopyNumber;
import com.hartwig.hmftools.common.purple.purity.FittedPurity;
import com.hartwig.hmftools.common.variant.EnrichedSomaticVariant;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.VariantType;
import com.hartwig.hmftools.common.variant.structural.StructuralVariant;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;
import com.hartwig.hmftools.purple.plot.ChartWriter;
import com.hartwig.hmftools.purple.somatic.EnrichedSomaticVariantFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class GenerateCircosData {

    private static final Logger LOGGER = LogManager.getLogger(GenerateCircosData.class);

    private static final String DATA_OUTPUT_DIR = "data_output_dir";
    private static final String PLOT_OUTPUT_DIR = "plot_output_dir";
    private static final String SAMPLE = "sample";
    private static final String DB_USER = "db_user";
    private static final String DB_PASS = "db_pass";
    private static final String DB_URL = "db_url";

    public static void main(@NotNull final String[] args) throws ParseException, IOException, HartwigException, SQLException {
        final Options options = createBasicOptions();
        final CommandLine cmd = createCommandLine(args, options);
        final String sample = cmd.getOptionValue(SAMPLE);
        final String dataOutput = cmd.getOptionValue(DATA_OUTPUT_DIR);
        final String plotOutput = cmd.getOptionValue(DATA_OUTPUT_DIR);
        final DatabaseAccess dbAccess = databaseAccess(cmd);

        LOGGER.info("Loading {} data from database");
        final FittedPurity purity = dbAccess.readFittedPurity(sample);
        if (purity == null) {
            LOGGER.error("Purity not available");
            System.exit(-1);
        }

        final List<PurpleCopyNumber> copyNumber = dbAccess.readCopynumbers(sample);
        if (copyNumber.isEmpty()) {
            LOGGER.error("Copynumber not available");
            System.exit(-1);
        }

        final List<SomaticVariant> somaticVariants = dbAccess.readComprehensiveSomaticVariants(sample, true)
                .stream()
                .filter(x -> x.type() == VariantType.SNP)
                .filter(x -> Chromosomes.asInt(x.chromosome()) <= 22)
                .collect(Collectors.toList());
        if (somaticVariants.isEmpty()) {
            LOGGER.error("Somatic Variants not available");
            System.exit(-1);
        }

        final List<StructuralVariant> structuralVariants = dbAccess.readStructuralVariants(sample);
        if (structuralVariants.isEmpty()) {
            LOGGER.error("Structural Variants not available");
        } else {
            CircosLinkWriter.writeVariants(dataOutput + File.separator + sample + ".link.circos", structuralVariants);
        }

        final List<EnrichedSomaticVariant> enrichedSomaticVariants = EnrichedSomaticVariantFactory.create(purity, somaticVariants, copyNumber);

        LOGGER.info("Writing data files");
        CircosFileWriter.writePositions(dataOutput + File.separator + sample + ".snp.circos", enrichedSomaticVariants, EnrichedSomaticVariant::adjustedVAF);

        CircosFileWriter.writeRegions(dataOutput + File.separator + sample + ".cnv.circos", copyNumber, x -> x.averageTumorCopyNumber() - 2);

        CircosFileWriter.writeRegions(dataOutput + File.separator + sample + ".baf.circos", copyNumber, PurpleCopyNumber::averageActualBAF);

        LOGGER.info("Writing plots");
        final ChartWriter chartWriter = new ChartWriter(sample, plotOutput);
        chartWriter.copyNumberCDF(copyNumber);
        chartWriter.copyNumberPDF(copyNumber);
        chartWriter.somaticPloidy(enrichedSomaticVariants);

        LOGGER.info("Complete Successfully");
    }

    @NotNull
    private static Options createBasicOptions() {
        final Options options = new Options();
        options.addOption(PLOT_OUTPUT_DIR, true, "Plot output directory.");
        options.addOption(DATA_OUTPUT_DIR, true, "Data output directory.");
        options.addOption(SAMPLE, true, "Sample.");
        options.addOption(DB_USER, true, "Database user name.");
        options.addOption(DB_PASS, true, "Database password.");
        options.addOption(DB_URL, true, "Database url.");
        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final String[] args, @NotNull final Options options) throws ParseException {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    private static DatabaseAccess databaseAccess(CommandLine cmd) throws SQLException {
        final String userName = cmd.getOptionValue(DB_USER);
        final String password = cmd.getOptionValue(DB_PASS);
        final String databaseUrl = cmd.getOptionValue(DB_URL);  //e.g. mysql://localhost:port/database";
        final String jdbcUrl = "jdbc:" + databaseUrl;
        return new DatabaseAccess(userName, password, jdbcUrl);
    }
}
