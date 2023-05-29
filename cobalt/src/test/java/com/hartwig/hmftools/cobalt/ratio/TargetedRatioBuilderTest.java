package com.hartwig.hmftools.cobalt.ratio;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.hartwig.hmftools.cobalt.Chromosome;
import com.hartwig.hmftools.cobalt.ChromosomePositionCodec;
import com.hartwig.hmftools.cobalt.CobaltColumns;
import com.hartwig.hmftools.cobalt.targeted.TargetedRatioBuilder;
import com.hartwig.hmftools.common.cobalt.ReadRatio;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import tech.tablesaw.api.*;

public class TargetedRatioBuilderTest
{
    private static final Chromosome CHROMOSOME = new Chromosome("chr1", 10000);
    private static final double EPSILON = 1e-10;

    @Test
    public void testOnTargetRatio()
    {
        var chromosomePositionCodec = new ChromosomePositionCodec();

        final Table ratios = Table.create(
                StringColumn.create("chromosome"),
                IntColumn.create("position"),
                DoubleColumn.create("ratio"),
                IntColumn.create("gcBucket"),
                BooleanColumn.create("isMappable"),
                BooleanColumn.create("isAutosome"));

        addReadRatio(ratios, 1001, 0, 45);
        addReadRatio(ratios, 2001, 0.5, 45);
        addReadRatio(ratios, 11001, 4.0, 45);
        addReadRatio(ratios, 12001, 19.5, 45);
        addReadRatio(ratios, 23001, 0, 45);

        chromosomePositionCodec.addEncodedChrPosColumn(ratios, false);

        final Table targetEnrichmentRatios = Table.create(
                StringColumn.create(CobaltColumns.CHROMOSOME),
                IntColumn.create(CobaltColumns.POSITION),
                DoubleColumn.create(CobaltColumns.RELATIVE_ENRICHMENT),
                BooleanColumn.create("offTarget"));

        Row row = targetEnrichmentRatios.appendRow();
        row.setString(CobaltColumns.CHROMOSOME, CHROMOSOME.contig);
        row.setInt(CobaltColumns.POSITION, 2001);
        row.setDouble(CobaltColumns.RELATIVE_ENRICHMENT, 2.0);
        row.setBoolean("offTarget", false);

        row = targetEnrichmentRatios.appendRow();
        row.setString(CobaltColumns.CHROMOSOME, CHROMOSOME.contig);
        row.setInt(CobaltColumns.POSITION, 12001);
        row.setDouble(CobaltColumns.RELATIVE_ENRICHMENT, 10.0);
        row.setBoolean("offTarget", false);

        chromosomePositionCodec.addEncodedChrPosColumn(targetEnrichmentRatios, true);

        var ratioBuilder = new TargetedRatioBuilder(ratios, targetEnrichmentRatios, chromosomePositionCodec);

        Table onTargetRatios = ratioBuilder.onTargetRatios();

        assertEquals(2, onTargetRatios.rowCount());

        Row readRatio2001 = onTargetRatios.row(0);
        Row readRatio12001 = onTargetRatios.row(1);

        assertEquals(2001, readRatio2001.getInt(CobaltColumns.POSITION));

        // ratio = raw ratio / target enrichment / median of raw ratios that overlap with targeted

        // median of the unnormalized gc ratio is 10.0
        // so read ratio = 0.5 / 2.0 / 10 = 0.025
        assertEquals(0.22727272727, readRatio2001.getDouble(CobaltColumns.RATIO), EPSILON);

        assertEquals(12001, readRatio12001.getInt(CobaltColumns.POSITION));

        // median of the unnormalized gc ratio is 10.0
        // so read ratio = 19.5 / 10.0 / 10 = 0.195
        assertEquals(1.7727272727272725, readRatio12001.getDouble(CobaltColumns.RATIO), EPSILON);
    }

    @NotNull
    private static ListMultimap<Chromosome, ReadRatio> create(ReadRatio ... readRatios)
    {
        ListMultimap<Chromosome, ReadRatio> ratios = ArrayListMultimap.create();
        ratios.putAll(CHROMOSOME, Arrays.asList(readRatios));
        return ratios;
    }

    private static void addReadRatio(Table table, int position, double ratio, int gcBucket)
    {
        Row row = table.appendRow();
        row.setString(CobaltColumns.CHROMOSOME, CHROMOSOME.contig);
        row.setInt(CobaltColumns.POSITION, position);
        row.setDouble(CobaltColumns.RATIO, ratio);
        row.setInt("gcBucket", gcBucket);
        row.setBoolean("isMappable", true);
        row.setBoolean("isAutosome", true);
    }
}
