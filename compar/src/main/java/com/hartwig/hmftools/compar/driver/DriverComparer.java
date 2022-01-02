package com.hartwig.hmftools.compar.driver;

import static com.hartwig.hmftools.compar.Category.DRIVER;
import static com.hartwig.hmftools.compar.ComparConfig.CMP_LOGGER;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalogFile;
import com.hartwig.hmftools.common.sv.linx.LinxDriver;
import com.hartwig.hmftools.compar.Category;
import com.hartwig.hmftools.compar.CommonUtils;
import com.hartwig.hmftools.compar.ComparConfig;
import com.hartwig.hmftools.compar.ComparableItem;
import com.hartwig.hmftools.compar.FileSources;
import com.hartwig.hmftools.compar.Mismatch;
import com.hartwig.hmftools.compar.ItemComparer;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

public class DriverComparer implements ItemComparer
{
    private final ComparConfig mConfig;

    public DriverComparer(final ComparConfig config)
    {
        mConfig = config;
    }

    @Override
    public Category category() { return DRIVER; }

    @Override
    public void processSample(final String sampleId, final List<Mismatch> mismatches)
    {
        CommonUtils.processSample(this, mConfig, sampleId, mismatches);
    }

    @Override
    public List<ComparableItem> loadFromDb(final String sampleId, final DatabaseAccess dbAccess)
    {
        final List<DriverCatalog> drivers = dbAccess.readDriverCatalog(sampleId);
        final List<LinxDriver> svDrivers = dbAccess.readSvDriver(sampleId);

        final List<ComparableItem> driverDataList = Lists.newArrayList();

        for(DriverCatalog driver : drivers)
        {
            List<LinxDriver> svDriverList = svDrivers.stream().filter(x -> x.gene().equals(driver.gene())).collect(Collectors.toList());
            driverDataList.add(new DriverData(driver, svDriverList, mConfig.getGeneMappedName(driver.gene())));
        }

        return driverDataList;
    }

    @Override
    public List<ComparableItem> loadFromFile(final String sampleId, final FileSources fileSources)
    {
        final List<ComparableItem> comparableItems = Lists.newArrayList();

        try
        {
            List<LinxDriver> svDrivers = LinxDriver.read(LinxDriver.generateFilename(fileSources.Linx, sampleId));

            List<DriverCatalog> drivers =
                    DriverCatalogFile.read(LinxDriver.generateCatalogFilename(fileSources.Linx, sampleId, true));

            for(DriverCatalog driver : drivers)
            {
                List<LinxDriver> svDriverList = svDrivers.stream().filter(x -> x.gene().equals(driver.gene())).collect(Collectors.toList());
                comparableItems.add(new DriverData(driver, svDriverList, mConfig.getGeneMappedName(driver.gene())));
            }
        }
        catch(IOException e)
        {
            CMP_LOGGER.info("sample({}) failed to load Linx driver data: {}", sampleId, e.toString());
        }

        return comparableItems;
    }
}
