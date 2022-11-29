package com.hartwig.hmftools.orange.report.interpretation;

import static org.junit.Assert.assertEquals;

import com.hartwig.hmftools.orange.algo.purple.PurpleVariantTestFactory;
import com.hartwig.hmftools.orange.report.ReportResources;

import org.junit.Test;

public class VariantsTest {

    @Test
    public void canRenderRNADepthField() {
        VariantEntry missingRNA = VariantEntryTestFactory.builder().rnaDepth(null).build();
        assertEquals(ReportResources.NOT_AVAILABLE, Variants.rnaDepthField(missingRNA));

        VariantEntry proper = VariantEntryTestFactory.builder()
                .rnaDepth(PurpleVariantTestFactory.depthBuilder().alleleReadCount(10).totalReadCount(20).build())
                .build();
        assertEquals("10/20 (50%)", Variants.rnaDepthField(proper));

        VariantEntry noDepth = VariantEntryTestFactory.builder()
                .rnaDepth(PurpleVariantTestFactory.depthBuilder().alleleReadCount(0).totalReadCount(0).build())
                .build();
        assertEquals("0/0", Variants.rnaDepthField(noDepth));
    }
}