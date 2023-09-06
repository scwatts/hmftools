package com.hartwig.hmftools.pave;

import static com.hartwig.hmftools.common.drivercatalog.panel.DriverGenePanelConfig.DRIVER_GENE_PANEL_OPTION;
import static com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache.ENSEMBL_DATA_DIR;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.REF_GENOME;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.loadRefGenome;
import static com.hartwig.hmftools.pave.PaveConfig.PON_ARTEFACTS_FILE;
import static com.hartwig.hmftools.pave.PaveConfig.PON_FILE;
import static com.hartwig.hmftools.pave.PaveConfig.PON_FILTERS;
import static com.hartwig.hmftools.pave.PaveConfig.PV_LOGGER;

import com.hartwig.hmftools.common.genome.refgenome.RefGenomeInterface;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;
import com.hartwig.hmftools.pave.annotation.Blacklistings;
import com.hartwig.hmftools.pave.annotation.ClinvarAnnotation;
import com.hartwig.hmftools.pave.annotation.GnomadAnnotation;
import com.hartwig.hmftools.pave.annotation.Mappability;
import com.hartwig.hmftools.pave.annotation.PonAnnotation;
import com.hartwig.hmftools.pave.annotation.Reportability;

public class ReferenceData
{
    public final GeneDataCache GeneDataCache;
    public final GnomadAnnotation Gnomad;
    public final PonAnnotation StandardPon;
    public final PonAnnotation ArtefactsPon;
    public final Mappability VariantMappability;
    public final ClinvarAnnotation Clinvar;
    public final Blacklistings BlacklistedVariants;
    public final RefGenomeInterface RefGenome;
    public final Reportability ReportableClassifier;

    public ReferenceData(final PaveConfig config, final ConfigBuilder configBuilder)
    {
        GeneDataCache = new GeneDataCache(
                configBuilder.getValue(ENSEMBL_DATA_DIR), config.RefGenVersion,
                configBuilder.getValue(DRIVER_GENE_PANEL_OPTION), true);

        if(!GeneDataCache.loadCache(config.OnlyCanonical, false))
        {
            PV_LOGGER.error("gene data cache loading failed, exiting");
            System.exit(1);
        }

        ReportableClassifier = new Reportability(GeneDataCache.getDriverPanel());

        Gnomad = new GnomadAnnotation(configBuilder);

        StandardPon = new PonAnnotation(configBuilder.getValue(PON_FILE));
        StandardPon.loadFilters(configBuilder.getValue(PON_FILTERS));

        ArtefactsPon = new PonAnnotation(configBuilder.getValue(PON_ARTEFACTS_FILE));

        VariantMappability = new Mappability(configBuilder);
        Clinvar = new ClinvarAnnotation(configBuilder);
        BlacklistedVariants = new Blacklistings(configBuilder);

        RefGenome = loadRefGenome(configBuilder.getValue(REF_GENOME));
    }

    public boolean isValid()
    {
        if(StandardPon.isEnabled() && !StandardPon.hasValidData())
        {
            PV_LOGGER.error("invalid PON");
            return false;
        }

        if(ArtefactsPon.isEnabled() && !ArtefactsPon.hasValidData())
        {
            PV_LOGGER.error("invalid PON artefacts");
            return false;
        }

        if(!VariantMappability.hasValidData())
        {
            PV_LOGGER.error("invalid mappability data");
            return false;
        }

        if(!Clinvar.hasValidData())
        {
            PV_LOGGER.error("invalid Clinvar data");
            return false;
        }

        if(!BlacklistedVariants.hasValidData())
        {
            PV_LOGGER.error("invalid blacklistings data");
            return false;
        }

        if(!Gnomad.hasValidData())
        {
            PV_LOGGER.error("invalid Gnomad data");
            return false;
        }

        return true;
    }
}