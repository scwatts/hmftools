package com.hartwig.hmftools.summon.actionability;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.List;

import com.google.common.io.Resources;

import org.junit.Ignore;
import org.junit.Test;

public class ActionabilityFileReaderTest {

    private static final String ACTIONABILITY_DB_TSV = Resources.getResource("actionability/ActionabilityDB.tsv").getPath();

    @Test
    @Ignore
    public void canReadActionabilityDbTsv() throws IOException {
        List<ActionabilityEntry> actionabilityEntries = ActionabilityFileReader.read(ACTIONABILITY_DB_TSV);
        assertEquals(1, actionabilityEntries.size());
    }
}