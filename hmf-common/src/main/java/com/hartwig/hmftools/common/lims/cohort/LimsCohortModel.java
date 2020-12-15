package com.hartwig.hmftools.common.lims.cohort;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(allParameters = true,
             passAnnotations = { NotNull.class, Nullable.class })
public abstract class LimsCohortModel {

    @NotNull
    abstract Map<String, LimsCohortConfigData> limsCohortMap();

    private static final Logger LOGGER = LogManager.getLogger(LimsCohortModel.class);

    @Nullable
    public LimsCohortConfigData queryCohortData(@NotNull String cohortString, @NotNull String sampleId) {
        if (cohortString.equals(Strings.EMPTY)) {
            LOGGER.error("Could not resolve LIMS cohort string: '" + cohortString + "'");
            return ImmutableLimsCohortConfigData.builder()
                    .cohortId(Strings.EMPTY)
                    .hospitalId(false)
                    .reportGermline(false)
                    .reportGermlineFlag(false)
                    .reportConclusion(false)
                    .reportViral(false)
                    .requireHospitalId(false)
                    .requireHospitalPAId(false)
                    .hospitalPersonsStudy(false)
                    .hospitalPersonsRequester(false)
                    .outputFile(false)
                    .submission(false)
                    .sidePanelInfo(false)
                    .build();
        } else {
            LimsCohortConfigData cohortConfigData = limsCohortMap().get(cohortString);
            if (cohortConfigData == null) {
                LOGGER.warn("No cohort map is present for cohortString {}", cohortString);
                return ImmutableLimsCohortConfigData.builder()
                        .cohortId(Strings.EMPTY)
                        .hospitalId(false)
                        .reportGermline(false)
                        .reportGermlineFlag(false)
                        .reportConclusion(false)
                        .reportViral(false)
                        .requireHospitalId(false)
                        .requireHospitalPAId(false)
                        .hospitalPersonsStudy(false)
                        .hospitalPersonsRequester(false)
                        .outputFile(false)
                        .submission(false)
                        .sidePanelInfo(false)
                        .build();
            } else {
                if (sampleId.startsWith(cohortConfigData.cohortId())) {
                    return cohortConfigData;
                } else {
                    LOGGER.error("Cohort '{}' does match with sampleId '{}'" + cohortConfigData.cohortId() + sampleId);
                    return ImmutableLimsCohortConfigData.builder()
                            .cohortId(Strings.EMPTY)
                            .hospitalId(false)
                            .reportGermline(false)
                            .reportGermlineFlag(false)
                            .reportConclusion(false)
                            .reportViral(false)
                            .requireHospitalId(false)
                            .requireHospitalPAId(false)
                            .hospitalPersonsStudy(false)
                            .hospitalPersonsRequester(false)
                            .outputFile(false)
                            .submission(false)
                            .sidePanelInfo(false)
                            .build();
                }
            }
        }
    }
}
