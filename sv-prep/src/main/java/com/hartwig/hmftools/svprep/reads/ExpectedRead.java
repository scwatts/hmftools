package com.hartwig.hmftools.svprep.reads;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.samtools.SupplementaryReadData;

public class ExpectedRead
{
    public final String Chromosome;
    public final int Position;
    public final boolean FirstInPair;
    public final boolean IsSupplementary;

    private int mExpectedMatchCount;
    private int mMatchCount;
    private boolean mFound;

    private ReadRecord mCachedRead;
    private boolean mRemoteCandidateGroup;

    public ExpectedRead(final String chromosome, final int position, boolean firstInPair, boolean isSupplementary, boolean found)
    {
        Chromosome = chromosome;
        Position = position;
        FirstInPair = firstInPair;
        IsSupplementary = isSupplementary;
        mFound = found;
        mExpectedMatchCount = 1;
        mMatchCount = 0;
        mCachedRead = null;
        mRemoteCandidateGroup = false;
    }

    public boolean fullyMatched() { return mMatchCount >= mExpectedMatchCount; }
    public void registerMatch() { ++mMatchCount; }

    public boolean found() { return mFound; }
    public void markFound() { mFound = true; }

    public void setExpectedMatchCount(int count)
    {
        if(IsSupplementary)
            mExpectedMatchCount = min(count, 2); // supplementaries only come through on their partner
        else
            mExpectedMatchCount = max(count,mExpectedMatchCount);
    }

    public void setCachedRead(final ReadRecord read) { mCachedRead = read; }
    public boolean hasCachedRead() { return mCachedRead != null; }
    public ReadRecord getCachedRead() { return mCachedRead; }

    public boolean remoteCandidateGroup() { return mRemoteCandidateGroup; }
    public void markRemoteCandidateGroup() { mRemoteCandidateGroup = true; }

    public boolean matches(final ExpectedRead other)
    {
        return Chromosome.equals(other.Chromosome) && Position == other.Position && FirstInPair == other.FirstInPair
                && IsSupplementary == other.IsSupplementary;
    }

    public String toString()
    {
        return format("%s:%d first(%s) supp(%s) found(%s) matched(%d/%d)",
                Chromosome, Position, FirstInPair, IsSupplementary, mFound, mMatchCount, mExpectedMatchCount);
    }

    public static ExpectedRead fromRead(final ReadRecord read)
    {
        return new ExpectedRead(read.Chromosome, read.start(), read.isFirstOfPair(), read.isSupplementaryAlignment(), true);
    }

    public static List<ExpectedRead> getExpectedReads(final ReadGroup readGroup)
    {
        List<ExpectedRead> expectedReads = Lists.newArrayList();

        for(ReadRecord read : readGroup.reads())
        {
            if(read.hasSuppAlignment())
            {
                SupplementaryReadData suppData = read.supplementaryAlignment();

                if(!readGroup.hasSupplementaryMatch(suppData))
                {
                    expectedReads.add(new ExpectedRead(
                            suppData.Chromosome, suppData.Position, read.isFirstOfPair(), !read.isSupplementaryAlignment(), false));
                }
            }

            if(HumanChromosome.contains(read.MateChromosome) && !readGroup.hasReadMate(read))
            {
                expectedReads.add(new ExpectedRead(
                        read.MateChromosome, read.MatePosStart, !read.isFirstOfPair(), false, false));
            }
        }

        expectedReads.forEach(x -> x.setExpectedMatchCount(readGroup.partitionCount() - 1));
        return expectedReads;
    }
}
