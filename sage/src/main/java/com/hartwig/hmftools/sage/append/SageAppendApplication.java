package com.hartwig.hmftools.sage.append;

import static com.hartwig.hmftools.common.utils.config.ConfigUtils.setLogLevel;
import static com.hartwig.hmftools.common.utils.sv.BaseRegion.positionWithin;
import static com.hartwig.hmftools.sage.SageCommon.SG_LOGGER;
import static com.hartwig.hmftools.sage.vcf.VariantVCF.appendHeader;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.utils.Doubles;
import com.hartwig.hmftools.common.utils.TaskExecutor;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;
import com.hartwig.hmftools.common.utils.sv.ChrBaseRegion;
import com.hartwig.hmftools.common.utils.version.VersionInfo;
import com.hartwig.hmftools.common.variant.VcfFileReader;
import com.hartwig.hmftools.common.variant.impact.VariantImpact;
import com.hartwig.hmftools.common.variant.impact.VariantImpactSerialiser;
import com.hartwig.hmftools.sage.SageConfig;
import com.hartwig.hmftools.sage.pipeline.ChromosomePartition;
import com.hartwig.hmftools.sage.quality.BaseQualityRecalibration;
import com.hartwig.hmftools.sage.quality.QualityRecalibrationMap;
import com.hartwig.hmftools.sage.vcf.VariantVCF;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.IndexedFastaSequenceFile;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;

public class SageAppendApplication
{
    private final SageConfig mConfig;
    private final String mInputVcf;
    private final IndexedFastaSequenceFile mRefGenome;
    private final boolean mFilterToGenes;

    private static final double MIN_PRIOR_VERSION = 2.8;
    private static final String INPUT_VCF = "input_vcf";
    private static final String FILTER_TO_GENES = "require_gene";

    public SageAppendApplication(final ConfigBuilder configBuilder)
    {
        final VersionInfo version = new VersionInfo("sage.version");
        SG_LOGGER.info("SAGE version: {}", version.version());

        mConfig = new SageConfig(true, version.version(), configBuilder);
        mInputVcf = mConfig.SampleDataDir + configBuilder.getValue(INPUT_VCF);
        mFilterToGenes = configBuilder.hasFlag(FILTER_TO_GENES);

        IndexedFastaSequenceFile refFastaSeqFile = null;

        try
        {
            refFastaSeqFile = new IndexedFastaSequenceFile(new File(mConfig.RefGenomeFile));
        }
        catch (IOException e)
        {
            SG_LOGGER.error("Reference file loading failed: {}", e.toString());
            System.exit(1);
        }

        mRefGenome = refFastaSeqFile;
    }

    public void run() throws IOException, ExecutionException, InterruptedException
    {
        // check config
        if(mInputVcf == null || mInputVcf.isEmpty())
        {
            SG_LOGGER.error("no input VCF file specified");
            System.exit(1);
        }

        if(mInputVcf.equals(mConfig.OutputFile))
        {
            SG_LOGGER.error("input and output VCFs must be different");
            System.exit(1);
        }

        if(mConfig.ReferenceIds.isEmpty())
        {
            SG_LOGGER.error("missing reference Id must be supplied");
            System.exit(1);
        }

        SG_LOGGER.info("reading and validating file: {}", mInputVcf);

        long startTime = System.currentTimeMillis();

        // mFilterToGenes

        VcfFileReader vcfFileReader = new VcfFileReader(mInputVcf);

        if(!vcfFileReader.fileValid())
        {
            SG_LOGGER.error("invalid input VCF({})", mInputVcf);
            System.exit(1);
        }

        VCFHeader inputHeader = vcfFileReader.vcfHeader();

        if(!validateInputHeader(inputHeader))
        {
            System.exit(1);
        }

        final List<VariantContext> existingVariants = Lists.newArrayList();

        for(VariantContext variantContext : vcfFileReader.iterator())
        {
            VariantContext variant = variantContext.fullyDecode(inputHeader, false);

            if(mFilterToGenes)
            {
                VariantImpact variantImpact = VariantImpactSerialiser.fromVariantContext(variant);

                if(variantImpact == null || variantImpact.CanonicalGeneName.isEmpty())
                    continue;
            }

            existingVariants.add(variant);
        }

        vcfFileReader.close();

        SG_LOGGER.info("loaded {} variants", existingVariants.size());

        SG_LOGGER.info("writing to file: {}", mConfig.OutputFile);
        final VariantVCF outputVCF = new VariantVCF(mRefGenome, mConfig, inputHeader);

        if(existingVariants.isEmpty())
        {
            outputVCF.close();
            SG_LOGGER.info("writing empty output VCF", existingVariants.size());
            return;
        }

        final SAMSequenceDictionary dictionary = dictionary();

        BaseQualityRecalibration baseQualityRecalibration = new BaseQualityRecalibration(mConfig, mRefGenome);
        baseQualityRecalibration.produceRecalibrationMap();

        if(!baseQualityRecalibration.isValid())
            System.exit(1);

        final Map<String,QualityRecalibrationMap> recalibrationMap = baseQualityRecalibration.getSampleRecalibrationMap();

        final ChromosomePartition chromosomePartition = new ChromosomePartition(mConfig, mRefGenome);

        for(final SAMSequenceRecord samSequenceRecord : dictionary.getSequences())
        {
            final String chromosome = samSequenceRecord.getSequenceName();

            if(!mConfig.processChromosome(chromosome))
                continue;

            SG_LOGGER.info("processing chromosome({})", chromosome);

            final List<VariantContext> chromosomeVariants = existingVariants.stream()
                    .filter(x -> x.getContig().equals(chromosome)).collect(Collectors.toList());

            List<ChrBaseRegion> chrBaseRegions = chromosomePartition.partition(chromosome);

            List<RegionAppendTask> regionTasks = Lists.newArrayList();

            for(int i = 0; i < chrBaseRegions.size(); ++i)
            {
                ChrBaseRegion region = chrBaseRegions.get(i);

                final List<VariantContext> regionVariants = chromosomeVariants.stream()
                        .filter(x -> positionWithin(x.getStart(), region.start(), region.end()))
                        .collect(Collectors.toList());

                if(regionVariants.isEmpty())
                    continue;

                regionTasks.add(new RegionAppendTask(i, region, regionVariants, mConfig, mRefGenome, recalibrationMap));
            }

            final List<Callable> callableList = regionTasks.stream().collect(Collectors.toList());
            TaskExecutor.executeTasks(callableList, mConfig.Threads);

            for(RegionAppendTask regionTask : regionTasks)
            {
                final List<VariantContext> updatedVariants = regionTask.finalVariants();
                updatedVariants.forEach(outputVCF::write);
            }
        }

        outputVCF.close();

        mRefGenome.close();

        long timeTaken = System.currentTimeMillis() - startTime;
        SG_LOGGER.info("completed in {} seconds", String.format("%.1f",timeTaken / 1000.0));
    }

    private boolean validateInputHeader(VCFHeader header)
    {
        double oldVersion = sageVersion(header);
        if(Doubles.lessThan(oldVersion, MIN_PRIOR_VERSION))
        {
            SG_LOGGER.error("Sage VCF version({}) older than required({})", oldVersion, MIN_PRIOR_VERSION);
            return false;
        }

        final Set<String> existingSamples = existingSamples(header);

        StringJoiner sj = new StringJoiner(", ");
        existingSamples.forEach(x -> sj.add(x));

        SG_LOGGER.info("existing VCF samples: {}", sj.toString());

        for(String refSample : mConfig.ReferenceIds)
        {
            if(existingSamples.contains(refSample))
            {
                SG_LOGGER.error("config reference sample({}) already exits in input VCF", refSample);
                return false;
            }
        }

        appendHeader(header);

        return true;
    }

    private static double sageVersion(@NotNull final VCFHeader header)
    {
        VCFHeaderLine oldVersion = header.getMetaDataLine(VariantVCF.VERSION_META_DATA);

        if(oldVersion == null)
            return 0;

        String[] versionComponents = oldVersion.getValue().split("\\.", -1);

        try
        {
            return Double.parseDouble(versionComponents[0]) + Double.parseDouble(versionComponents[1]);
        }
        catch(Exception e)
        {
            SG_LOGGER.error("failed to parse Sage version: {}", oldVersion.getValue());
            return 0;
        }
    }

    private static Set<String> existingSamples(final VCFHeader header)
    {
        return Sets.newHashSet(header.getGenotypeSamples());
    }

    private SAMSequenceDictionary dictionary() throws IOException
    {
        final String bam = mConfig.ReferenceBams.get(0);

        SamReader tumorReader = SamReaderFactory.makeDefault()
                .validationStringency(mConfig.BamStringency)
                .referenceSource(new ReferenceSource(mRefGenome)).open(new File(bam));

        SAMSequenceDictionary dictionary = tumorReader.getFileHeader().getSequenceDictionary();
        tumorReader.close();
        return dictionary;
    }

    public static void main(String[] args)
    {
        ConfigBuilder configBuilder = new ConfigBuilder();
        SageConfig.registerCommonConfig(configBuilder);
        configBuilder.addPath(INPUT_VCF, true, "Path to input vcf");
        configBuilder.addFlag(FILTER_TO_GENES, "Only process variants with gene annotations");

        if(!configBuilder.parseCommandLine(args))
        {
            configBuilder.logInvalidDetails();
            System.exit(1);
        }

        setLogLevel(configBuilder);

        SageAppendApplication application = new SageAppendApplication(configBuilder);

        try
        {
            application.run();
        }
        catch(Exception e)
        {
            SG_LOGGER.error("error: {}", e.toString());
            e.printStackTrace();
            System.exit(1);
        }
    }

}
