package com.hartwig.hmftools.common.cuppa2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.*;
import java.util.stream.Collectors;

import static com.hartwig.hmftools.common.utils.file.FileDelimiters.TSV_DELIM;
import static com.hartwig.hmftools.common.utils.file.FileReaderUtils.createFieldsIndexMap;

public class CuppaPredictions
{
    public final List<CuppaPredictionEntry> PredictionEntries;
    public final boolean HasRnaData;
    public final Categories.ClfName MainCombinedClfName;

    public CuppaPredictions(final List<CuppaPredictionEntry> entries) throws IOException {
        PredictionEntries = entries;
        HasRnaData = checkHasRnaData();
        MainCombinedClfName = getMainCombinedClfName();
    }

    private static double parseDouble(String string)
    {
        if(string.length() == 0)
        {
            string = "NaN";
        }
        else if(string.equals("inf"))
        {
            string = "Infinity";
        }
        else if(string.equals("-inf"))
        {
            string = "-Infinity";
        }

        return Double.parseDouble(string);
    }

    private static String parseString(final String string)
    {
        if(string.length() > 0)
        {
            return string.toUpperCase();
        }
        return "NONE";
    }

    public static CuppaPredictions fromTsv(final String filename) throws IOException
    {
        String delimiter = TSV_DELIM;

        BufferedReader fileReader = new BufferedReader(new FileReader(filename));

        String line = fileReader.readLine();
        final Map<String, Integer> fieldsMap = createFieldsIndexMap(line, delimiter);
        int sampleIdIndex = fieldsMap.get(CuppaPredictionEntry.FLD_SAMPLE_ID);
        int dataTypeIndex = fieldsMap.get(CuppaPredictionEntry.FLD_DATA_TYPE);
        int clfGroupIndex = fieldsMap.get(CuppaPredictionEntry.FLD_CLF_GROUP);
        int clfNameIndex = fieldsMap.get(CuppaPredictionEntry.FLD_CLF_NAME);
        int featNameIndex = fieldsMap.get(CuppaPredictionEntry.FLD_FEAT_NAME);
        int featValueIndex = fieldsMap.get(CuppaPredictionEntry.FLD_FEAT_VALUE);
        int cancerTypeIndex = fieldsMap.get(CuppaPredictionEntry.FLD_CANCER_TYPE);
        int dataValueIndex = fieldsMap.get(CuppaPredictionEntry.FLD_DATA_VALUE);
        int rankIndex = fieldsMap.get(CuppaPredictionEntry.FLD_RANK);
        int rankGroupIndex = fieldsMap.get(CuppaPredictionEntry.FLD_RANK_GROUP);

        List<CuppaPredictionEntry> cuppaPredictions = new ArrayList<>();
        while((line = fileReader.readLine()) != null)
        {
            String[] rowValues = line.split(delimiter, -1);

            String dataTypeStr = parseString(rowValues[dataTypeIndex]);
            Categories.DataType dataType = Categories.DataType.valueOf(dataTypeStr);
            if(!Categories.DataType.isSampleLevelDataType(dataType))
            {
                continue;
            }

            String sampleId = parseString(rowValues[sampleIdIndex]);

            String clfGroupStr = parseString(rowValues[clfGroupIndex]);
            Categories.ClfGroup clfGroup = Categories.ClfGroup.valueOf(clfGroupStr);

            String clfNameStr;
            clfNameStr = parseString(rowValues[clfNameIndex]);
            clfNameStr = Categories.ClfName.convertAliasToName(clfNameStr);
            Categories.ClfName clfName = Categories.ClfName.valueOf(clfNameStr);

            String featName = parseString(rowValues[featNameIndex]);
            double featValue = parseDouble(rowValues[featValueIndex]);
            String cancerType = rowValues[cancerTypeIndex];
            double dataValue = parseDouble(rowValues[dataValueIndex]);

            int rank = Integer.parseInt(rowValues[rankIndex]);
            int rankGroup = Integer.parseInt(rowValues[rankGroupIndex]);

            CuppaPredictionEntry cuppaPrediction = new CuppaPredictionEntry(
                    sampleId, dataType, clfGroup, clfName,
                    featName, featValue, cancerType, dataValue,
                    rank, rankGroup
            );

            cuppaPredictions.add(cuppaPrediction);
        }

        return new CuppaPredictions(cuppaPredictions);
    }

    public void printPredictions(int nRows)
    {
        int i = 0;
        for(CuppaPredictionEntry cuppaPrediction : PredictionEntries)
        {
            System.out.println( cuppaPrediction.toString());

            i++;
            if(nRows == i)
            {
                break;
            }

        }
    }

    public void printPredictions()
    {
        printPredictions(10);
    }

    private boolean checkHasRnaData()
    {
        for(CuppaPredictionEntry cuppaPrediction : PredictionEntries)
        {
            if(!cuppaPrediction.DataType.equals(Categories.DataType.PROB))
            {
                continue;
            }

            if(cuppaPrediction.ClfName.equals(Categories.ClfName.RNA_COMBINED) & !Double.isNaN(cuppaPrediction.DataValue))
            {
                return true;
            }
        }

        return false;
    }

    private Categories.ClfName getMainCombinedClfName()
    {
        if(HasRnaData)
        {
            return Categories.ClfName.COMBINED;
        }
        return Categories.ClfName.DNA_COMBINED;
    }

    public CuppaPredictions subsetByDataType(Categories.DataType dataType) throws IOException
    {
        List<CuppaPredictionEntry> newPredictionEntries = new ArrayList<>();
        for(CuppaPredictionEntry cuppaPrediction : PredictionEntries)
        {
            if(!cuppaPrediction.DataType.equals(dataType))
            {
                continue;
            }
            newPredictionEntries.add(cuppaPrediction);
        }
        return new CuppaPredictions(newPredictionEntries);
    }

    public CuppaPredictions getTopPredictions(int n) throws IOException
    {
        List<CuppaPredictionEntry> newPredictionEntries = new ArrayList<>();
        for(CuppaPredictionEntry cuppaPrediction : PredictionEntries)
        {
            if(cuppaPrediction.Rank <= n)
            {
                newPredictionEntries.add(cuppaPrediction);
            }
        }
        return new CuppaPredictions(newPredictionEntries);
    }

    public CuppaPredictions sortByRank() throws IOException
    {
        Comparator<CuppaPredictionEntry> comparator = Comparator.comparing(cuppaPrediction -> cuppaPrediction.RankGroup);
        comparator = comparator.thenComparing(cuppaPrediction -> cuppaPrediction.Rank);

        List<CuppaPredictionEntry> sortPredictionEntries = PredictionEntries
                .stream()
                .sorted(comparator)
                .collect(Collectors.toList());

        return new CuppaPredictions(sortPredictionEntries);
    }

}


