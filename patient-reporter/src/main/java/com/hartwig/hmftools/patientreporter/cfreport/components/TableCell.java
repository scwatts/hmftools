package com.hartwig.hmftools.patientreporter.cfreport.components;

import com.hartwig.hmftools.patientreporter.cfreport.ReportResources;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;

public class TableCell extends Cell {

    public TableCell() {
        super();
        setBorder(Border.NO_BORDER);
        setBorderBottom(new SolidBorder(ReportResources.PALETTE_MID_GREY, 0.25f));
    }

}
