package com.hartwig.hmftools.svprep.reads;

import static java.lang.String.format;

import static com.hartwig.hmftools.common.samtools.CigarUtils.cigarFromStr;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.NEG_ORIENT;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.POS_ORIENT;

import static htsjdk.samtools.CigarOperator.D;
import static htsjdk.samtools.CigarOperator.M;
import static htsjdk.samtools.CigarOperator.N;

import com.hartwig.hmftools.common.samtools.SoftClipSide;
import com.hartwig.hmftools.common.samtools.SupplementaryReadData;

import htsjdk.samtools.Cigar;

public class RemoteJunction
{
    public final String Chromosome;
    public final int Position;
    public final byte Orientation;
    public int Support;

    public RemoteJunction(final String chromosome, final int position, final byte orientation)
    {
        Chromosome = chromosome;
        Position = position;
        Orientation = orientation;
        Support = 0;
    }

    public static RemoteJunction fromSupplementaryData(final SupplementaryReadData suppData)
    {
        // use the cigar if the alignment suggest the soft-clip is on the right side
        Cigar remoteCigar = cigarFromStr(suppData.Cigar);
        SoftClipSide scSide = SoftClipSide.fromCigar(remoteCigar);

        if(scSide.isLeft())
        {
            return new RemoteJunction(suppData.Chromosome, suppData.Position, NEG_ORIENT);
        }
        else
        {
            int skippedBases = remoteCigar.getCigarElements().stream()
                    .filter(x -> x.getOperator() == N || x.getOperator() == M || x.getOperator() == D)
                    .mapToInt(x -> x.getLength()).sum();

            int remoteJunctionPos = suppData.Position + skippedBases - 1;
            return new RemoteJunction(suppData.Chromosome, remoteJunctionPos, POS_ORIENT);
        }
    }

    public boolean matches(final RemoteJunction other)
    {
        return Chromosome.equals(other.Chromosome) && Position == other.Position && Orientation == other.Orientation;
    }

    public boolean matches(final String chromosome, final int position, final byte orientation)
    {
        return Chromosome.equals(chromosome) && Position == position && Orientation == orientation;
    }

    public String toString() { return format("loc(%s:%d:%d) reads(%d)", Chromosome, Position, Orientation, Support); }
}