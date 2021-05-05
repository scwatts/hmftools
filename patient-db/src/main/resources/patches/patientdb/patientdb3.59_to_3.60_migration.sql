DROP TABLE pgxCalls;

ALTER TABLE pgxGenotype RENAME TO peachGenotype;

CREATE TABLE peachCalls
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    gene varchar(255) NOT NULL,
    chromosome varchar(255) NOT NULL,
    positionV37 varchar(255) NOT NULL,
    positionV38 varchar(255) NOT NULL,
    refV37 varchar(255) NOT NULL,
    refV38 varchar(255) NOT NULL,
    allele1 varchar(255) NOT NULL,
    allele2 varchar(255) NOT NULL,
    rsid varchar(255) NOT NULL,
    variantAnnotationV37 varchar(255) NOT NULL,
    filterV37 varchar(255) NOT NULL,
    variantAnnotationV38 varchar(255) NOT NULL,
    filterV38 varchar(255) NOT NULL,
    panelVersion varchar(255) NOT NULL,
    repoVersion varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE virusBreakend
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    taxidGenus int NOT NULL,
    nameGenus varchar(255) NOT NULL,
    readsGenusTree int NOT NULL,
    taxidSpecies int NOT NULL,
    nameSpecies varchar(255) NOT NULL,
    readsSpeciesTree int NOT NULL,
    taxidAssigned int NOT NULL,
    nameAssigned varchar(255) NOT NULL,
    readsAssignedTree int NOT NULL,
    readsAssignedDirect int NOT NULL,
    reference varchar(255) NOT NULL,
    referenceTaxid int NOT NULL,
    referenceKmerCount int NOT NULL,
    alternateKmerCount int NOT NULL,
    Rname varchar(255) NOT NULL,
    startpos int NOT NULL,
    endpos int NOT NULL,
    numreads int NOT NULL,
    covbases int NOT NULL,
    coverage int NOT NULL,
    meandepth int NOT NULL,
    meanbaseq int NOT NULL,
    meanmapq int NOT NULL,
    integrations int NOT NULL,
    QCStatus varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE flagstat
(   sampleId varchar(255) NOT NULL,
    refUniqueReadCount BIGINT NOT NULL,
    refSecondaryCount BIGINT NOT NULL,
    refSupplementaryCount BIGINT NOT NULL,
    refDuplicateProportion DOUBLE PRECISION NOT NULL,
    refMappedProportion DOUBLE PRECISION NOT NULL,
    refPairedInSequencingProportion DOUBLE PRECISION NOT NULL,
    refProperlyPairedProportion DOUBLE PRECISION NOT NULL,
    refWithItselfAndMateMappedProportion DOUBLE PRECISION NOT NULL,
    refSingletonProportion DOUBLE PRECISION NOT NULL,
    tumorUniqueReadCount BIGINT NOT NULL,
    tumorSecondaryCount BIGINT NOT NULL,
    tumorSupplementaryCount BIGINT NOT NULL,
    tumorDuplicateProportion DOUBLE PRECISION NOT NULL,
    tumorMappedProportion DOUBLE PRECISION NOT NULL,
    tumorPairedInSequencingProportion DOUBLE PRECISION NOT NULL,
    tumorProperlyPairedProportion DOUBLE PRECISION NOT NULL,
    tumorWithItselfAndMateMappedProportion DOUBLE PRECISION NOT NULL,
    tumorSingletonProportion DOUBLE PRECISION NOT NULL,
    passQC BOOLEAN NOT NULL,
    PRIMARY KEY (sampleId)
);

CREATE TABLE cuppaResult
(   id int NOT NULL AUTO_INCREMENT,
    modified DATETIME NOT NULL,
    sampleId varchar(255) NOT NULL,
    cuppaResult varchar(255) NOT NULL,
    PRIMARY KEY (id),
    KEY(sampleId)
);