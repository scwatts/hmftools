package com.hartwig.hmftools.cobalt.diploid;

import static com.hartwig.hmftools.cobalt.CobaltConfig.CB_LOGGER;
import static com.hartwig.hmftools.cobalt.CobaltConstants.WINDOW_SIZE;

import static htsjdk.tribble.AbstractFeatureReader.getFeatureReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import com.hartwig.hmftools.cobalt.Chromosome;
import com.hartwig.hmftools.cobalt.ChromosomePositionCodec;
import com.hartwig.hmftools.cobalt.CobaltColumns;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.util.Locatable;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.readers.LineIterator;

import tech.tablesaw.api.*;

public class DiploidRatioLoader implements Consumer<Locatable>
{
    private final Table mResult = Table.create(
            StringColumn.create(CobaltColumns.CHROMOSOME), IntColumn.create(CobaltColumns.POSITION), DoubleColumn.create(CobaltColumns.RATIO));
    private final Table mContigResult = mResult.emptyCopy();
    private final Collection<Chromosome> mChromosomeList;

    private final ChromosomePositionCodec mChromosomePosCodec;

    private String mChromosome = null;
    private int mStart = 0;

    public DiploidRatioLoader(final Collection<Chromosome> chromosomes, ChromosomePositionCodec chromosomePosCodec)
    {
        mChromosomeList = chromosomes;
        mChromosomePosCodec = chromosomePosCodec;
    }

    public DiploidRatioLoader(final Collection<Chromosome> chromosomes, final String diploidBedPath,
            ChromosomePositionCodec chromosomePosCodec) throws IOException
    {
        this(chromosomes, chromosomePosCodec);
        List<BEDFeature> bedFeatures = new ArrayList<>();

        CB_LOGGER.info("Reading diploid regions from {}", diploidBedPath);
        try(final AbstractFeatureReader<BEDFeature, LineIterator> reader = getFeatureReader(diploidBedPath,
                new BEDCodec(),
                false))
        {
            for(BEDFeature bedFeature : reader.iterator())
            {
                bedFeatures.add(bedFeature);
            }
        }

        bedFeatures.forEach(this);
    }

    @Override
    public void accept(@NotNull Locatable bed)
    {
        if(mChromosome == null || !bed.getContig().equals(mChromosome))
        {
            finaliseCurrent();
            mChromosome = bed.getContig();
            mStart = 1;
        }

        createRatio(bed.getContig(), bed.getStart(), bed.getEnd());
        mStart = bed.getEnd() + 1;
    }

    private void createRatio(String contig, int start, int end)
    {
        int position = start;
        while(position < end)
        {
            Row row = mContigResult.appendRow();
            row.setString("chromosome", contig);
            row.setInt("position", position);
            row.setDouble("ratio", 1);
            position += WINDOW_SIZE;
        }
    }

    private void finaliseCurrent()
    {
        if(mChromosome != null && mStart > 0)
        {
            for (Chromosome c : mChromosomeList)
            {
                if (mChromosome.equals(c.contig))
                {
                    mResult.append(mContigResult);
                    break;
                }
            }
        }

        mContigResult.clear();
    }

    @NotNull
    public Table build()
    {
        finaliseCurrent();
        mChromosomePosCodec.addEncodedChrPosColumn(mResult, false);
        return mResult;
    }
}
