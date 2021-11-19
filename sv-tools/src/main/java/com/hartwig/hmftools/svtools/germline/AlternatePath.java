package com.hartwig.hmftools.svtools.germline;

import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class AlternatePath
{
    public final String VcfId;
    public final String MateId;
    public final List<Link> Links;

    public AlternatePath(final String vcfId, final String mateId, final List<Link> links)
    {
        VcfId = vcfId;
        MateId = mateId;
        Links = links;
    }

    public List<String> pathVcfIds()
    {
        List<String> pathStrings = Links.stream().map(x -> x.breakendStart().VcfId).collect(Collectors.toList());
        pathStrings.add(Links.get(Links.size() - 1).breakendEnd().VcfId);
        return pathStrings;
    }

    public List<Link> transitiveLinks()
    {
        // TODO: either make 'trs' a constant or add a link type
        return Links.stream().filter(x -> x.Id.startsWith("trs")).collect(Collectors.toList());
    }

    public String pathString()
    {
        StringJoiner sj = new StringJoiner("");

        for(int i = 0; i < Links.size(); ++i)
        {
            Link link = Links.get(i);

            if (i == 0)
                sj.add(link.breakendStart().VcfId);

            if (link.Id.equals("PAIR"))
                sj.add("-");
            else
                sj.add(String.format("<%s>", link.Id));

            sj.add(link.breakendEnd().VcfId);
        }

        return sj.toString();
    }

    public static LinkStore createLinkStore(final List<AlternatePath> alternatePaths)
    {
        LinkStore linkStore = new LinkStore();

        for(AlternatePath altPath : alternatePaths)
        {
            List<Link> transLinks = altPath.transitiveLinks();
            transLinks.forEach(x -> linkStore.addLink(altPath.VcfId, x));
        }

        return linkStore;
    }

}