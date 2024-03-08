package com.hartwig.hmftools.esvee.common;

public enum SupportType
{
    JUNCTION,
    INDEL,
    CANDIDATE_DISCORDANT,
    DISCORDANT,
    JUNCTION_MATE;

    public boolean isSplitSupport() { return this == JUNCTION || this == INDEL; }
}
