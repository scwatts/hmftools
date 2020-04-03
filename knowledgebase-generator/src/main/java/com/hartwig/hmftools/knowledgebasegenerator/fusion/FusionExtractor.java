package com.hartwig.hmftools.knowledgebasegenerator.fusion;

import java.util.List;

import com.hartwig.hmftools.vicc.datamodel.ViccSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

public class FusionExtractor {
    private static final Logger LOGGER = LogManager.getLogger(FusionExtractor.class);

    @NotNull
    public static KnownFusions determinePromiscuous3Fusions(@NotNull ViccSource source, @NotNull String typeEvent, @NotNull String gene) {
        String promiscuous3 = Strings.EMPTY;
            if (gene.equals("TRAC-NKX2-1")) {
                promiscuous3 = "TRAC";
            } else if (gene.contains("-")) {
                promiscuous3 = gene.split("-")[0];
            }

        return ImmutableKnownFusions.builder()
                .gene(promiscuous3)
                .eventType(typeEvent)
                .source(source.toString())
                .sourceLink(source.toString())
                .build();
    }

    @NotNull
    public static KnownFusions determinePromiscuous5Fusions(@NotNull ViccSource source, @NotNull String typeEvent, @NotNull String gene) {
        String promiscuous5 = Strings.EMPTY;
        if (gene.equals("TRAC-NKX2-1")) {
            promiscuous5 = "NKX2-1";
        } else if (gene.contains("-")) {
            promiscuous5 = gene.split("-")[1];
        }

        return ImmutableKnownFusions.builder()
                .gene(promiscuous5)
                .eventType(typeEvent)
                .source(source.toString())
                .sourceLink(source.toString())
                .build();
    }

    @NotNull
    public static KnownFusions determineKnownFusionsPairs(@NotNull ViccSource source, @NotNull String typeEvent, @NotNull String gene,
            @NotNull String function) {
        if (gene.equals("ZNF198-FGFR1")) {
            gene = "ZMYM2-FGFR1";
        } else if (gene.equals("NPM-ALK")) {
            gene = "NPM1-ALK";
        } else if (gene.equals("ABL1-BCR")) {
            gene = "BCR-ABL1";
        } else if (gene.equals("ROS1-CD74")) {
            gene = "CD74-ROS1";
        } else if (gene.equals("RET-CCDC6")) {
            gene = "CCDC6-RET";
        } else if (gene.equals("EP300-MOZ")) {
            gene = "KAT6A-EP300";
        } else if (gene.equals("EP300-MLL")) {
            gene = "KMT2A-EP300";
        } else if (gene.equals("BRD4-NUT")) {
            gene = "BRD4-NUTM1";
        } else if (gene.equals("CEP110-FGFR1")) {
            gene = "CNTRL-FGFR1";
        } else if (gene.equals("FGFR2-KIAA1967")) {
            gene = "FGFR2-CCAR2";
        } else if (gene.equals("FIG-ROS1")) {
            gene = "GOPC-ROS1";
        } else if (gene.equals("GPIAP1-PDGFRB")) {
            gene = "CAPRIN1-PDGFRB";
        } else if (gene.equals("IGL-MYC")) {
            gene = "IGLC6-MYC";
        } else if (gene.equals("KIAA1509-PDGFRB")) {
            gene = "CCDC88C-PDGFRB";
        } else if (gene.equals("MLL-TET1")) {
            gene = "KMT2A-TET1";
        } else if (gene.equals("PAX8-PPAR?")) {
            gene = "PAX8-PPARA";
        } else if (gene.equals("SEC16A1-NOTCH1")) {
            gene = "SEC16A-NOTCH1";
        } else if (gene.equals("TEL-JAK2")) {
            gene = "ETV6-JAK2";
        } else if (gene.equals("TRA-NKX2-1")) {
            gene = "TRAC-NKX2-1";
        } else if (gene.equals("FGFR1OP1-FGFR1")) {
            gene = "FGFR1OP-FGFR1";
        } else if (gene.equals("PDGFRA-FIP1L1")) {
            gene = "FIP1L1-PDGFRA";
        } else if (gene.equals("PDGFB-COL1A1")) {
            gene = "COL1A1-PDGFB";
        } else if (gene.equals("BRD4-C15orf55")) {
            gene = "BRD4-NUTM1";
        } else if (gene.equals("MLL-MLLT3")) {
            gene = "KMT2A-MLLT3";
        } else if (gene.equals("BCR-ABL")) {
            gene = "BCR-ABL1";
        } else if (gene.equals("ERLIN2?FGFR1")) {
            gene = "ERLIN2-FGFR1";
        } else if (gene.equals("FGFR2?PPHLN1")) {
            gene = "FGFR2-PPHLN1";
        } else if (gene.equals("FGFR3 - BAIAP2L1")) {
            gene = "FGFR3-BAIAP2L1";
        } else if (gene.equals("PAX8-PPAR?")) {
            gene = "PAX8-PPARA";
        } else if (gene.contains("IGH") || gene.contains("IGK") || gene.contains("TRB") || gene.contains("Delta")
                || gene.equals("RET-TPCN1") || gene.equals("PVT1-MYC") || gene.equals("ESR1-CCDC170") || gene.equals("BRAF-CUL1")
                || function.equals("Loss-of-function")) {
            gene = Strings.EMPTY;
            typeEvent = Strings.EMPTY;
        }
        return ImmutableKnownFusions.builder()
                .gene(gene)
                .eventType(typeEvent)
                .source(source.toString())
                .sourceLink(source.toString())
                .build();
    }
}
