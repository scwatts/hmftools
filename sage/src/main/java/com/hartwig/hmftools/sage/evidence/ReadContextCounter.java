package com.hartwig.hmftools.sage.evidence;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.lang.String.format;

import static com.hartwig.hmftools.common.region.BaseRegion.positionWithin;
import static com.hartwig.hmftools.common.samtools.CigarUtils.leftSoftClipLength;
import static com.hartwig.hmftools.common.samtools.CigarUtils.rightSoftClipLength;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.CONSENSUS_INFO_DELIM;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.CONSENSUS_READ_ATTRIBUTE;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.UMI_TYPE_ATTRIBUTE;
import static com.hartwig.hmftools.common.region.BaseRegion.positionsOverlap;
import static com.hartwig.hmftools.common.samtools.UmiReadType.DUAL_STRAND_OLD;
import static com.hartwig.hmftools.common.variant.SageVcfTags.UMI_TYPE_COUNT;
import static com.hartwig.hmftools.common.variant.VariantReadSupport.CORE;
import static com.hartwig.hmftools.common.variant.VariantReadSupport.FULL;
import static com.hartwig.hmftools.common.variant.VariantReadSupport.PARTIAL;
import static com.hartwig.hmftools.common.variant.VariantReadSupport.REALIGNED;
import static com.hartwig.hmftools.common.variant.VariantReadSupport.OTHER_ALT;
import static com.hartwig.hmftools.common.variant.VariantReadSupport.REF;
import static com.hartwig.hmftools.sage.SageCommon.SG_LOGGER;
import static com.hartwig.hmftools.sage.SageConstants.CORE_LOW_QUAL_MISMATCH_BASE_LENGTH;
import static com.hartwig.hmftools.sage.SageConstants.EVIDENCE_MIN_MAP_QUAL;
import static com.hartwig.hmftools.sage.SageConstants.MQ_RATIO_SMOOTHING;
import static com.hartwig.hmftools.sage.SageConstants.REALIGN_READ_CONTEXT_MIN_SEARCH_BUFFER;
import static com.hartwig.hmftools.sage.SageConstants.REALIGN_READ_CONTEXT_MIN_SEARCH_LENGTH;
import static com.hartwig.hmftools.sage.SageConstants.REALIGN_READ_MIN_INDEL_LENGTH;
import static com.hartwig.hmftools.sage.SageConstants.SC_READ_EVENTS_FACTOR;
import static com.hartwig.hmftools.sage.candidate.RefContextConsumer.ignoreSoftClipAdapter;
import static com.hartwig.hmftools.sage.common.ReadContextMatch.NONE;
import static com.hartwig.hmftools.sage.sync.FragmentSyncOutcome.ORIG_READ_COORDS;
import static com.hartwig.hmftools.sage.evidence.ReadMatchType.NO_SUPPORT;
import static com.hartwig.hmftools.sage.evidence.ReadMatchType.SUPPORT;
import static com.hartwig.hmftools.sage.evidence.ReadMatchType.UNRELATED;
import static com.hartwig.hmftools.sage.evidence.RealignedType.CORE_PARTIAL;
import static com.hartwig.hmftools.sage.evidence.RealignedType.EXACT;
import static com.hartwig.hmftools.sage.evidence.RealignedType.LENGTHENED;
import static com.hartwig.hmftools.sage.evidence.RealignedType.SHORTENED;
import static com.hartwig.hmftools.sage.filter.ReadFilters.isChimericRead;
import static com.hartwig.hmftools.sage.quality.QualityCalculator.isImproperPair;

import static htsjdk.samtools.CigarOperator.D;
import static htsjdk.samtools.CigarOperator.I;
import static htsjdk.samtools.CigarOperator.M;
import static htsjdk.samtools.CigarOperator.S;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.samtools.UmiReadType;
import com.hartwig.hmftools.common.variant.VariantReadSupport;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.sage.SageConfig;
import com.hartwig.hmftools.sage.quality.QualityCalculator;
import com.hartwig.hmftools.sage.read.SplitReadUtils;
import com.hartwig.hmftools.sage.read.NumberEvents;
import com.hartwig.hmftools.sage.common.ReadContext;
import com.hartwig.hmftools.sage.common.ReadContextMatch;
import com.hartwig.hmftools.sage.common.VariantTier;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;

public class ReadContextCounter implements VariantHotspot
{
    private final int mId;
    private final VariantTier mTier;
    private final VariantHotspot mVariant;
    private final ReadContext mReadContext;
    private final SageConfig mConfig;
    private final QualityCalculator mQualityCalculator;
    private final String mSample;
    private final int mMaxCoverage;

    // local variant-related state
    private final int mMinNumberOfEvents;
    private final boolean mIsMnv;
    private final boolean mIsSnv;
    private final boolean mIsIndel;
    private final boolean mAllowWildcardMatchInCore;
    private final int mMaxCoreMismatches;
    private final int mAdjustedVariantPosition;

    // counts of various
    private final ReadSupportCounts mQualities;
    private final ReadSupportCounts mCounts;

    private final StrandBiasData mFragmentStrandBias;
    private final StrandBiasData mReadStrandBias;

    private final JitterData mJitterData;
    private int mImproperPairCount;

    private int mRawDepth;
    private int mRawAltSupport;
    private int mRawRefSupport;
    private int mRawAltBaseQuality;
    private int mRawRefBaseQuality;
    private double mSupportAltBaseQualityTotal;

    private long mTotalMapQuality;
    private long mTotalAltMapQuality;
    private int mTotalNmCount;
    private int mTotalAltNmCount;

    private int mSoftClipInsertSupport;
    private int mMaxCandidateDeleteLength;
    private int mMaxDistanceFromEdge;

    private List<Integer> mLocalPhaseSets;
    private List<int[]> mLpsCounts;
    private int[] mUmiTypeCounts;
    private FragmentLengthData mFragmentLengthData;

    public ReadContextCounter(
            final int id, final VariantHotspot variant, final ReadContext readContext, final VariantTier tier,
            final int maxCoverage, final int minNumberOfEvents, final SageConfig config, final QualityCalculator qualityCalculator,
            final String sampleId)
    {
        mId = id;

        mTier = tier;
        mMaxCoverage = maxCoverage;
        mMinNumberOfEvents = minNumberOfEvents;
        mSample = sampleId;
        mQualityCalculator = qualityCalculator;
        mConfig = config;

        mReadContext = readContext;
        mVariant = variant;

        // set local state to avoid testing on each read
        mIsMnv = variant.isMNV();
        mIsSnv = variant.isSNV();
        mIsIndel = variant.isIndel();

        mAllowWildcardMatchInCore = mVariant.isSNV() && mReadContext.microhomology().isEmpty();

        mMaxCoreMismatches = mVariant.isIndel() && mVariant.alt().length() >= CORE_LOW_QUAL_MISMATCH_BASE_LENGTH ?
                mVariant.alt().length() / CORE_LOW_QUAL_MISMATCH_BASE_LENGTH : 0;

        mAdjustedVariantPosition = mVariant.isIndel() ? mVariant.position() + indelLength() / 2 : mVariant.position();

        mQualities = new ReadSupportCounts();
        mCounts = new ReadSupportCounts();

        mJitterData = new JitterData();

        mFragmentStrandBias = new StrandBiasData();
        mReadStrandBias = new StrandBiasData();

        mImproperPairCount = 0;

        mRawDepth = 0;
        mRawAltSupport = 0;
        mRawRefSupport = 0;
        mRawAltBaseQuality = 0;
        mRawRefBaseQuality = 0;
        mSupportAltBaseQualityTotal = 0;
        mSoftClipInsertSupport = 0;
        mMaxCandidateDeleteLength = 0;
        mTotalMapQuality = 0;
        mTotalAltMapQuality = 0;
        mTotalNmCount = 0;
        mTotalAltNmCount = 0;
        mMaxDistanceFromEdge = 0;

        mLocalPhaseSets = null;
        mLpsCounts = null;
        mUmiTypeCounts = null;
        mFragmentLengthData = mConfig.WriteFragmentLengths ? new FragmentLengthData() : null;
    }

    public int id() { return mId; }
    public VariantHotspot variant() { return mVariant; }
    public ReadContext readContext() { return mReadContext; }
    public VariantTier tier() { return mTier; }
    public int indelLength() { return mVariant.isIndel() ? max(mVariant.alt().length(), mVariant.ref().length()) : 0; }
    public boolean isSnv() { return mIsSnv; }
    public boolean isMnv() { return mIsMnv; }
    public boolean isIndel() { return mIsIndel; }

    @Override
    public String chromosome() { return mVariant.chromosome(); }

    @Override
    public int position() { return mVariant.position(); }

    @Override
    public String ref() { return mVariant.ref(); }

    @Override
    public String alt() { return mVariant.alt(); }

    public int altSupport() { return mCounts.altSupport(); }
    public int refSupport() { return mCounts.Ref; }
    public int depth() { return mCounts.Total; }

    public double vaf()
    {
        return mCounts.Total == 0 ? 0d : mCounts.altSupport() / (double)mCounts.Total;
    }

    public int tumorQuality()
    {
        int tumorQuality = mQualities.Full + mQualities.Partial + mQualities.Realigned;
        return Math.max(0, tumorQuality - mJitterData.penalty());
    }

    public int[] counts() { return mCounts.toArray(); }
    public int[] quality() { return mQualities.toArray(); }

    public int[] jitter()
    {
        return mJitterData.summary();
    }

    public StrandBiasData fragmentStrandBias() { return mFragmentStrandBias; }
    public StrandBiasData readStrandBias() { return mReadStrandBias; }
    public int improperPairCount() { return mImproperPairCount; }

    public int rawDepth() { return mRawDepth; }
    public int rawAltSupport() { return mRawAltSupport; }
    public int rawRefSupport() { return mRawRefSupport; }
    public int rawAltBaseQuality() { return mRawAltBaseQuality; }
    public int rawRefBaseQuality() { return mRawRefBaseQuality; }

    public long totalMapQuality() { return mTotalMapQuality; }
    public long altMapQuality() { return mTotalAltMapQuality; }

    public long totalNmCount() { return mTotalNmCount; }
    public long altNmCount() { return mTotalAltNmCount; }
    public int maxDistanceFromEdge() { return mMaxDistanceFromEdge; }
    public int minNumberOfEvents() { return mMinNumberOfEvents; }

    public double averageAltBaseQuality()
    {
        // excludes realigned
        int supportCount = mCounts.Full + mCounts.Partial + mCounts.Core;
        return supportCount > 0 ? mSupportAltBaseQualityTotal / (double)supportCount : 0;
    }

    public int softClipInsertSupport() { return mSoftClipInsertSupport; }

    public void setMaxCandidateDeleteLength(int length) { mMaxCandidateDeleteLength = length; }
    public int maxCandidateDeleteLength() { return mMaxCandidateDeleteLength; }

    public List<Integer> localPhaseSets() { return mLocalPhaseSets; }
    public List<int[]> lpsCounts() { return mLpsCounts; }

    public int[] umiTypeCounts() { return mUmiTypeCounts; }
    public FragmentLengthData fragmentLengths() { return mFragmentLengthData; }

    public boolean exceedsMaxCoverage() { return mCounts.Total >= mMaxCoverage; }

    public String toString()
    {
        return format("id(%d) var(%s) core(%s) counts(f=%d p=%d c=%d)",
                mId, varString(), mReadContext.toString(), mCounts.Full, mCounts.Partial, mCounts.Core);
    }

    public String varString()
    {
        return format("%s:%d %s>%s", mVariant.chromosome(), mVariant.position(), mVariant.ref(), mVariant.alt());
    }

    private enum MatchType
    {
        NONE,
        FULL,
        PARTIAL,
        CORE,
        REALIGNED,
        CORE_PARTIAL,REF,
        ALT;
    }

    public ReadMatchType processRead(final SAMRecord record, int numberOfEvents)
    {
        if(exceedsMaxCoverage())
            return UNRELATED;

        if(!mTier.equals(VariantTier.HOTSPOT))
        {
            if(mConfig.Quality.MapQualityRatioFactor == 0 && record.getMappingQuality() < EVIDENCE_MIN_MAP_QUAL)
                return UNRELATED;
        }

        RawContext rawContext = RawContext.create(mVariant, record);

        if(mConfig.Quality.HighBaseQualLimit > 0)
        {
            if(isChimericRead(record))
                return UNRELATED;

            if(rawContext.ReadIndexInSoftClip)
                return UNRELATED;
        }

        if(rawContext.ReadIndex < 0 && !ignoreSoftClipAdapter(record))
        {
            if(rawContext.DepthSupport || rawContext.AltSupport || rawContext.RefSupport)
            {
                SG_LOGGER.error("rawContext missing readIndex but with support(depth={} ref={} alt={}",
                        rawContext.DepthSupport, rawContext.AltSupport, rawContext.RefSupport);
            }

            // search for a core match within soft-clipped bases, checking if a proixmate DEL may explain the soft-clipping
            rawContext = createRawContextFromCoreMatch(record);

            if(rawContext.ReadIndex < 0)
                return UNRELATED;
        }

        if(rawContext.ReadIndexInSkipped)
            return UNRELATED;

        int readIndex = rawContext.ReadIndex;
        boolean baseDeleted = rawContext.ReadIndexInDelete;

        mRawDepth += rawContext.DepthSupport ? 1 : 0;

        if(rawContext.ReadIndexInSoftClip && rawContext.AltSupport)
            ++mSoftClipInsertSupport;

        boolean covered = mReadContext.indexedBases().isCoreCovered(readIndex, record.getReadBases().length);

        if(!covered)
        {
            registerRawSupport(rawContext);
            return UNRELATED;
        }

        double adjustedNumOfEvents = numberOfEvents;

        if(mIsMnv)
            adjustedNumOfEvents = NumberEvents.calcWithMnvRaw(numberOfEvents, mVariant.ref(), mVariant.alt());

        if(max(mVariant.ref().length(), mVariant.alt().length()) <= SC_READ_EVENTS_FACTOR)
        {
            // penalise variants except long INDELs for their soft-clipped bases
            adjustedNumOfEvents += NumberEvents.calcSoftClipAdjustment(record);
        }

        adjustedNumOfEvents = max(mMinNumberOfEvents, adjustedNumOfEvents);

        double rawBaseQuality = mQualityCalculator.rawBaseQuality(this, readIndex, record);

        if(mConfig.Quality.HighBaseQualLimit > 0 && rawBaseQuality < mConfig.Quality.HighBaseQualLimit)
        {
            if(rawContext.AltSupport)
                countStrandedness(record);

            return UNRELATED;
        }

        double quality = mQualityCalculator.calculateQualityScore(
                this, readIndex, record, adjustedNumOfEvents, rawBaseQuality);

        MatchType matchType = MatchType.NONE;

        // Check if FULL, PARTIAL, OR CORE
        if(!baseDeleted)
        {
            final ReadContextMatch match = determineReadContextMatch(record, readIndex, true);

            if(match != NONE && match != ReadContextMatch.CORE_PARTIAL)
            {
                VariantReadSupport readSupport = null;

                switch(match)
                {
                    case FULL:
                        readSupport = FULL;
                        matchType = MatchType.FULL;
                        break;

                    case PARTIAL:
                        readSupport = PARTIAL;
                        matchType = MatchType.PARTIAL;
                        break;

                    case CORE:
                        readSupport = CORE;
                        matchType = MatchType.CORE;
                        break;
                }

                registerReadSupport(record, readSupport, quality);

                mTotalMapQuality += record.getMappingQuality();
                mTotalAltMapQuality += record.getMappingQuality();
                mTotalNmCount += numberOfEvents;
                mTotalAltNmCount += numberOfEvents;

                mSupportAltBaseQualityTotal += rawBaseQuality;

                registerRawSupport(rawContext);

                if(rawContext.AltSupport)
                    updateDistanceFromReadEdge(record);

                logReadEvidence(record, matchType, readIndex, quality);

                /*
                if(SG_LOGGER.isTraceEnabled() && sampleId != null)
                {
                    qualityCalc.logReadQualCalcs(this, readIndex, record, adjustedNumOfEvents);
                }
                */

                countStrandedness(record);

                checkImproperCount(record);
                return SUPPORT;
            }
            else if(match == ReadContextMatch.CORE_PARTIAL)
            {
                // if the core is partly overlapped then back out any attribution to a ref match
                rawContext.updateSupport(false, rawContext.AltSupport);
            }
        }

        boolean canRealign = abs(mVariant.indelLength()) >= REALIGN_READ_MIN_INDEL_LENGTH || readHasIndelInCore(record);
        RealignedContext realignment = canRealign ? checkRealignment(record) : RealignedContext.NONE;

        if(realignment.Type == EXACT)
        {
            registerReadSupport(record, REALIGNED, quality);

            mTotalMapQuality += record.getMappingQuality();
            mTotalAltMapQuality += record.getMappingQuality();
            mTotalNmCount += numberOfEvents;
            mTotalAltNmCount += numberOfEvents;

            logReadEvidence(record, MatchType.REALIGNED, readIndex,quality);
            rawContext.updateSupport(false, rawContext.AltSupport);
            registerRawSupport(rawContext);

            return SUPPORT;
        }
        else if(realignment.Type == CORE_PARTIAL)
        {
            matchType = MatchType.CORE_PARTIAL;
            rawContext.updateSupport(false, false);
        }

        registerRawSupport(rawContext);

        // switch back to the old method to test for jitter
        RealignedContext jitterRealign = Realignment.realignedAroundIndex(mReadContext, readIndex, record.getReadBases(), getMaxRealignDistance(record));

        if(rawContext.ReadIndexInSoftClip && !rawContext.AltSupport)
        {
            if(jitterRealign.Type != LENGTHENED && jitterRealign.Type != SHORTENED)
                return UNRELATED;
        }

        ReadMatchType readMatchType = UNRELATED;

        mTotalMapQuality += record.getMappingQuality();
        mTotalNmCount += numberOfEvents;

        VariantReadSupport readSupport = null;

        if(rawContext.RefSupport)
        {
            readSupport = REF;
            readMatchType = NO_SUPPORT;
        }
        else if(rawContext.AltSupport)
        {
            readSupport = OTHER_ALT;

            mTotalAltMapQuality += record.getMappingQuality();
            mTotalAltNmCount += numberOfEvents;

            countStrandedness(record);
        }

        registerReadSupport(record, readSupport, quality);

        // add to jitter penalty as a function of the number of repeats found
        mJitterData.update(jitterRealign, mConfig.Quality);

        if(mConfig.LogEvidenceReads)
        {
            if(rawContext.RefSupport)
                matchType = MatchType.REF;
            else if(rawContext.AltSupport)
                matchType = MatchType.ALT;

            logReadEvidence(record, matchType, readIndex, quality);
        }

        return readMatchType;
    }

    private ReadContextMatch determineReadContextMatch(final SAMRecord record, int readIndex, boolean allowCoreVariation)
    {
        ReadIndexBases readIndexBases;

        if(record.getCigar().containsOperator(CigarOperator.N))
        {
            readIndexBases = SplitReadUtils.expandSplitRead(readIndex, record);
        }
        else
        {
            readIndexBases = new ReadIndexBases(readIndex, record.getReadBases());
        }

        final ReadContextMatch match = mReadContext.indexedBases().matchAtPosition(
                readIndexBases, record.getBaseQualities(),
                allowCoreVariation ? mAllowWildcardMatchInCore : false,
                allowCoreVariation ? mMaxCoreMismatches : 0);

        return match;
    }

    private void registerReadSupport(final SAMRecord record, @Nullable final VariantReadSupport support, final double quality)
    {
        mCounts.addSupport(support, 1);
        mQualities.addSupport(support, (int)quality);

        boolean supportsVariant = support != null
                && (support == FULL || support == PARTIAL || support == CORE || support == REALIGNED);

        if(mConfig.TrackUMIs)
        {
            countUmiType(record, supportsVariant);
        }

        if(mFragmentLengthData != null && (support == REF || supportsVariant))
        {
            mFragmentLengthData.addLength(abs(record.getInferredInsertSize()), supportsVariant);
        }
    }

    private void countUmiType(final SAMRecord record, final boolean supportsVariant)
    {
        if(mUmiTypeCounts == null)
        {
            // 3 total depth values followed by the 3 variant support values
            mUmiTypeCounts = new int[UMI_TYPE_COUNT];
        }

        UmiReadType umiReadType = UmiReadType.NONE;

        if(record.hasAttribute(UMI_TYPE_ATTRIBUTE))
        {
            String umiType = record.getStringAttribute(UMI_TYPE_ATTRIBUTE);

            if(umiType != null)
                umiReadType = UmiReadType.valueOf(umiType);
        }
        else
        {
            // to be deprecated since have return to using UMI type attribute above
            String consensusInfo = record.getStringAttribute(CONSENSUS_READ_ATTRIBUTE);

            if(consensusInfo != null && consensusInfo.contains(CONSENSUS_INFO_DELIM))
            {
                String[] values = consensusInfo.split(CONSENSUS_INFO_DELIM, 3);

                if(values.length == 3)
                    umiReadType = values[2].equals(DUAL_STRAND_OLD) ? UmiReadType.DUAL : UmiReadType.valueOf(values[2]);
            }
        }

        // add to total and variant support if applicable
        ++mUmiTypeCounts[umiReadType.ordinal()];

        if(supportsVariant)
            ++mUmiTypeCounts[umiReadType.ordinal() + 3];
    }

    private void registerRawSupport(final RawContext rawContext)
    {
        if(rawContext.AltSupport)
        {
            ++mRawAltSupport;
            mRawAltBaseQuality += rawContext.BaseQuality;
        }
        else if(rawContext.RefSupport)
        {
            ++mRawRefSupport;
            mRawRefBaseQuality += rawContext.BaseQuality;
        }
    }

    private void logReadEvidence(final SAMRecord record, final MatchType matchType, int readIndex, double quality)
    {
        if(!mConfig.LogEvidenceReads || !SG_LOGGER.isTraceEnabled())
            return;

        // mQualityCalculator.logReadQualCalcs(this, readIndex, record, adjustedNumOfEvents);

        // Variant,MatchType,ReadId,ReadStart,Cigar,LeftCore,Index,RightCore,ReadIndex,Quality
        SG_LOGGER.trace("READ_EV,{},{},{},{},{},{},{},{},{},{},{},{},{},{}",
                mSample, chromosome(), position(), ref(), alt(),
                matchType, record.getReadName(), record.getAlignmentStart(), record.getCigarString(),
                mReadContext.indexedBases().LeftCoreIndex, mReadContext.indexedBases().Index,
                mReadContext.indexedBases().RightCoreIndex, readIndex, format("%.1f", quality));
    }

    private RawContext createRawContextFromCoreMatch(final SAMRecord record)
    {
        // check for an exact core match by which to centre the read
        if(mMaxCandidateDeleteLength < 5)
            return RawContext.INVALID_CONTEXT;

        int scLenLeft = leftSoftClipLength(record);
        int scLenRight = rightSoftClipLength(record);

        if(max(scLenLeft, scLenRight) < 5)
            return RawContext.INVALID_CONTEXT;

        final String variantCore = mReadContext.coreString();
        int coreStartIndex = record.getReadString().indexOf(variantCore);
        if(coreStartIndex < 1)
            return RawContext.INVALID_CONTEXT;

        int coreEndIndex = coreStartIndex + variantCore.length() - 1;
        boolean isValidRead = false;
        int baseQuality = 0;

        if(scLenLeft > scLenRight)
        {
            // the core match must span from the left soft-clipping into the matched bases
            int readRightCorePosition = record.getReferencePositionAtReadPosition(coreEndIndex + 1);

            if(readRightCorePosition > mVariant.position() && coreStartIndex < scLenLeft)
            {
                isValidRead = true;
                baseQuality = record.getBaseQualities()[scLenLeft];
            }
        }
        else
        {
            // the core match must span from the matched bases into the right soft-clipping
            int readLeftCorePosition = record.getReferencePositionAtReadPosition(coreStartIndex + 1);
            int postCoreIndexDiff = record.getReadBases().length - coreEndIndex;

            if(readLeftCorePosition > 0 && readLeftCorePosition < mVariant.position() && postCoreIndexDiff < scLenRight)
            {
                isValidRead = true;
                baseQuality = record.getBaseQualities()[record.getReadBases().length - scLenRight];
            }
        }

        if(!isValidRead)
            return RawContext.INVALID_CONTEXT;

        int readIndex = coreStartIndex + mReadContext.indexedBases().Index - mReadContext.indexedBases().LeftCoreIndex;

        return new RawContext(
                readIndex, false, false, true,
                true, false, true, baseQuality);
    }

    private boolean readHasIndelInCore(final SAMRecord record)
    {
        if(!record.getCigar().containsOperator(D) && !record.getCigar().containsOperator(I))
            return false;

        int variantLeftCorePos = mVariant.position() - (mReadContext.indexedBases().Index - mReadContext.indexedBases().LeftCoreIndex);
        int variantRightCorePos = mVariant.position() + (mReadContext.indexedBases().RightCoreIndex - mReadContext.indexedBases().Index);

        int currentPos = record.getAlignmentStart() - 1;

        // eg 2S10M2D10M starting at 100: first non-SC element, in this case a delete, starts at 109
        for(CigarElement element : record.getCigar())
        {
            if(element.getOperator() == S)
                continue;

            if(element.getOperator() == I || element.getOperator() == D)
            {
                int indelLowerPos = currentPos;
                int indelUpperPos = indelLowerPos + (element.getOperator() == D ? element.getLength() : 1);

                if(positionsOverlap(variantLeftCorePos, variantRightCorePos, indelLowerPos, indelUpperPos))
                    return true;
            }
            else if(element.getOperator() == M)
            {
                currentPos += element.getLength();
            }
        }

        return false;
    }

    private int calcLeftAlignmentIndex(final SAMRecord record)
    {
        // Left alignment: Match full read context starting at base = pos - rc_index
        int leftCoreOffset = mReadContext.indexedBases().Index - mReadContext.indexedBases().LeftCoreIndex;
        int realignLeftCorePos = position() - leftCoreOffset;
        int realignLeftCoreIndex = record.getReadPositionAtReferencePosition(realignLeftCorePos);

        if(realignLeftCoreIndex > 0)
        {
            int realignLeftReadIndex = realignLeftCoreIndex - 1 + leftCoreOffset;
            return realignLeftReadIndex;
        }

        int deleteCount = (int)record.getCigar().getCigarElements().stream().filter(x -> x.getOperator() == D).count();
        if(deleteCount == 0)
            return -1;

        int deleteStartPos = record.getAlignmentStart();
        int deleteStartIndex = 0;
        for(CigarElement element : record.getCigar())
        {
            if(element.getOperator() == S || element.getOperator() == I)
                continue;

            if(element.getOperator() == M)
            {
                deleteStartPos += element.getLength();
                deleteStartIndex += element.getLength();
            }
            else if(element.getOperator() == D)
            {
                --deleteCount;

                if(deleteCount == 0)
                    break;

                deleteStartPos += element.getLength();
            }
        }

        --deleteStartPos;

        int posDiff = realignLeftCorePos - deleteStartPos;
        int realignLeftReadIndex = deleteStartIndex + posDiff - 1 + leftCoreOffset;
        return realignLeftReadIndex;
    }

    private int calcRightAlignmentIndex(final SAMRecord record)
    {
        // Right alignment: Match full read context ending at base = pos + length[RC} - rc_index - 1 - length(alt) + length(ref)
        int rightCoreOffset = mReadContext.indexedBases().RightCoreIndex - mReadContext.indexedBases().Index;
        int realignRightPos = position() + rightCoreOffset - mVariant.alt().length() + mVariant.ref().length();
        int realignRightCoreIndex = record.getReadPositionAtReferencePosition(realignRightPos);

        if(realignRightCoreIndex > 0)
        {
            int realignRightReadIndex = realignRightCoreIndex - 1 - rightCoreOffset;
            return realignRightReadIndex;
        }

        return -1;
    }

    private RealignedContext checkRealignment(final SAMRecord record)
    {
        // try left and right alignment in turn
        int realignLeftReadIndex = calcLeftAlignmentIndex(record);

        if(realignLeftReadIndex >= 0) //  && realignLeftReadIndex != readIndex
        {
            ReadContextMatch match = determineReadContextMatch(record, realignLeftReadIndex, false);

            if(match == ReadContextMatch.FULL || match == ReadContextMatch.PARTIAL)
                return new RealignedContext(EXACT, mReadContext.indexedBases().length(), realignLeftReadIndex);
        }

        int realignRightReadIndex = calcRightAlignmentIndex(record);

        if(realignRightReadIndex >= 0)
        {
            // still need to test even if this index matches the original readIndex since if the readIndex was in a delete
            // it will be have skipped above
            ReadContextMatch match = determineReadContextMatch(record, realignRightReadIndex, false);

            if(match == ReadContextMatch.FULL || match == ReadContextMatch.PARTIAL)
                return new RealignedContext(RealignedType.EXACT, mReadContext.indexedBases().length(), realignRightReadIndex);
            else if(match == ReadContextMatch.CORE_PARTIAL)
                return new RealignedContext(RealignedType.CORE_PARTIAL, mReadContext.indexedBases().length(), realignRightReadIndex);
        }

        // try a simple string search and take it as exact if the matched index is within the expected range
        String readContext = mReadContext.indexedBases().fullString();
        if(readContext.length() >= REALIGN_READ_CONTEXT_MIN_SEARCH_LENGTH)
        {
            int matchedReadIndex = record.getReadString().indexOf(readContext);

            if(matchedReadIndex >= 0)
            {
                int matchedIndex = matchedReadIndex + mReadContext.indexedBases().Index - mReadContext.indexedBases().LeftFlankIndex;
                if(abs(matchedIndex - realignLeftReadIndex) <= REALIGN_READ_CONTEXT_MIN_SEARCH_BUFFER
                || abs(matchedIndex - realignRightReadIndex) <= REALIGN_READ_CONTEXT_MIN_SEARCH_BUFFER)
                {
                    return new RealignedContext(RealignedType.EXACT, mReadContext.indexedBases().length(), matchedIndex);
                }
            }
        }

        return RealignedContext.NONE;
    }

    public void addLocalPhaseSet(int lps, int readCount, double allocCount)
    {
        if(mLocalPhaseSets == null)
        {
            mLocalPhaseSets = Lists.newArrayList();
            mLpsCounts = Lists.newArrayList();
        }

        // add in order of highest counts
        int index = 0;
        while(index < mLpsCounts.size())
        {
            final int[] existingCounts = mLpsCounts.get(index);
            if(readCount + allocCount > existingCounts[0] + existingCounts[0])
                break;

            ++index;
        }

        mLocalPhaseSets.add(index, lps);
        mLpsCounts.add(index, new int[] { readCount, (int)allocCount } );
    }

    private void countStrandedness(final SAMRecord record)
    {
        boolean readIsForward = !record.getReadNegativeStrandFlag();

        mReadStrandBias.add(readIsForward);

        if(!record.getReadPairedFlag())
        {
            mFragmentStrandBias.add(readIsForward);
            return;
        }

        // make the distinction between F1R2 and F2R1
        boolean firstIsForward = record.getFirstOfPairFlag() ? readIsForward : !record.getMateNegativeStrandFlag();
        boolean secondIsForward = !record.getFirstOfPairFlag() ? readIsForward : !record.getMateNegativeStrandFlag();

        if(firstIsForward != secondIsForward)
        {
            mFragmentStrandBias.add(firstIsForward);
        }
    }

    private void updateDistanceFromReadEdge(final SAMRecord record)
    {
        if(mMaxDistanceFromEdge >= record.getReadBases().length / 2)
            return;

        if(record.getCigar().containsOperator(CigarOperator.N)) // unnecessary in append mode
            return;

        // determine how far from the edge of the read the variant is, ignoring soft-clip bases and realigned reads
        // and for INDELs use the position of the middle base of INDEL
        // take the lower of the 2 distances for each read, eg 50 and 100 bases, then take 50
        int distFromStart = -1;
        int distFromEnd = -1;

        if(record.hasAttribute(ORIG_READ_COORDS))
        {
            String[] originalCoords = record.getStringAttribute(ORIG_READ_COORDS).split(";", 4);

            int firstPosStart = Integer.parseInt(originalCoords[0]);
            int firstPosEnd = Integer.parseInt(originalCoords[1]);
            int secondPosStart = Integer.parseInt(originalCoords[2]);
            int secondPosEnd = Integer.parseInt(originalCoords[3]);

            if(positionWithin(mAdjustedVariantPosition, firstPosStart, firstPosEnd))
            {
                distFromStart = mAdjustedVariantPosition - firstPosStart;
                distFromEnd = firstPosEnd - mAdjustedVariantPosition;
            }

            if(positionWithin(mAdjustedVariantPosition, secondPosStart, secondPosEnd))
            {
                if(distFromStart == -1 || mAdjustedVariantPosition - secondPosStart < distFromStart)
                    distFromStart = mAdjustedVariantPosition - secondPosStart;

                if(distFromEnd == -1 || secondPosEnd - mAdjustedVariantPosition < distFromEnd)
                    distFromEnd = secondPosEnd - mAdjustedVariantPosition;
            }
        }
        else
        {
            distFromStart = mAdjustedVariantPosition - record.getAlignmentStart();
            distFromEnd = record.getAlignmentEnd() - mAdjustedVariantPosition;
        }

        int minDistance = min(distFromStart, distFromEnd);

        mMaxDistanceFromEdge = max(minDistance, mMaxDistanceFromEdge);
    }

    private int getMaxRealignDistance(final SAMRecord record)
    {
        int index = mReadContext.readBasesPositionIndex();
        int leftIndex = mReadContext.readBasesLeftCentreIndex();
        int rightIndex = mReadContext.readBasesRightCentreIndex();

        int leftOffset = index - leftIndex;
        int rightOffset = rightIndex - index;

        int indelLength = record.getCigar().getCigarElements().stream()
                .filter(x -> x.getOperator() == I || x.getOperator() == D).mapToInt(x -> x.getLength()).sum();

        return Math.max(indelLength + Math.max(leftOffset, rightOffset), Realignment.MAX_REPEAT_SIZE) + 1;
    }

    private void checkImproperCount(final SAMRecord record)
    {
        if(isImproperPair(record) || record.getSupplementaryAlignmentFlag())
        {
            mImproperPairCount++;
        }
    }

    public void applyMapQualityRatio()
    {
        int depth = depth();
        int avgTotalMapQuality = depth > 0 ? (int)round(totalMapQuality() / (double)depth) : 0;

        if(avgTotalMapQuality == 0)
            return;

        int altSupport = altSupport();
        int avgAltMapQuality = altSupport > 0 ? (int)round(altMapQuality() / (double)altSupport) : 0;

        double ratioRaw = (avgAltMapQuality + MQ_RATIO_SMOOTHING) / (avgTotalMapQuality + MQ_RATIO_SMOOTHING);
        double calcRatio = pow(min(1, ratioRaw), mConfig.Quality.MapQualityRatioFactor);

        mQualities.applyRatio(calcRatio);
    }

    public boolean logEvidence() { return mConfig.LogEvidenceReads; }

    @VisibleForTesting
    public ReadSupportCounts readSupportQualityCounts() { return mQualities; };
    public ReadSupportCounts readSupportCounts() { return mCounts; }
}
