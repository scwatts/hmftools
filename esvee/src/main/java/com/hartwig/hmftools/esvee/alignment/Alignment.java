package com.hartwig.hmftools.esvee.alignment;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import static com.hartwig.hmftools.common.region.BaseRegion.positionsOverlap;
import static com.hartwig.hmftools.common.sv.StructuralVariantType.DEL;
import static com.hartwig.hmftools.common.sv.StructuralVariantType.DUP;
import static com.hartwig.hmftools.common.sv.StructuralVariantType.SGL;
import static com.hartwig.hmftools.common.utils.TaskExecutor.runThreadTasks;
import static com.hartwig.hmftools.esvee.AssemblyConfig.SV_LOGGER;
import static com.hartwig.hmftools.esvee.AssemblyConstants.PROXIMATE_DEL_LENGTH;
import static com.hartwig.hmftools.esvee.alignment.AlignmentOutcome.ALT_LOC_MATCH;
import static com.hartwig.hmftools.esvee.alignment.AlignmentOutcome.MATCH;
import static com.hartwig.hmftools.esvee.alignment.AlignmentOutcome.MULTIPLE;
import static com.hartwig.hmftools.esvee.alignment.AlignmentOutcome.NON_SV_MATCH;
import static com.hartwig.hmftools.esvee.alignment.AlignmentOutcome.NO_MATCH;
import static com.hartwig.hmftools.esvee.alignment.AlignmentOutcome.NO_RESULT;
import static com.hartwig.hmftools.esvee.alignment.AlignmentOutcome.PARTIAL;
import static com.hartwig.hmftools.esvee.assembly.types.ThreadTask.mergePerfCounters;
import static com.hartwig.hmftools.esvee.common.SvConstants.MIN_VARIANT_LENGTH;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.esvee.AssemblyConfig;
import com.hartwig.hmftools.esvee.assembly.types.AssemblyLink;
import com.hartwig.hmftools.esvee.assembly.types.AssemblyOutcome;
import com.hartwig.hmftools.esvee.assembly.types.SupportRead;
import com.hartwig.hmftools.esvee.assembly.types.JunctionAssembly;
import com.hartwig.hmftools.esvee.assembly.types.LinkType;
import com.hartwig.hmftools.esvee.assembly.types.SupportType;
import com.hartwig.hmftools.esvee.assembly.types.ThreadTask;

import org.broadinstitute.hellbender.utils.bwa.BwaMemAlignment;

public class Alignment
{
    private final AssemblyConfig mConfig;

    private final AlignmentWriter mWriter;
    private final Aligner mAligner;
    private final AlignmentCache mAlignmentCache;

    public Alignment(final AssemblyConfig config, final Aligner aligner)
    {
        mConfig = config;
        mAligner = aligner;
        mWriter = new AlignmentWriter(mConfig);
        mAlignmentCache = new AlignmentCache(config.AlignmentFile);
    }

    public void close() { mWriter.close(); }

    private static final int MIN_ALIGN_LENGTH = MIN_VARIANT_LENGTH * 2;
    private static final int MIN_SUPPORT_COUNT = 4;

    public static boolean skipJunctionAssembly(final JunctionAssembly assembly)
    {
        // apply filters on what to bother aligning

        if(assembly.refBaseTrimLength() < MIN_ALIGN_LENGTH)
            return true;

        if(assembly.extensionLength() < MIN_ALIGN_LENGTH)
            return true;

        if(assembly.outcome() == AssemblyOutcome.DUP_BRANCHED
        || assembly.outcome() == AssemblyOutcome.DUP_SPLIT
        || assembly.outcome() == AssemblyOutcome.SECONDARY)
        {
            // since identical to or associated with other links
            return true;
        }

        if(assembly.supportCount() < MIN_SUPPORT_COUNT)
            return true;

        return false;
    }

    public static boolean skipAssemblyLink(final AssemblyLink assemblyLink)
    {
        if(assemblyLink.type() != LinkType.SPLIT)
            return true;

        if(assemblyLink.svType() == DEL || assemblyLink.svType() == DUP)
        {
            if(assemblyLink.length() < PROXIMATE_DEL_LENGTH)
                return true;
        }

        int combinedSequenceLength = assemblyLink.first().refBaseTrimLength() + assemblyLink.second().refBaseTrimLength()
                + assemblyLink.insertedBases().length() - assemblyLink.overlapBases().length();

        if(combinedSequenceLength < MIN_ALIGN_LENGTH)
            return true;

        Set<String> uniqueFrags = Sets.newHashSet();

        for(int i = 0; i <= 1; ++i)
        {
            JunctionAssembly assembly = (i == 0) ? assemblyLink.first() : assemblyLink.second();

            for(SupportRead support : assembly.support())
            {
                if(support.type() == SupportType.JUNCTION_MATE)
                    continue;

                if(uniqueFrags.contains(support.id()))
                    continue;

                uniqueFrags.add(support.id());

                if(uniqueFrags.size() >= MIN_SUPPORT_COUNT)
                    return false;
            }
        }

        return true;
    }

    public void run(final List<AssemblyAlignment> assemblyAlignments, final List<PerformanceCounter> perfCounters)
    {
        if(mAligner == null && !mAlignmentCache.enabled())
            return;

        int singleAssemblies = (int)assemblyAlignments.stream().filter(x -> x.svType() == SGL).count();
        int linkedAssemblies = assemblyAlignments.size() - singleAssemblies;

        SV_LOGGER.info("running alignment for {} assemblies, linked({}) single({})",
                assemblyAlignments.size(), linkedAssemblies, singleAssemblies);

        Queue<AssemblyAlignment> assemblyAlignmentQueue = new ConcurrentLinkedQueue<>();

        assemblyAlignments.forEach(x -> assemblyAlignmentQueue.add(x));

        List<Thread> threadTasks = new ArrayList<>();
        List<AssemblerAlignerTask> alignerTasks = Lists.newArrayList();

        int taskCount = min(mConfig.Threads, assemblyAlignments.size());

        for(int i = 0; i < taskCount; ++i)
        {
            AssemblerAlignerTask assemblerAlignerTask = new AssemblerAlignerTask(assemblyAlignmentQueue);
            alignerTasks.add(assemblerAlignerTask);
            threadTasks.add(assemblerAlignerTask);
        }

        if(!runThreadTasks(threadTasks))
            System.exit(1);

        SV_LOGGER.info("alignment complete");

        mergePerfCounters(perfCounters, alignerTasks.stream().collect(Collectors.toList()));
    }

    private class AssemblerAlignerTask extends ThreadTask
    {
        private final Queue<AssemblyAlignment> mAssemblyAlignments;
        private final int mAssemblyAlignmentCount;

        public AssemblerAlignerTask(final Queue<AssemblyAlignment> assemblyAlignments)
        {
            super("AssemblerAlignment");
            mAssemblyAlignments = assemblyAlignments;
            mAssemblyAlignmentCount = assemblyAlignments.size();
        }

        private static final int LOG_COUNT = 10000;

        @Override
        public void run()
        {
            while(true)
            {
                try
                {
                    int remainingCount = mAssemblyAlignments.size();
                    int processedCount = mAssemblyAlignmentCount - remainingCount;

                    mPerfCounter.start();

                    ++processedCount;

                    AssemblyAlignment assemblyAlignment = mAssemblyAlignments.remove();

                    processAssembly(assemblyAlignment);

                    if((processedCount % LOG_COUNT) == 0)
                    {
                        SV_LOGGER.debug("processed {} assembly alignments", processedCount);
                    }

                    mPerfCounter.stop();
                }
                catch(NoSuchElementException e)
                {
                    SV_LOGGER.trace("all alignment tasks complete");
                    break;
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }

        private void processAssembly(final AssemblyAlignment assemblyAlignment)
        {
            String fullSequence = assemblyAlignment.fullSequence();

            List<AlignData> alignments;

            if(mAlignmentCache.enabled())
            {
                alignments = mAlignmentCache.findAssemblyAlignments(assemblyAlignment.info());
            }
            else
            {
                List<BwaMemAlignment> bwaAlignments = mAligner.alignSequence(fullSequence.getBytes());

                alignments = bwaAlignments.stream()
                        .map(x -> AlignData.from(x, mConfig.RefGenVersion))
                        .filter(x -> x != null).collect(Collectors.toList());
            }

            processAlignmentResults(assemblyAlignment, alignments, fullSequence);

            AlignmentWriter.writeAssemblyAlignment(mWriter.alignmentWriter(), assemblyAlignment, fullSequence, alignments);
            AlignmentWriter.writeAlignmentDetails(mWriter.alignmentDetailsWriter(), assemblyAlignment, alignments);
        }

        private void processAlignmentResults(
                final AssemblyAlignment assemblyAlignment, final List<AlignData> alignments, final String fullSequence)
        {
            if(alignments.isEmpty())
            {
                assemblyAlignment.setOutcome(NO_RESULT);
                return;
            }

            if(isSequenceLengthMatch(alignments.get(0), fullSequence.length()))
            {
                assemblyAlignment.setOutcome(NON_SV_MATCH);
                return;
            }

            AlignmentOutcome topOutcomeFirst = NO_RESULT;
            AlignmentOutcome topOutcomeSecond = NO_RESULT;

            JunctionAssembly firstAssembly = assemblyAlignment.first();
            JunctionAssembly secondAssembly = assemblyAlignment.second();

            for(AlignData alignment : alignments)
            {
                topOutcomeFirst = assessTopAlignmentResult(firstAssembly, alignment, topOutcomeFirst);

                if(secondAssembly != null)
                    topOutcomeSecond = assessTopAlignmentResult(secondAssembly, alignment, topOutcomeSecond);
            }

            firstAssembly.setAlignmentOutcome(topOutcomeFirst);

            if(secondAssembly != null)
                secondAssembly.setAlignmentOutcome(topOutcomeSecond);
        }

        private static final int ALIGN_MATCH_JUNCTION_POS_BUFFER = 3;

        private static final int ALIGN_MATCH_DIFF_ABS = 2;
        private static final double ALIGN_MATCH_DIFF_PERC = 0.02;
        private static final double ALIGN_MATCH_PERC = 1 - ALIGN_MATCH_DIFF_PERC;

        private boolean isSequenceLengthMatch(final AlignData alignment, final int sequenceLength)
        {
            int fullSequenceLength = sequenceLength;
            int alignmentLength = alignment.RefLocation.baseLength();

            int fullMatchDiff = max(fullSequenceLength - alignmentLength, 0);

            return fullMatchDiff <= ALIGN_MATCH_DIFF_ABS || fullMatchDiff / (double) fullSequenceLength <= ALIGN_MATCH_DIFF_PERC;
        }

        private AlignmentOutcome assessTopAlignmentResult(
                final JunctionAssembly assembly, final AlignData alignment, final AlignmentOutcome existingOutcome)
        {
            AlignmentOutcome outcome = assessAlignmentResult(assembly, alignment);

            if(outcome.exactMatch() && existingOutcome.exactMatch())
                return MULTIPLE;

            return outcome.ordinal() < existingOutcome.ordinal() ? outcome : existingOutcome;
        }

        private AlignmentOutcome assessAlignmentResult(final JunctionAssembly assembly, final AlignData alignment)
        {
            int assemblyRefLength = assembly.refBaseLength();

            boolean sequenceLengthMatch = isSequenceLengthMatch(alignment, assemblyRefLength);

            if(!assembly.junction().chromosome().equals(alignment.RefLocation.Chromosome))
                return sequenceLengthMatch ? ALT_LOC_MATCH : NO_MATCH;

            int assemblyPosStart = assembly.isForwardJunction() ? assembly.minAlignedPosition() : assembly.junction().Position;
            int assemblyPosEnd = assembly.isForwardJunction() ? assembly.junction().Position : assembly.maxAlignedPosition();

            if(!positionsOverlap(alignment.RefLocation.start(), alignment.RefLocation.end(), assemblyPosStart, assemblyPosEnd))
                return sequenceLengthMatch ? ALT_LOC_MATCH : NO_MATCH;

            boolean matchesJunctionPosition = false;

            if(assembly.isForwardJunction())
            {
                if(abs(alignment.RefLocation.end() - assembly.junction().Position) <= ALIGN_MATCH_JUNCTION_POS_BUFFER)
                    matchesJunctionPosition = true;
            }
            else
            {
                if(abs(alignment.RefLocation.start() - assembly.junction().Position) <= ALIGN_MATCH_JUNCTION_POS_BUFFER)
                    matchesJunctionPosition = true;
            }

            if(!matchesJunctionPosition)
                return sequenceLengthMatch ? ALT_LOC_MATCH : NO_MATCH;

            int refBaseOverlap = min(alignment.RefLocation.end(), assemblyPosEnd) - max(alignment.RefLocation.start(), assemblyPosStart);
            double refBaseOverlapPerc = refBaseOverlap / (double)assemblyRefLength;

            return refBaseOverlapPerc >= ALIGN_MATCH_PERC ? MATCH : PARTIAL;
        }
    }
}
