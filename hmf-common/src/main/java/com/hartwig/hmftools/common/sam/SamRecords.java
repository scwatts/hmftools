package com.hartwig.hmftools.common.sam;

import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMRecord;

public class SamRecords {

    public static final int PHRED_OFFSET = 33;

    public static int avgQuality(@NotNull final String baseQualities) {
        return (int) Math.floor(totalQuality(baseQualities) / baseQualities.length());
    }

    public static int totalQuality(@NotNull final String baseQualities) {
        int score = 0;
        for (int index = 0; index < baseQualities.length(); index++) {
            score += quality(baseQualities.charAt(index));
        }
        return score;
    }

    public static int quality(final char quality) {
        return quality  - PHRED_OFFSET;
    }

    public static boolean containsInsert(int position, @NotNull final String alt, @NotNull final SAMRecord record) {
        int recordIdxOfVariantStart = record.getReadPositionAtReferencePosition(position);

        int insertedBases = basesInsertedAfterPosition(position, record);
        return insertedBases == alt.length() - 1 && record.getReadString()
                .substring(recordIdxOfVariantStart - 1, recordIdxOfVariantStart - 1 + alt.length())
                .equals(alt);
    }

    public static boolean containsDelete(int position, @NotNull final String ref, @NotNull final SAMRecord record) {
        int deletedBases = basesDeletedAfterPosition(position, record);
        return deletedBases == ref.length() - 1;
    }

    public static int basesInsertedAfterPosition(int position, @NotNull final SAMRecord record) {
        int startReadPosition = record.getReadPositionAtReferencePosition(position);
        assert (startReadPosition != 0);
        int nextReadPosition = record.getReadPositionAtReferencePosition(position + 1);

        return nextReadPosition == 0 && record.getAlignmentEnd() == position
                ? record.getReadString().length() - startReadPosition
                : Math.max(0, nextReadPosition - startReadPosition - 1);
    }

    public static int basesDeletedAfterPosition(int position, @NotNull final SAMRecord record) {
        int startReadPosition = record.getReadPositionAtReferencePosition(position);
        assert (startReadPosition != 0);

        int nextReferencePosition = record.getReferencePositionAtReadPosition(startReadPosition + 1);
        return nextReferencePosition == 0 && startReadPosition == record.getReadLength()
                ? record.getAlignmentEnd() - position
                : Math.max(0, nextReferencePosition - position - 1);
    }
}
