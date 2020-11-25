package com.hartwig.hmftools.isofox.novel.cohort;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import static com.hartwig.hmftools.common.fusion.FusionCommon.POS_STRAND;
import static com.hartwig.hmftools.common.utils.Strings.appendStrList;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.common.utils.io.FileWriterUtils.createFieldsIndexMap;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_END;
import static com.hartwig.hmftools.common.utils.sv.StartEndIterator.SE_START;
import static com.hartwig.hmftools.common.utils.sv.SvRegion.positionWithin;
import static com.hartwig.hmftools.common.variant.structural.StructuralVariantFactory.PASS;
import static com.hartwig.hmftools.isofox.IsofoxConfig.ISF_LOGGER;
import static com.hartwig.hmftools.isofox.cohort.CohortAnalysisType.ALT_SPLICE_JUNCTION;
import static com.hartwig.hmftools.isofox.cohort.CohortConfig.formSampleFilenames;
import static com.hartwig.hmftools.isofox.novel.AltSpliceJunctionType.MIXED_TRANS;
import static com.hartwig.hmftools.isofox.novel.AltSpliceJunctionType.NOVEL_3_PRIME;
import static com.hartwig.hmftools.isofox.novel.AltSpliceJunctionType.NOVEL_5_PRIME;
import static com.hartwig.hmftools.isofox.novel.AltSpliceJunctionType.NOVEL_EXON;
import static com.hartwig.hmftools.isofox.novel.AltSpliceJunctionType.SKIPPED_EXONS;
import static com.hartwig.hmftools.isofox.novel.AltSpliceJunctionType.UNKNOWN;
import static com.hartwig.hmftools.isofox.novel.cohort.AcceptorDonorType.ACCEPTOR;
import static com.hartwig.hmftools.isofox.novel.cohort.AcceptorDonorType.DONOR;
import static com.hartwig.hmftools.isofox.novel.cohort.AcceptorDonorType.NONE;
import static com.hartwig.hmftools.isofox.novel.cohort.AltSjCohortAnalyser.loadFile;
import static com.hartwig.hmftools.isofox.novel.cohort.SpliceVariantMatchType.PROXIMATE;
import static com.hartwig.hmftools.isofox.novel.cohort.SpliceVariantMatchType.RELATED;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.DELIMITER;
import static com.hartwig.hmftools.isofox.results.ResultsWriter.ITEM_DELIM;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.ensemblcache.EnsemblGeneData;
import com.hartwig.hmftools.common.ensemblcache.ExonData;
import com.hartwig.hmftools.common.ensemblcache.TranscriptData;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.VariantType;
import com.hartwig.hmftools.isofox.cohort.CohortConfig;
import com.hartwig.hmftools.isofox.novel.AltSpliceJunction;
import com.hartwig.hmftools.isofox.novel.AltSpliceJunctionContext;
import com.hartwig.hmftools.isofox.novel.AltSpliceJunctionType;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class SpliceVariantMatcher
{
    private final CohortConfig mConfig;
    private final EnsemblDataCache mGeneTransCache;

    private Map<String,List<SpliceVariant>> mSampleSpliceVariants;

    private BufferedWriter mWriter;
    private BufferedWriter mSomaticWriter;
    private final Map<String,Integer> mFieldsMap;

    private final Map<String,EnsemblGeneData> mGeneDataMap;
    private final AltSjFilter mAltSjFilter;

    private final Map<String,Integer> mCohortAltSJs;
    private final boolean mWriteSomaticVariants;

    private static final String SOMATIC_VARIANT_FILE = "somatic_variant_file";
    private static final String COHORT_ALT_SJ_FILE = "cohort_alt_sj_file";
    private static final String INCLUDE_ALL_TRANSCRIPTS = "include_all_transcripts";
    private static final String WRITE_SOMATIC_VARIANTS = "write_somatic_variants";

    public SpliceVariantMatcher(final CohortConfig config, final CommandLine cmd)
    {
        mConfig = config;
        mFieldsMap = Maps.newHashMap();
        mGeneDataMap = Maps.newHashMap();
        mCohortAltSJs = Maps.newHashMap();

        boolean allTranscripts = cmd.hasOption(INCLUDE_ALL_TRANSCRIPTS);
        mWriteSomaticVariants = cmd.hasOption(WRITE_SOMATIC_VARIANTS);

        mGeneTransCache = new EnsemblDataCache(mConfig.EnsemblDataCache, RefGenomeVersion.HG37);
        mGeneTransCache.setRequiredData(true, false, false, !allTranscripts);
        mGeneTransCache.load(false);
        mSampleSpliceVariants = Maps.newHashMap();

        mAltSjFilter = new AltSjFilter(mConfig.RestrictedGeneIds, mConfig.ExcludedGeneIds, 0);
        mWriter = null;

        initialiseWriter();

        if(cmd.hasOption(SOMATIC_VARIANT_FILE))
        {
            loadSpliceVariants(cmd.getOptionValue(SOMATIC_VARIANT_FILE));
        }

        if(cmd.hasOption(COHORT_ALT_SJ_FILE))
        {
            loadCohortAltSJs(cmd.getOptionValue(COHORT_ALT_SJ_FILE));
        }

        for(String geneId : mConfig.RestrictedGeneIds)
        {
            final EnsemblGeneData geneData = mGeneTransCache.getGeneDataById(geneId);

            if(geneData != null)
            {
                mGeneDataMap.put(geneData.GeneName, geneData);
            }
        }
    }

    public static void addCmdLineOptions(final Options options)
    {
        options.addOption(SOMATIC_VARIANT_FILE, true, "File with somatic variants potentially affecting splicing");
        options.addOption(COHORT_ALT_SJ_FILE, true, "Cohort frequency for alt SJs");
        options.addOption(WRITE_SOMATIC_VARIANTS, false, "Write out somatic variants for subsequent non-DB loading");
        options.addOption(INCLUDE_ALL_TRANSCRIPTS, false, "Consider all transcripts, not just canonical (default: false)");
    }

    public void processAltSpliceJunctions()
    {
        final List<Path> filenames = Lists.newArrayList();

        if(!formSampleFilenames(mConfig, ALT_SPLICE_JUNCTION, filenames))
            return;

        for(int i = 0; i < mConfig.SampleData.SampleIds.size(); ++i)
        {
            final String sampleId = mConfig.SampleData.SampleIds.get(i);
            final Path altSJFile = filenames.get(i);

            final List<AltSpliceJunction> altSJs = loadFile(altSJFile, mFieldsMap, mAltSjFilter);

            ISF_LOGGER.debug("{}: sample({}) loaded {} alt-SJ records", i, sampleId, altSJs.size());
            evaluateSpliceVariants(sampleId, altSJs);
        }

        ISF_LOGGER.info("splice variant matching complete");

        closeBufferedWriter(mWriter);
        closeBufferedWriter(mSomaticWriter);
    }

    public void evaluateSpliceVariants(final String sampleId, final List<AltSpliceJunction> altSpliceJunctions)
    {
        if(mGeneTransCache == null || (mSampleSpliceVariants.isEmpty() && mConfig.DbAccess == null))
            return;

        final List<SpliceVariant> spliceVariants = getSomaticVariants(sampleId);

        if(spliceVariants == null || spliceVariants.isEmpty())
            return;

        ISF_LOGGER.debug("sampleId({}) evaluating {} splice variants",
                sampleId, spliceVariants.size());

        spliceVariants.forEach(x -> evaluateSpliceVariant(sampleId, altSpliceJunctions, x));

        if(mWriteSomaticVariants)
            spliceVariants.forEach(x -> writeSomaticVariant(sampleId, x));
    }

    private final List<SpliceVariant> getSomaticVariants(final String sampleId)
    {
        if(!mSampleSpliceVariants.isEmpty())
            return mSampleSpliceVariants.get(sampleId);

        final List<SpliceVariant> spliceVariants = Lists.newArrayList();

        final List<SomaticVariant> somaticVariants = mConfig.DbAccess.readSomaticVariants(sampleId, VariantType.UNDEFINED);

        // filter to specific gene list
        for(final SomaticVariant variant : somaticVariants)
        {
            if(!mGeneDataMap.isEmpty() && !mGeneDataMap.containsKey(variant.gene()))
                continue;

            if(variant.filter() != "PASS")

            spliceVariants.add(new SpliceVariant(
                    variant.gene(), variant.chromosome(), (int)variant.position(), variant.type(),variant.ref(), variant.alt(),
                    variant.canonicalEffect(), variant.canonicalHgvsCodingImpact(), variant.trinucleotideContext()));
        }

        return spliceVariants;
    }

    private void evaluateSpliceVariant(final String sampleId, final List<AltSpliceJunction> altSpliceJunctions, final SpliceVariant variant)
    {
        final EnsemblGeneData geneData = mGeneTransCache.getGeneDataByName(variant.GeneName);

        if(geneData == null)
        {
            ISF_LOGGER.error("variant({}) gene data not found");
            return;
        }

        final List<AltSpliceJunction> matchedAltSJs = findRelatedAltSpliceJunctions(sampleId, altSpliceJunctions, variant, geneData);

        findCloseAltSpliceJunctions(sampleId, altSpliceJunctions, variant, geneData, matchedAltSJs);
    }

    private static final int SPLICE_REGION_NON_CODING_DISTANCE = 10;
    private static final int SPLICE_REGION_CODING_DISTANCE = 2;

    private static boolean withinRange(int pos1, int pos2, int distance) { return abs(pos1 - pos2) <= distance; }

    private static boolean validRelatedType(AltSpliceJunctionType type)
    {
        return type == SKIPPED_EXONS || type == NOVEL_5_PRIME || type == NOVEL_3_PRIME || type == NOVEL_EXON || type == MIXED_TRANS;
    }

    private static boolean isExactMatch(final AltSpliceJunction altSJ, final SpliceVariant variant)
    {
        if(variant.Type != VariantType.INDEL)
            return false;

        int delLength = variant.Ref.length() - variant.Alt.length();

        if(delLength <= 0)
            return false;

        if(altSJ.SpliceJunction[SE_START] == variant.Position && altSJ.SpliceJunction[SE_END] == variant.Position + delLength + 1)
            return true;

        return false;
    }

    private final List<AltSpliceJunction> findRelatedAltSpliceJunctions(
            final String sampleId, final List<AltSpliceJunction> altSpliceJunctions,
            final SpliceVariant variant, final EnsemblGeneData geneData)
    {
        /* Candidate alt SJs must be either

        The splice region variant will be within 10 base of an exon.
        It may cause a novel splice junction anywhere in that exon or the intron.
        It could also cause the exon to be skipped entirely. eg. the splice variant is 7 bases after exon 3.
        Then you should check for novel splice junctions that are wholly contained within the entire region from end of exon 2 to start of exon 4.
        */

        final List<AltSpliceJunction> matchedAltSJs = Lists.newArrayList();

        final List<TranscriptData> transDataList = mGeneTransCache.getTranscripts(geneData.GeneId);

        boolean isWithinSpliceRegion = false;

        AcceptorDonorType closestAcceptorDonorType = AcceptorDonorType.NONE;
        int closestExonDistance = 0;
        String closestTransStr = "";

        for(final TranscriptData transData : transDataList)
        {
            for(int i = 0; i < transData.exons().size(); ++i)
            {
                final ExonData exon = transData.exons().get(i);

                AcceptorDonorType acceptorDonorType = AcceptorDonorType.NONE;
                int exonDistance = 0;

                if(positionWithin(variant.Position, exon.ExonStart, exon.ExonEnd))
                {
                    if(withinRange(exon.ExonStart, variant.Position, SPLICE_REGION_CODING_DISTANCE))
                    {
                        acceptorDonorType = geneData.Strand == POS_STRAND ? ACCEPTOR : DONOR;
                        exonDistance = -abs(exon.ExonStart - variant.Position);
                    }
                    else if(withinRange(exon.ExonEnd, variant.Position, SPLICE_REGION_CODING_DISTANCE))
                    {
                        acceptorDonorType = geneData.Strand == POS_STRAND ? DONOR : ACCEPTOR;
                        exonDistance = -abs(exon.ExonEnd - variant.Position);
                    }
                    else
                    {
                        continue;
                    }
                }
                else
                {
                    if(withinRange(exon.ExonStart, variant.Position, SPLICE_REGION_NON_CODING_DISTANCE))
                    {
                        acceptorDonorType = geneData.Strand == POS_STRAND ? ACCEPTOR : DONOR;
                        exonDistance = abs(exon.ExonStart - variant.Position);
                    }
                    else if(withinRange(exon.ExonEnd, variant.Position, SPLICE_REGION_NON_CODING_DISTANCE))
                    {
                        acceptorDonorType = geneData.Strand == POS_STRAND ? DONOR : ACCEPTOR;
                        exonDistance = abs(exon.ExonEnd - variant.Position);
                    }
                    else
                    {
                        continue;
                    }
                }

                isWithinSpliceRegion = true;

                if(closestAcceptorDonorType == NONE || abs(exonDistance) < abs(closestExonDistance))
                {
                    closestAcceptorDonorType = acceptorDonorType;
                    closestExonDistance = exonDistance;
                    closestTransStr = String.format("%s;%d", transData.TransName, exon.ExonRank);
                }

                final ExonData prevExon = i > 0 ? transData.exons().get(i - 1) : null;
                final ExonData nextExon = i < transData.exons().size() - 1 ? transData.exons().get(i + 1) : null;

                // look for any alt SJs

                for(final AltSpliceJunction altSJ : altSpliceJunctions)
                {
                    if(!altSJ.getGeneId().equals(geneData.GeneId))
                        continue;

                    if(!validRelatedType(altSJ.type()))
                        continue;

                    // skip exact base matches
                    if(isExactMatch(altSJ, variant))
                        continue;

                    // check for a position within the exon boundaries
                    int minAsjPos = min(altSJ.SpliceJunction[SE_START], altSJ.SpliceJunction[SE_END]);
                    int maxAsjPos = max(altSJ.SpliceJunction[SE_START], altSJ.SpliceJunction[SE_END]);

                    if(prevExon != null && minAsjPos < prevExon.ExonEnd)
                        continue;

                    if(nextExon != null && maxAsjPos > nextExon.ExonEnd)
                        continue;

                    ISF_LOGGER.trace("sampleId({}) variant({}:{}) gene({}) transcript({}) matched altSJ({})",
                            sampleId, variant.Chromosome, variant.Position, geneData.GeneName, transData.TransName, altSJ.toString());

                    final String transDataStr = String.format("%s;%d", transData.TransName, exon.ExonRank);
                    writeMatchData(sampleId, variant, geneData, altSJ, RELATED, acceptorDonorType, exonDistance, transDataStr);

                    matchedAltSJs.add(altSJ);
                }
            }
        }

        if(matchedAltSJs.isEmpty() && isWithinSpliceRegion)
        {
            writeMatchData(
                    sampleId, variant, geneData, null, SpliceVariantMatchType.NONE,
                    closestAcceptorDonorType, closestExonDistance, closestTransStr);
        }

        return matchedAltSJs;
    }

    private static final int CLOSE_ALT_SJ_DISTANCE = 5;

    private void findCloseAltSpliceJunctions(
            final String sampleId, final List<AltSpliceJunction> altSpliceJunctions, final SpliceVariant variant,
            final EnsemblGeneData geneData, final List<AltSpliceJunction> matchedAltSJs)
    {
        final List<AltSpliceJunction> closeAltSJs = altSpliceJunctions.stream()
                .filter(x -> !matchedAltSJs.contains(x)) // only report ones not already related
                .filter(x -> !isExactMatch(x, variant))
                .filter(x -> withinRange(x.SpliceJunction[SE_START], variant.Position, CLOSE_ALT_SJ_DISTANCE)
                        || withinRange(x.SpliceJunction[SE_END], variant.Position, CLOSE_ALT_SJ_DISTANCE))
                .collect(Collectors.toList());

        if(closeAltSJs.isEmpty())
            return;

        for(AltSpliceJunction altSJ : closeAltSJs)
        {
            List<String> allTransNames = Lists.newArrayList();

            for(int se = SE_START; se <= SE_END; ++se)
            {
                final String[] transNames = altSJ.getTranscriptNames()[se].split(ITEM_DELIM);
                for(String transName : transNames)
                {
                    if(!transName.equals("NONE") && !allTransNames.contains(transName))
                        allTransNames.add(transName);
                }
            }

            writeMatchData(
                    sampleId, variant, geneData, altSJ, PROXIMATE, NONE, -1,
                    appendStrList(allTransNames, ITEM_DELIM.charAt(0)));
        }
    }

    private void initialiseWriter()
    {
        try
        {
            final String outputFileName = mConfig.formCohortFilename("splice_variant_matching.csv");
            mWriter = createBufferedWriter(outputFileName, false);

            mWriter.write("SampleId,MatchType,GeneId,GeneName,Chromosome,Strand");
            mWriter.write(",Position,Type,CodingEffect,Ref,Alt,HgvsImpact,TriNucContext,AccDonType,ExonDistance");
            mWriter.write(",AsjType,AsjContextStart,AsjContextEnd,AsjPosStart,AsjPosEnd,AsjFragmentCount,AsjDepthStart,AsjDepthEnd");
            mWriter.write(",AsjCohortCount,AsjTransData");

            mWriter.newLine();

            if(mWriteSomaticVariants)
            {
                final String somVarFileName = mConfig.formCohortFilename("somatic_variants.csv");
                mSomaticWriter = createBufferedWriter(somVarFileName, false);

                mSomaticWriter.write("SampleId,GeneName,Chromosome,Position,Type,CodingEffect,Ref,Alt,HgvsImpact,TriNucContext");
                mSomaticWriter.newLine();
            }
        }
        catch(IOException e)
        {
            ISF_LOGGER.error("failed to write splice variant file: {}", e.toString());
        }
    }

    private void writeMatchData(
            final String sampleId, final SpliceVariant variant, final EnsemblGeneData geneData, final AltSpliceJunction altSJ,
            final SpliceVariantMatchType matchType, final AcceptorDonorType accDonType, int exonBaseDistance, final String transDataStr)
    {
        try
        {
            mWriter.write(String.format("%s,%s,%s,%s,%s,%d",
                    sampleId, matchType, geneData.GeneId, geneData.GeneName, geneData.Chromosome, geneData.Strand));

            mWriter.write(String.format(",%d,%s,%s,%s,%s,%s,%s,%s,%d",
                    variant.Position, variant.Type, variant.CodingEffect, variant.Ref, variant.Alt,
                    variant.HgvsCodingImpact, variant.TriNucContext, accDonType, exonBaseDistance));

            if(altSJ != null)
            {
                mWriter.write(String.format(",%s,%s,%s,%d,%d,%d,%d,%d,%d,%s",
                        altSJ.type(), altSJ.RegionContexts[SE_START], altSJ.RegionContexts[SE_END],
                        altSJ.SpliceJunction[SE_START], altSJ.SpliceJunction[SE_END],
                        altSJ.getFragmentCount(), altSJ.getPositionCount(SE_START), altSJ.getPositionCount(SE_END),
                        getCohortAltSjFrequency(altSJ), transDataStr));
            }
            else
            {
                mWriter.write(String.format(",%s,%s,%s,%d,%d,%d,%d,%d,%s",
                        UNKNOWN, AltSpliceJunctionContext.UNKNOWN, AltSpliceJunctionContext.UNKNOWN, -1, -1, 0, 0, 0, 0, ""));
            }

            mWriter.newLine();
        }
        catch(IOException e)
        {
            ISF_LOGGER.error("failed to write splice variant matching file: {}", e.toString());
        }
    }

    private void writeSomaticVariant(final String sampleId, final SpliceVariant variant)
    {
        if(mSomaticWriter == null)
            return;

        try
        {
            mSomaticWriter.write(String.format("%s,%s,%s,%d,%s,%s,%s,%s,%s,%s",
                    sampleId, variant.GeneName, variant.Chromosome, variant.Position, variant.Type,
                    variant.CodingEffect, variant.Ref, variant.Alt, variant.HgvsCodingImpact, variant.TriNucContext));

            mSomaticWriter.newLine();
        }
        catch(IOException e)
        {
            ISF_LOGGER.error("failed to write splice variant data: {}", e.toString());
        }
    }

    private int getCohortAltSjFrequency(final AltSpliceJunction altSJ)
    {
        if(mCohortAltSJs.isEmpty())
            return 0;

        final String altSjKey = String.format("%s;%s;%s",
            altSJ.Chromosome, altSJ.SpliceJunction[SE_START], altSJ.SpliceJunction[SE_END]); // altSJ.type(),

        Integer cohortCount = mCohortAltSJs.get(altSjKey);
        return cohortCount != null ? cohortCount : 0;
    }

    private void loadCohortAltSJs(final String filename)
    {
        if(!Files.exists(Paths.get(filename)))
        {
            ISF_LOGGER.error("invalid cohort alt-SJ file({})", filename);
            return;
        }

        try
        {

            final List<String> lines = Files.readAllLines(Paths.get(filename));

            final Map<String,Integer> fieldsMap = createFieldsIndexMap(lines.get(0), DELIMITER);

            lines.remove(0);

            // GeneId,CancerType,SampleCount,Chromosome,Type,SjStart,SjEnd
            int sampleCountIndex = fieldsMap.get("SampleCount");
            // int typeIndex = fieldsMap.get("Type");
            int chromosomeIndex = fieldsMap.get("Chromosome");
            int sjStartPosIndex = fieldsMap.get("SjStart");
            int sjEndPosIndex = fieldsMap.get("SjEnd");

            for(final String data : lines)
            {
                final String[] items = data.split(DELIMITER);

                final String altSjKey = String.format("%s;%s;%s",
                        items[chromosomeIndex], items[sjStartPosIndex], items[sjEndPosIndex]);

                final int sampleCount = Integer.parseInt(items[sampleCountIndex]);

                mCohortAltSJs.put(altSjKey, sampleCount);
            }

            ISF_LOGGER.info("loaded {} cohort alt-SJ records", mCohortAltSJs.size());
        }
        catch(IOException e)
        {
            ISF_LOGGER.error("failed to load cohort alt-SJ data file({}): {}", filename, e.toString());
            return;
        }
    }

    private void loadSpliceVariants(final String filename)
    {
        if(!Files.exists(Paths.get(filename)))
        {
            ISF_LOGGER.error("invalid splice variant file({})", filename);
            return;
        }

        try
        {

            final List<String> lines = Files.readAllLines(Paths.get(filename));

            final Map<String,Integer> fieldsMap = createFieldsIndexMap(lines.get(0), DELIMITER);

            lines.remove(0);

            int sampleIdIndex = mFieldsMap.get("SampleId");

            List<SpliceVariant> spliceVariants = null;
            String currentSampleId = "";

            for(final String data : lines)
            {
                final String[] items = data.split(DELIMITER);
                final String sampleId = items[sampleIdIndex];

                if(!sampleId.equals(currentSampleId))
                {
                    currentSampleId = sampleId;
                    spliceVariants = Lists.newArrayList();
                    mSampleSpliceVariants.put(sampleId, spliceVariants);
                }

                spliceVariants.add(SpliceVariant.fromCsv(items, fieldsMap));
            }
        }
        catch(IOException e)
        {
            ISF_LOGGER.error("failed to load splice variant data file({}): {}", filename, e.toString());
            return;
        }
    }

}
