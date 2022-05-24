package com.hartwig.hmftools.cup.somatics;

import static java.lang.Math.pow;

import static com.hartwig.hmftools.common.utils.FileWriterUtils.createBufferedWriter;
import static com.hartwig.hmftools.common.utils.MatrixFile.DEFAULT_MATRIX_DELIM;
import static com.hartwig.hmftools.cup.CuppaConfig.CUP_LOGGER;
import static com.hartwig.hmftools.cup.common.CupCalcs.convertToPercentages;
import static com.hartwig.hmftools.cup.somatics.SomaticDataLoader.loadRefSampleCounts;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.Matrix;

public final class SomaticsCommon
{
    public static final String INCLUDE_AID_APOBEC = "include_aid_apobec_gen_pos";
    public static final String INCLUDE_AID_APOBEC_DESC = "Include 8 AID/APOBEC trinucleotide contexts in genomic positions";

    public static final String NORMALISE_COPY_NUMBER = "normalise_cn";
    public static final String NORMALISE_COPY_NUMBER_DESC = "Adjust genomic-position counts by copy number ";

    public static final String EXCLUDE_SNV_96_AID_APOBEC = "exclude_aid_apobec_snv_96";
    public static final String EXCLUDE_SNV_96_AID_APOBEC_DESC = "Exclude 8 AID/APOBEC trinucleotide contexts from SNV-96 counts";

    public static final String INCLUDE_AID_APOBEC_SIG = "aid_apobec_sig_feature";
    public static final String INCLUDE_AID_APOBEC_SIG_DESC = "Add an enriched AID/APOBEC signature feature";

    public static final String INTEGER_FORMAT = "%.0f";
    public static final String DEC_1_FORMAT = "%.1f";
    public static final String DEC_3_FORMAT = "%.3f";

    public static void applyMaxCssAdjustment(double maxCssScore, final Map<String,Double> cancerCssTotals, double adjustFactor)
    {
        if(adjustFactor == 0)
            return;

        double adjustedFactor = pow(maxCssScore, adjustFactor);

        for(Map.Entry<String,Double> entry : cancerCssTotals.entrySet())
        {
            double adjCancerScore = pow(entry.getValue(), adjustedFactor);
            cancerCssTotals.put(entry.getKey(), adjCancerScore);
        }

        convertToPercentages(cancerCssTotals);
    }

    public static Matrix loadMultipleMatrixFiles(
            final List<String> filenames, final List<String> refSampleIds, final Map<String,Integer> sampleCountsIndex, final String type)
    {
        int refSampleCount = refSampleIds.size();

        Matrix combinedMatrix = null;
        int sampleIndex = 0;

        for(String filename : filenames)
        {
            final List<String> samplesList = Lists.newArrayList();
            final Matrix subMatrix = loadRefSampleCounts(filename, samplesList, Lists.newArrayList("BucketName"));

            if(subMatrix == null)
                return null;

            CUP_LOGGER.info("combined {} counts from {} samples", type, samplesList.size());

            final double[][] subData = subMatrix.getData();

            if(combinedMatrix == null)
            {
                combinedMatrix = new Matrix(refSampleCount, subMatrix.Rows);
            }

            final double[][] combinedData = combinedMatrix.getData();

            for(int s = 0; s < samplesList.size(); ++s)
            {
                final String sampleId = samplesList.get(s);

                if(!refSampleIds.contains(sampleId))
                    continue;

                sampleCountsIndex.put(sampleId, sampleIndex);

                for(int b = 0; b < combinedMatrix.Cols; ++b)
                {
                    combinedData[sampleIndex][b] = subData[s][b];
                }

                ++sampleIndex;
            }
        }

        return combinedMatrix;
    }

    public static void writeSampleMatrix(
            final Matrix matrix, final Map<String,Integer> sampleCountsIndex, final String filename, final String decFormat)
    {
        try
        {
            BufferedWriter writer = createBufferedWriter(filename, false);

            final List<String> sampleIds = sampleCountsIndex.keySet().stream().collect(Collectors.toList());
            writer.write(sampleIds.get(0));
            for(int i = 1; i < sampleIds.size(); ++i)
            {
                writer.write(String.format(",%s", sampleIds.get(i)));
            }

            writer.newLine();

            final double[][] matrixData = matrix.getData();

            // handle the transposed data - return to the form Samples in the cols, bucket data in the rows
            for(int bucket = 0; bucket < matrix.Cols; ++bucket)
            {
                int sampleIndex = sampleCountsIndex.get(sampleIds.get(0));

                writer.write(String.format(decFormat, matrixData[sampleIndex][bucket]));

                for(int s = 1; s < sampleIds.size(); ++s)
                {
                    sampleIndex = sampleCountsIndex.get(sampleIds.get(s));

                    if(sampleIndex >= matrix.Rows)
                    {
                        CUP_LOGGER.error("file({}) invalid row({}) sampleId({})", filename, s, sampleIds.get(s));
                        return;
                    }

                    writer.write(String.format(DEFAULT_MATRIX_DELIM + decFormat, matrixData[sampleIndex][bucket]));
                }

                writer.newLine();
            }

            /*
            // for each row, write out the counts for all samples

            for(int b = 0; b < matrix.Rows; ++b)
            {
                writer.write(String.format(decFormat, matrixData[b][sampleCountsIndex.get(sampleIds.get(0))]));

                for(int i = 1; i < sampleIds.size(); ++i)
                {
                    int sampleIndex = sampleCountsIndex.get(sampleIds.get(i));

                    if(sampleIndex >= matrix.Cols)
                    {
                        CUP_LOGGER.error("file({}) invalid col({}) sampleId({})", filename, i, sampleIds.get(i));
                        return;
                    }

                    writer.write(String.format("," + decFormat, matrixData[b][sampleIndex]));
                }

                writer.newLine();
            }
            */

            writer.close();
        }
        catch(IOException e)
        {
            CUP_LOGGER.error("failed to write ref sample SNV counts: {}", e.toString());
        }
    }

    public static void writeMatrix(
            final Matrix matrix, final List<String> columnNames, final String filename, final String decFormat)
    {
        try
        {
            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write(columnNames.get(0));
            for(int i = 1; i < columnNames.size(); ++i)
            {
                writer.write(DEFAULT_MATRIX_DELIM + columnNames.get(i));
            }

            writer.newLine();

            final double[][] data = matrix.getData();

            for(int b = 0; b < matrix.Cols; ++b)
            {
                writer.write(String.format(decFormat, data[0][b]));

                for(int i = 1; i < matrix.Rows; ++i)
                {
                    writer.write(String.format(DEFAULT_MATRIX_DELIM + decFormat, data[i][b]));
                }

                writer.newLine();
            }

            /*
            for(int b = 0; b < matrix.Rows; ++b)
            {
                writer.write(String.format(decFormat, data[b][0]));

                for(int i = 1; i < matrix.Cols; ++i)
                {
                    writer.write(String.format(DEFAULT_MATRIX_DELIM + decFormat, data[b][i]));
                }

                writer.newLine();
            }
            */

            writer.close();
        }
        catch(IOException e)
        {
            CUP_LOGGER.error("failed to write ref matrix data: {}", e.toString());
        }
    }

}
