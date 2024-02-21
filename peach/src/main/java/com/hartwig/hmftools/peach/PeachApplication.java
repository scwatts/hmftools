package com.hartwig.hmftools.peach;

import static com.hartwig.hmftools.common.utils.config.ConfigUtils.addLoggingOptions;

import com.hartwig.hmftools.common.utils.config.ConfigBuilder;
import com.hartwig.hmftools.peach.data_loader.DrugInfoLoader;
import com.hartwig.hmftools.peach.data_loader.HaplotypeEventLoader;
import com.hartwig.hmftools.peach.data_loader.HaplotypeFunctionLoader;
import com.hartwig.hmftools.peach.data_loader.PanelLoader;
import com.hartwig.hmftools.peach.effect.DrugInfoStore;
import com.hartwig.hmftools.peach.effect.HaplotypeFunction;
import com.hartwig.hmftools.peach.effect.HaplotypeFunctionStore;
import com.hartwig.hmftools.peach.output.AllHaplotypeCombinationsFile;
import com.hartwig.hmftools.peach.output.BestHaplotypeCombinationsFile;
import com.hartwig.hmftools.peach.output.EventsFile;
import com.hartwig.hmftools.peach.output.EventsPerGeneFile;
import com.hartwig.hmftools.peach.output.QcStatusFile;
import com.hartwig.hmftools.peach.panel.HaplotypePanel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.hartwig.hmftools.peach.PeachUtils.PCH_LOGGER;

public class PeachApplication
{
    @NotNull
    private final PeachConfig config;

    public PeachApplication(@NotNull ConfigBuilder configBuilder)
    {
        this.config = new PeachConfig(configBuilder);
    }

    public void run()
    {
        try
        {
            if(!config.isValid())
            {
                throw new IllegalArgumentException("invalid config");
            }
            PCH_LOGGER.info("running PEACH");

            PCH_LOGGER.info("creating output directory");
            createOutputDirectory();

            PCH_LOGGER.info("load config files");

            PCH_LOGGER.info("load haplotype config");
            HaplotypePanel haplotypePanel = PanelLoader.loadHaplotypePanel(config.haplotypesFile);

            DrugInfoStore drugInfoStore = loadDrugInfoStore();
            HaplotypeFunctionStore haplotypeFunctionStore = loadHaplotypeFunctionStore();

            PCH_LOGGER.info("load events");
            Map<String, Integer> eventIdToCount =
                    HaplotypeEventLoader.loadRelevantVariantHaplotypeEvents(config.vcfFile, config.sampleName, haplotypePanel.getRelevantVariantPositions());

            PCH_LOGGER.info("call haplotypes");
            HaplotypeCaller caller = new HaplotypeCaller(haplotypePanel);
            Map<String, HaplotypeAnalysis> geneToHaplotypeAnalysis = caller.getGeneToHaplotypeAnalysis(eventIdToCount);

            writeOutputFiles(eventIdToCount, geneToHaplotypeAnalysis, drugInfoStore, haplotypeFunctionStore);
        }
        catch(Exception e)
        {
            PCH_LOGGER.error("unrecoverable error encountered", e);
            System.exit(1);
        }

        PCH_LOGGER.info("finished running PEACH");
    }

    private void writeOutputFiles(@NotNull Map<String, Integer> eventIdToCount,
            @NotNull Map<String, HaplotypeAnalysis> geneToHaplotypeAnalysis, @Nullable DrugInfoStore drugInfoStore,
            @Nullable HaplotypeFunctionStore haplotypeFunctionStore)
    {
        try
        {
            PCH_LOGGER.info("write events output file");
            EventsFile.write(config.getEventsOutputPath(), eventIdToCount);
            PCH_LOGGER.info("write events per gene output file");
            EventsPerGeneFile.write(config.getEventsPerGeneOutputPath(), geneToHaplotypeAnalysis);
            PCH_LOGGER.info("write all haplotype combinations output file");
            AllHaplotypeCombinationsFile.write(config.getAllHaplotypeCombinationsOutputPath(), geneToHaplotypeAnalysis);
            PCH_LOGGER.info("write best haplotype combination output file");
            BestHaplotypeCombinationsFile.write(config.getBestHaplotypeCombinationsOutputPath(), geneToHaplotypeAnalysis);
            PCH_LOGGER.info("write qc status output file");
            QcStatusFile.write(config.getQcStatusOutputPath(), geneToHaplotypeAnalysis);

//            if(drugInfoStore != null || haplotypeFunctionStore != null)
//            {
//                DrugFunctionFile.write(config.getDrugFunctionsOutputPath(), geneToHaplotypeAnalysis, drugInfoStore, haplotypeFunctionStore);
//            }
        }
        catch(IOException e)
        {
            throw new RuntimeException("failed to create all output files", e);
        }
    }

    @Nullable
    private DrugInfoStore loadDrugInfoStore()
    {
        if(config.drugsFile == null)
        {
            PCH_LOGGER.info("skip loading drugs since input file not provided");
            return null;
        }
        else
        {
            PCH_LOGGER.info("load drugs");
            return new DrugInfoStore(DrugInfoLoader.loadDrugInfos(config.drugsFile));
        }
    }

    @Nullable
    private HaplotypeFunctionStore loadHaplotypeFunctionStore()
    {
        if(config.functionFile == null)
        {
            PCH_LOGGER.info("skip loading haplotype functions since input file not provided");
            return null;
        }
        else
        {
            PCH_LOGGER.info("load haplotype functions");
            List<HaplotypeFunction> haplotypeFunctions = HaplotypeFunctionLoader.loadFunctions(config.functionFile);
            return new HaplotypeFunctionStore(haplotypeFunctions);
        }
    }

    private void createOutputDirectory()
    {
        // Output directory cannot be null in valid config
        assert config.outputDir != null;
        File outputDirectory = new File(config.outputDir);
        if(!outputDirectory.exists() && !outputDirectory.mkdirs())
        {
            throw new RuntimeException(String.format("could not create output directory: %s", config.outputDir));
        }
    }

    public static void main(@NotNull String[] args)
    {
        ConfigBuilder configBuilder = new ConfigBuilder("Peach");

        PeachConfig.addOptions(configBuilder);

        addLoggingOptions(configBuilder);

        configBuilder.checkAndParseCommandLine(args);

        PeachApplication peachApplication = new PeachApplication(configBuilder);
        peachApplication.run();
    }
}