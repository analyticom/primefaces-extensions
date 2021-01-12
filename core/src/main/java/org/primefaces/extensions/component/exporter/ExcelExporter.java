/*
 * Copyright (c) 2011-2021 PrimeFaces Extensions
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.primefaces.extensions.component.exporter;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.*;
import java.util.List;

import javax.el.MethodExpression;
import javax.faces.FacesException;
import javax.faces.component.UIComponent;
import javax.faces.component.UIPanel;
import javax.faces.component.html.HtmlCommandButton;
import javax.faces.component.html.HtmlCommandLink;
import javax.faces.component.html.HtmlOutputText;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.*;
import org.primefaces.component.api.DynamicColumn;
import org.primefaces.component.api.UIColumn;
import org.primefaces.component.column.Column;
import org.primefaces.component.columngroup.ColumnGroup;
import org.primefaces.component.datalist.DataList;
import org.primefaces.component.datatable.DataTable;
import org.primefaces.component.rowexpansion.RowExpansion;
import org.primefaces.component.subtable.SubTable;
import org.primefaces.expression.SearchExpressionFacade;
import org.primefaces.extensions.util.ExtLangUtils;
import org.primefaces.util.Constants;

/**
 * <code>Exporter</code> component.
 *
 * @author Sudheer Jonna / last modified by $Author$
 * @since 0.7.0
 * @deprecated use core Primefaces DataExporter
 */
@Deprecated
public class ExcelExporter extends Exporter {

    private XSSFWorkbook wb;

    private CellStyle cellStyle;
    private CellStyle facetStyle;
    private Color facetBackground;
    private Short facetFontSize;
    private Color facetFontColor;
    private String facetFontStyle;
    private String fontName;
    private Short cellFontSize;
    private Color cellFontColor;
    private String cellFontStyle;
    private String datasetPadding;

    private CellStyle facetStyleLeftAlign;
    private CellStyle facetStyleCenterAlign;
    private CellStyle facetStyleRightAlign;
    private CellStyle cellStyleLeftAlign;
    private CellStyle cellStyleCenterAlign;
    private CellStyle cellStyleRightAlign;

    @Override
    public void export(final ActionEvent event, final String tableId, final FacesContext context, final String filename, final String tableTitle,
                final boolean pageOnly, final boolean selectionOnly,
                final String encodingType, final MethodExpression preProcessor, final MethodExpression postProcessor, final boolean subTable)
                throws IOException {

        wb = new XSSFWorkbook();
        final String safeName = WorkbookUtil.createSafeSheetName(filename);
        final Sheet sheet = wb.createSheet(safeName);

        cellStyle = wb.createCellStyle();
        facetStyle = wb.createCellStyle();
        final CellStyle titleStyle = wb.createCellStyle();

        facetStyleLeftAlign = wb.createCellStyle();
        facetStyleCenterAlign = wb.createCellStyle();
        facetStyleRightAlign = wb.createCellStyle();
        cellStyleLeftAlign = wb.createCellStyle();
        cellStyleCenterAlign = wb.createCellStyle();
        cellStyleRightAlign = wb.createCellStyle();

        createCustomFonts();

        if (preProcessor != null) {
            preProcessor.invoke(context.getELContext(), new Object[] {wb});
        }

        int maxColumns = 0;
        final String tokenString = ExtLangUtils.normalizeSpace(tableId.replace(',', ' '));
        final StringTokenizer st = new StringTokenizer(tokenString, " ");
        while (st.hasMoreElements()) {
            final String tableName = (String) st.nextElement();
            final UIComponent component = SearchExpressionFacade.resolveComponent(context, event.getComponent(), tableName);
            if (component == null) {
                throw new FacesException("Cannot find component \"" + tableName + "\" in view.");
            }
            if (!(component instanceof DataTable || component instanceof DataList)) {
                throw new FacesException(
                            "Unsupported datasource target:\"" + component.getClass().getName() + "\", exporter must target a PrimeFaces DataTable/DataList.");
            }
            if (!component.isRendered()) {
                continue;
            }

            final DataList list;
            final DataTable table;

            if (tableTitle != null && !tableTitle.isEmpty() && !tableId.contains(",")) {
                int lastRowNum = sheet.getLastRowNum();
                if (lastRowNum < 0) {
                    lastRowNum = 0;
                }
                final Row titleRow = sheet.createRow(lastRowNum);
                final int cellIndex = titleRow.getLastCellNum() == -1 ? 0 : titleRow.getLastCellNum();
                final Cell cell = titleRow.createCell(cellIndex);
                cell.setCellValue(new XSSFRichTextString(tableTitle));
                final Font titleFont = wb.createFont();
                titleFont.setBold(true);
                titleStyle.setFont(titleFont);
                cell.setCellStyle(titleStyle);
                sheet.createRow(lastRowNum + 3);
            }
            if (component instanceof DataList) {
                list = (DataList) component;

                if (list.getHeader() != null) {
                    tableFacet(context, sheet, list);
                }
                if (pageOnly) {
                    exportPageOnly(list, sheet);
                }
                else {
                    exportAll(list, sheet);
                }
            }
            else {

                table = (DataTable) component;
                final int columnsCount = Exporter.getColumnsCount(table);

                if (table.getHeader() != null && !subTable) {
                    tableFacet(context, sheet, table, columnsCount, ColumnType.HEADER.facet());

                }
                if (!subTable) {
                    tableColumnGroup(sheet, table, ColumnType.HEADER.facet());
                }

                addColumnFacets(table, sheet, ColumnType.HEADER);

                if (pageOnly) {
                    exportPageOnly(table, sheet);
                }
                else if (selectionOnly) {
                    exportSelectionOnly(context, table, sheet);
                }
                else {
                    exportAll(context, table, sheet, subTable);
                }

                if (table.hasFooterColumn() && !subTable) {
                    addColumnFacets(table, sheet, ColumnType.FOOTER);
                }
                if (!subTable) {
                    tableColumnGroup(sheet, table, ColumnType.FOOTER.facet());
                }
                table.setRowIndex(-1);

                final int cols = table.getColumnsCount();

                if (maxColumns < cols) {
                    maxColumns = cols;
                }
            }
            sheet.createRow(sheet.getLastRowNum() + Integer.parseInt(datasetPadding));
        }

        if (postProcessor != null) {
            postProcessor.invoke(context.getELContext(), new Object[] {wb});
        }

        if (!subTable) {
            for (int i = 0; i < maxColumns; i++) {
                sheet.autoSizeColumn((short) i);
            }
        }

        final PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setLandscape(true);
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.setPrintGridlines(true);

        writeExcelToResponse(context.getExternalContext(), wb, filename);

    }

    protected void exportAll(final FacesContext context, final DataTable table, final Sheet sheet, final boolean subTable) {

        final int first = table.getFirst();
        final int rowCount = table.getRowCount();
        final boolean lazy = table.isLazy();
        int i = 0;
        if (subTable) {
            int subTableCount = table.getRowCount();
            SubTable subtable = table.getSubTable();
            final int subTableColumnsCount = Exporter.getColumnsCount(subtable);

            if (table.getHeader() != null) {
                tableFacet(context, sheet, table, subTableColumnsCount, ColumnType.HEADER.facet());
            }

            tableColumnGroup(sheet, table, ColumnType.HEADER.facet());

            while (subTableCount > 0) {

                subTableCount--;
                table.setRowIndex(i);
                i++;
                if (subtable.getHeader() != null) {
                    tableFacet(context, sheet, subtable, subTableColumnsCount, ColumnType.HEADER.facet());
                }

                if (Exporter.hasHeaderColumn(subtable)) {
                    addColumnFacets(subtable, sheet, ColumnType.HEADER);
                }

                exportAll(subtable, sheet);

                if (Exporter.hasFooterColumn(subtable)) {

                    addColumnFacets(subtable, sheet, ColumnType.FOOTER);
                }

                if (subtable.getFooter() != null) {
                    tableFacet(context, sheet, subtable, subTableColumnsCount, ColumnType.FOOTER.facet());
                }

                subtable.setRowIndex(-1);
                subtable = table.getSubTable();
            }

            tableColumnGroup(sheet, table, ColumnType.FOOTER.facet());

            if (table.hasFooterColumn()) {
                tableFacet(context, sheet, table, subTableColumnsCount, ColumnType.FOOTER.facet());
            }
        }
        else {
            if (lazy) {
                if (rowCount > 0) {
                    table.setFirst(0);
                    table.setRows(rowCount);
                    table.clearLazyCache();
                    table.loadLazyData();
                }
                for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                    exportRow(table, sheet, rowIndex);
                }

                // restore
                table.setFirst(first);
                table.setRowIndex(-1);
                table.clearLazyCache();
                table.loadLazyData();
            }
            else {
                for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                    exportRow(table, sheet, rowIndex);
                }
                // restore
                table.setFirst(first);
            }
        }
    }

    protected void exportAll(final SubTable table, final Sheet sheet) {
        final int rowCount = table.getRowCount();

        tableColumnGroup(sheet, table, ColumnType.HEADER.facet());
        if (Exporter.hasHeaderColumn(table)) {
            addColumnFacets(table, sheet, ColumnType.HEADER);
        }
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            exportRow(table, sheet, rowIndex);
        }
        if (Exporter.hasFooterColumn(table)) {
            addColumnFacets(table, sheet, ColumnType.FOOTER);
        }
        tableColumnGroup(sheet, table, ColumnType.FOOTER.facet());

    }

    protected void exportAll(final DataList list, final Sheet sheet) {
        final int first = list.getFirst();
        final int rowCount = list.getRowCount();
        final int rows = list.getRows();
        final boolean lazy = list.isLazy();

        if (lazy) {
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                if (rowIndex % rows == 0) {
                    list.setFirst(rowIndex);
                    list.loadLazyData();
                }

                exportRow(list, sheet, rowIndex);
            }

            // restore
            list.setFirst(first);
            list.loadLazyData();
        }
        else {

            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                exportRow(list, sheet, rowIndex);
            }
            // restore
            list.setFirst(first);
        }

    }

    protected void exportPageOnly(final DataTable table, final Sheet sheet) {
        final int first = table.getFirst();
        final int rowsToExport = first + table.getRows();

        for (int rowIndex = first; rowIndex < rowsToExport; rowIndex++) {
            exportRow(table, sheet, rowIndex);
        }
    }

    protected void exportPageOnly(final DataList list, final Sheet sheet) {
        final int first = list.getFirst();
        final int rowsToExport = first + list.getRows();

        for (int rowIndex = first; rowIndex < rowsToExport; rowIndex++) {
            exportRow(list, sheet, rowIndex);
        }
    }

    protected void exportSelectionOnly(final FacesContext context, final DataTable table, final Sheet sheet) {
        final Object selection = table.getSelection();
        final String var = table.getVar();

        if (selection != null) {
            final Map<String, Object> requestMap = context.getExternalContext().getRequestMap();

            if (selection.getClass().isArray()) {
                final int size = Array.getLength(selection);

                for (int i = 0; i < size; i++) {
                    requestMap.put(var, Array.get(selection, i));
                    exportCells(table, sheet);
                }
            }
            else if (Collection.class.isAssignableFrom(selection.getClass())) {
                final Collection<?> collection = (Collection<?>) selection;
                for (final Object o : collection) {
                    requestMap.put(var, o);
                    exportCells(table, sheet);
                }
            }
            else {
                requestMap.put(var, selection);
                exportCells(table, sheet);
            }
        }
    }

    protected void tableFacet(final FacesContext context, final Sheet sheet, final DataTable table, final int columnCount, final String facetType) {
        final Map<String, UIComponent> map = table.getFacets();
        final UIComponent component = map.get(facetType);
        if (component != null) {
            String headerValue;
            if (component instanceof HtmlCommandButton) {
                headerValue = exportValue(context, component);
            }
            else if (component instanceof HtmlCommandLink) {
                headerValue = exportValue(context, component);
            }
            else if (component instanceof UIPanel) {
                final StringBuilder header = new StringBuilder(Constants.EMPTY_STRING);
                for (final UIComponent child : component.getChildren()) {
                    headerValue = exportValue(context, child);
                    header.append(headerValue);
                }
                headerValue = header.toString();
            }
            else {
                headerValue = Exporter.exportFacetValue(context, component);
            }

            final int sheetRowIndex = sheet.getLastRowNum() + 1;
            final Row row = sheet.createRow(sheetRowIndex);
            final Cell cell = row.createCell((short) 0);
            cell.setCellValue(headerValue);
            cell.setCellStyle(facetStyle);

            sheet.addMergedRegion(new CellRangeAddress(
                        sheetRowIndex, // first row (0-based)
                        sheetRowIndex, // last row (0-based)
                        0, // first column (0-based)
                        columnCount - 1 // last column (0-based)
            ));

        }
    }

    protected void tableFacet(final FacesContext context, final Sheet sheet, final SubTable table, final int columnCount, final String facetType) {
        final Map<String, UIComponent> map = table.getFacets();
        final UIComponent component = map.get(facetType);
        if (component != null) {
            String headerValue;
            if (component instanceof HtmlCommandButton) {
                headerValue = exportValue(context, component);
            }
            else if (component instanceof HtmlCommandLink) {
                headerValue = exportValue(context, component);
            }
            else if (component instanceof UIPanel) {
                final StringBuilder header = new StringBuilder(Constants.EMPTY_STRING);
                for (final UIComponent child : component.getChildren()) {
                    headerValue = exportValue(context, child);
                    header.append(headerValue);
                }
                headerValue = header.toString();
            }
            else {
                headerValue = Exporter.exportFacetValue(context, component);
            }

            final int sheetRowIndex = sheet.getLastRowNum() + 1;
            final Row row = sheet.createRow(sheetRowIndex);
            final Cell cell = row.createCell((short) 0);
            cell.setCellValue(headerValue);
            cell.setCellStyle(facetStyle);

            sheet.addMergedRegion(new CellRangeAddress(
                        sheetRowIndex, // first row (0-based)
                        sheetRowIndex, // last row (0-based)
                        0, // first column (0-based)
                        columnCount - 1 // last column (0-based)
            ));

        }
    }

    protected void tableFacet(final FacesContext context, final Sheet sheet, final DataList list) {
        final Map<String, UIComponent> map = list.getFacets();
        final UIComponent component = map.get(ColumnType.HEADER.facet());
        if (component != null) {
            final String headerValue;
            if (component instanceof HtmlCommandButton) {
                headerValue = exportValue(context, component);
            }
            else if (component instanceof HtmlCommandLink) {
                headerValue = exportValue(context, component);
            }
            else {
                headerValue = Exporter.exportFacetValue(context, component);
            }

            final int sheetRowIndex = sheet.getLastRowNum() + 1;
            final Row row = sheet.createRow(sheetRowIndex);
            final Cell cell = row.createCell((short) 0);
            cell.setCellValue(headerValue);
            cell.setCellStyle(facetStyle);

            sheet.addMergedRegion(new CellRangeAddress(
                        sheetRowIndex, // first row (0-based)
                        sheetRowIndex, // last row (0-based)
                        0, // first column (0-based)
                        1 // last column (0-based)
            ));

        }
    }

    private static int calculateColumnOffset(final Sheet sheet, final int row, int col) {
        for (int j = 0; j < sheet.getNumMergedRegions(); j++) {
            final CellRangeAddress merged = sheet.getMergedRegion(j);
            if (merged.isInRange(row, col)) {
                col = merged.getLastColumn() + 1;
            }
        }
        return col;
    }

    private void putText(final Row xlRow, final short col, final String text) {
        final Cell cell = xlRow.createCell(col);
        cell.setCellValue(text);
        cell.setCellStyle(facetStyleCenterAlign);
    }

    protected void tableColumnGroup(final Sheet sheet, final DataTable table, final String facetType) {
        facetStyleCenterAlign.setAlignment(HorizontalAlignment.CENTER);
        facetStyleCenterAlign.setVerticalAlignment(VerticalAlignment.CENTER);
        facetStyleCenterAlign.setWrapText(true);

        final ColumnGroup cg = table.getColumnGroup(facetType);
        List<UIComponent> headerComponentList = null;
        if (cg != null) {
            headerComponentList = cg.getChildren();
        }
        if (headerComponentList != null) {
            for (final UIComponent component : headerComponentList) {
                if (component instanceof org.primefaces.component.row.Row) {
                    final org.primefaces.component.row.Row row = (org.primefaces.component.row.Row) component;
                    final int rowIndex = sheet.getLastRowNum() + 1;
                    final Row xlRow = sheet.createRow(rowIndex);
                    int colIndex = 0;
                    for (final UIComponent rowComponent : row.getChildren()) {
                        final UIColumn column = (UIColumn) rowComponent;
                        if (!column.isRendered() || !column.isExportable()) {
                            continue;
                        }

                        final String text = facetType.equalsIgnoreCase(ColumnType.HEADER.facet()) ? column.getHeaderText() : column.getFooterText();
                        // by default column has 1 rowspan && colspan
                        final int rowSpan = column.getRowspan() - 1;
                        final int colSpan = column.getColspan() - 1;

                        if (rowSpan > 0 && colSpan > 0) {
                            colIndex = calculateColumnOffset(sheet, rowIndex, colIndex);
                            sheet.addMergedRegion(new CellRangeAddress(
                                        rowIndex, // first row (0-based)
                                        rowIndex + rowSpan, // last row (0-based)
                                        colIndex, // first column (0-based)
                                        colIndex + colSpan // last column (0-based)
                            ));
                            putText(xlRow, (short) colIndex, text);
                            colIndex = colIndex + colSpan;
                        }
                        else if (rowSpan > 0) {
                            sheet.addMergedRegion(new CellRangeAddress(
                                        rowIndex, // first row (0-based)
                                        rowIndex + rowSpan, // last row (0-based)
                                        colIndex, // first column (0-based)
                                        colIndex // last column (0-based)
                            ));
                            putText(xlRow, (short) colIndex, text);
                        }
                        else if (colSpan > 0) {
                            colIndex = calculateColumnOffset(sheet, rowIndex, colIndex);
                            sheet.addMergedRegion(new CellRangeAddress(
                                        rowIndex, // first row (0-based)
                                        rowIndex, // last row (0-based)
                                        colIndex, // first column (0-based)
                                        colIndex + colSpan // last column (0-based)
                            ));
                            putText(xlRow, (short) colIndex, text);
                            colIndex = colIndex + colSpan;
                        }
                        else {
                            colIndex = calculateColumnOffset(sheet, rowIndex, colIndex);
                            putText(xlRow, (short) colIndex, text);
                        }
                        colIndex++;
                    }
                }
            }
        }
    }

    protected void tableColumnGroup(final Sheet sheet, final SubTable table, final String facetType) {
        final ColumnGroup cg = table.getColumnGroup(facetType);
        List<UIComponent> headerComponentList = null;
        if (cg != null) {
            headerComponentList = cg.getChildren();
        }
        if (headerComponentList != null) {
            for (final UIComponent component : headerComponentList) {
                if (component instanceof org.primefaces.component.row.Row) {
                    final org.primefaces.component.row.Row row = (org.primefaces.component.row.Row) component;
                    final int sheetRowIndex = sheet.getPhysicalNumberOfRows() > 0 ? sheet.getLastRowNum() + 1 : 0;
                    final Row xlRow = sheet.createRow(sheetRowIndex);
                    int i = 0;
                    for (final UIComponent rowComponent : row.getChildren()) {
                        final UIColumn column = (UIColumn) rowComponent;
                        final String value;
                        if (facetType.equalsIgnoreCase(ColumnType.HEADER.facet())) {
                            value = column.getHeaderText();
                        }
                        else {
                            value = column.getFooterText();
                        }
                        final int rowSpan = column.getRowspan();
                        final int colSpan = column.getColspan();

                        Cell cell;

                        if (rowSpan > 1 || colSpan > 1) {

                            if (rowSpan > 1) {
                                cell = xlRow.createCell((short) i);
                                boolean rowSpanFlag = false;
                                for (int j = 0; j < sheet.getNumMergedRegions(); j++) {
                                    final CellRangeAddress merged = sheet.getMergedRegion(j);
                                    if (merged.isInRange(sheetRowIndex, i)) {
                                        rowSpanFlag = true;
                                    }

                                }
                                if (!rowSpanFlag) {
                                    cell.setCellStyle(cellStyle);
                                    cell.setCellValue(value);
                                    sheet.addMergedRegion(new CellRangeAddress(
                                                sheetRowIndex, // first row (0-based)
                                                sheetRowIndex + rowSpan - 1, // last row (0-based)
                                                i, // first column (0-based)
                                                i // last column (0-based)
                                    ));
                                }
                            }
                            if (colSpan > 1) {
                                cell = xlRow.createCell((short) i);
                                for (int j = 0; j < sheet.getNumMergedRegions(); j++) {
                                    final CellRangeAddress merged = sheet.getMergedRegion(j);
                                    if (merged.isInRange(sheetRowIndex, i)) {
                                        cell = xlRow.createCell((short) ++i);
                                    }
                                }
                                cell.setCellStyle(cellStyle);
                                cell.setCellValue(value);
                                sheet.addMergedRegion(new CellRangeAddress(
                                            sheetRowIndex, // first row (0-based)
                                            sheetRowIndex, // last row (0-based)
                                            i, // first column (0-based)
                                            i + colSpan - 1 // last column (0-based)
                                ));
                                i = i + colSpan - 1;
                            }
                        }
                        else {
                            cell = xlRow.createCell((short) i);
                            for (int j = 0; j < sheet.getNumMergedRegions(); j++) {
                                final CellRangeAddress merged = sheet.getMergedRegion(j);
                                if (merged.isInRange(sheetRowIndex, i)) {
                                    cell = xlRow.createCell((short) ++i);
                                }
                            }
                            cell.setCellValue(value);
                            cell.setCellStyle(facetStyle);

                        }
                        i++;
                    }
                }

            }
        }

    }

    protected void exportRow(final DataTable table, final Sheet sheet, final int rowIndex) {
        table.setRowIndex(rowIndex);
        if (!table.isRowAvailable()) {
            return;
        }

        exportCells(table, sheet);
    }

    protected void exportRow(final SubTable table, final Sheet sheet, final int rowIndex) {
        table.setRowIndex(rowIndex);

        if (!table.isRowAvailable()) {
            return;
        }

        exportCells(table, sheet);
    }

    protected void exportRow(final DataList list, final Sheet sheet, final int rowIndex) {
        list.setRowIndex(rowIndex);

        if (!list.isRowAvailable()) {
            return;
        }

        exportCells(list, sheet);
    }

    protected void exportCells(final DataTable table, final Sheet sheet) {
        final int sheetRowIndex = sheet.getLastRowNum() + 1;
        final Row row = sheet.createRow(sheetRowIndex);

        facetStyleLeftAlign.setAlignment(HorizontalAlignment.LEFT);
        facetStyleCenterAlign.setAlignment(HorizontalAlignment.CENTER);
        facetStyleCenterAlign.setVerticalAlignment(VerticalAlignment.CENTER);
        facetStyleCenterAlign.setWrapText(true);
        facetStyleRightAlign.setAlignment(HorizontalAlignment.RIGHT);
        cellStyleLeftAlign.setAlignment(HorizontalAlignment.LEFT);
        cellStyleCenterAlign.setAlignment(HorizontalAlignment.CENTER);
        cellStyleRightAlign.setAlignment(HorizontalAlignment.RIGHT);

        for (final UIColumn col : table.getColumns()) {

            if (col instanceof DynamicColumn) {
                ((DynamicColumn) col).applyStatelessModel();
            }

            if (col.isRendered() && col.isExportable()) {
                addColumnValue(row, col.getChildren(), col);
            }
        }
        final FacesContext context = null;
        if (table.getRowIndex() == 0) {
            for (final UIComponent component : table.getChildren()) {
                if (component instanceof RowExpansion) {
                    final RowExpansion rowExpansion = (RowExpansion) component;
                    if (rowExpansion.getChildren() != null) {
                        if (rowExpansion.getChildren().get(0) instanceof DataTable) {
                            final DataTable childTable = (DataTable) rowExpansion.getChildren().get(0);
                            childTable.setRowIndex(-1);
                        }
                        if (rowExpansion.getChildren().get(0) instanceof DataList) {
                            final DataList childList = (DataList) rowExpansion.getChildren().get(0);
                            childList.setRowIndex(-1);
                        }
                    }

                }
            }
        }
        for (final UIComponent component : table.getChildren()) {
            if (component instanceof RowExpansion) {
                final RowExpansion rowExpansion = (RowExpansion) component;
                if (rowExpansion.getChildren() != null) {
                    for (int i = 0; i < rowExpansion.getChildCount(); i++) {
                        final UIComponent child = rowExpansion.getChildren().get(i);
                        if (child instanceof DataList) {
                            final DataList list = (DataList) child;
                            if (list.getHeader() != null) {
                                tableFacet(null, sheet, list);
                            }
                            exportAll(list, sheet);
                        }
                    }
                    for (int i = 0; i < rowExpansion.getChildCount(); i++) {
                        if (rowExpansion.getChildren().get(i) instanceof DataTable) {
                            final DataTable childTable = (DataTable) rowExpansion.getChildren().get(i);
                            final int columnsCount = Exporter.getColumnsCount(childTable);
                            if (columnsCount > 0) { // In case none of the colums are exportable.
                                if (childTable.getHeader() != null) {
                                    tableFacet(null, sheet, childTable, columnsCount, ColumnType.HEADER.facet());

                                }
                                tableColumnGroup(sheet, childTable, ColumnType.HEADER.facet());

                                addColumnFacets(childTable, sheet, ColumnType.HEADER);

                                exportAll(context, childTable, sheet, false);

                                if (childTable.hasFooterColumn()) {
                                    addColumnFacets(childTable, sheet, ColumnType.FOOTER);
                                }
                                tableColumnGroup(sheet, childTable, ColumnType.FOOTER.facet());
                                childTable.setRowIndex(-1);
                            }
                        }
                    }

                }
            }
        }
    }

    protected void exportCells(final SubTable table, final Sheet sheet) {
        final int sheetRowIndex = sheet.getLastRowNum() + 1;
        final Row row = sheet.createRow(sheetRowIndex);

        facetStyleLeftAlign.setAlignment(HorizontalAlignment.LEFT);
        facetStyleCenterAlign.setAlignment(HorizontalAlignment.CENTER);
        facetStyleCenterAlign.setVerticalAlignment(VerticalAlignment.CENTER);
        facetStyleCenterAlign.setWrapText(true);
        facetStyleRightAlign.setAlignment(HorizontalAlignment.RIGHT);
        cellStyleLeftAlign.setAlignment(HorizontalAlignment.LEFT);
        cellStyleCenterAlign.setAlignment(HorizontalAlignment.CENTER);
        cellStyleRightAlign.setAlignment(HorizontalAlignment.RIGHT);

        for (final UIColumn col : table.getColumns()) {

            if (col instanceof DynamicColumn) {
                ((DynamicColumn) col).applyStatelessModel();
            }

            if (col.isRendered() && col.isExportable()) {
                addColumnValue(row, col.getChildren(), col);
            }
        }
    }

    protected void exportCells(final DataList list, final Sheet sheet) {
        final int sheetRowIndex = sheet.getLastRowNum() + 1;
        final Row row = sheet.createRow(sheetRowIndex);

        facetStyleLeftAlign.setAlignment(HorizontalAlignment.LEFT);
        facetStyleCenterAlign.setAlignment(HorizontalAlignment.CENTER);
        facetStyleCenterAlign.setVerticalAlignment(VerticalAlignment.CENTER);
        facetStyleCenterAlign.setWrapText(true);
        facetStyleRightAlign.setAlignment(HorizontalAlignment.RIGHT);
        cellStyleLeftAlign.setAlignment(HorizontalAlignment.LEFT);
        cellStyleCenterAlign.setAlignment(HorizontalAlignment.CENTER);
        cellStyleRightAlign.setAlignment(HorizontalAlignment.RIGHT);

        for (final UIComponent component : list.getChildren()) {
            if (component instanceof Column) {
                final UIColumn column = (UIColumn) component;
                for (final UIComponent childComponent : column.getChildren()) {
                    final int cellIndex = row.getLastCellNum() == -1 ? 0 : row.getLastCellNum();
                    final Cell cell = row.createCell(cellIndex);
                    if (component.isRendered()) {
                        final String value = exportValue(FacesContext.getCurrentInstance(), childComponent);
                        cell.setCellValue(new XSSFRichTextString(value));
                        cell.setCellStyle(cellStyle);
                    }
                }

            }
            else {
                final int cellIndex = row.getLastCellNum() == -1 ? 0 : row.getLastCellNum();
                final Cell cell = row.createCell(cellIndex);
                if (component.isRendered()) {
                    final String value = exportValue(FacesContext.getCurrentInstance(), component);
                    cell.setCellValue(new XSSFRichTextString(value));
                    cell.setCellStyle(cellStyle);
                }
            }
        }

    }

    protected void addColumnFacets(final DataTable table, final Sheet sheet, final ColumnType columnType) {

        final int sheetRowIndex = sheet.getLastRowNum() + 1;
        Row rowHeader = null;

        for (final UIColumn col : table.getColumns()) {

            if (col instanceof DynamicColumn) {
                ((DynamicColumn) col).applyStatelessModel();
            }

            if (col.isRendered() && col.isExportable() && col.getFacet(columnType.facet()) != null) {
                if (rowHeader == null) {
                    rowHeader = sheet.createRow(sheetRowIndex);
                }
                addColumnValue(rowHeader, col.getFacet(columnType.facet()));
            }
        }

    }

    protected void addColumnFacets(final SubTable table, final Sheet sheet, final ColumnType columnType) {

        final int sheetRowIndex = sheet.getPhysicalNumberOfRows() > 0 ? sheet.getLastRowNum() + 1 : 0;
        final Row rowHeader = sheet.createRow(sheetRowIndex);

        for (final UIColumn col : table.getColumns()) {

            if (col instanceof DynamicColumn) {
                ((DynamicColumn) col).applyStatelessModel();
            }

            if (col.isRendered() && col.isExportable()) {
                addColumnValue(rowHeader, col.getFacet(columnType.facet()));
            }
        }
    }

    protected void addColumnValue(final Row row, final UIComponent component) {
        final int cellIndex = row.getLastCellNum() == -1 ? 0 : row.getLastCellNum();
        final Cell cell = row.createCell(cellIndex);
        final String value = component == null ? Constants.EMPTY_STRING : exportValue(FacesContext.getCurrentInstance(), component);
        cell.setCellValue(new XSSFRichTextString(value));
        addColumnAlignments(component, cell);
    }

    protected void addColumnValue(final Row row, final List<UIComponent> components, final UIColumn column) {
        final int cellIndex = row.getLastCellNum() == -1 ? 0 : row.getLastCellNum();
        final Cell cell = row.createCell(cellIndex);
        final FacesContext context = FacesContext.getCurrentInstance();

        if (column.getExportFunction() != null) {
            cell.setCellValue(new XSSFRichTextString(Exporter.exportColumnByFunction(context, column)));
        }
        else {
            final StringBuilder builder = new StringBuilder();
            for (final UIComponent component : components) {
                if (component.isRendered()) {
                    final String value = exportValue(context, component);

                    if (value != null) {
                        builder.append(value);
                    }
                }
            }

            cell.setCellValue(new XSSFRichTextString(builder.toString()));

            for (final UIComponent component : components) {
                addColumnAlignments(component, cell);
            }
        }
    }

    protected void addColumnAlignments(final UIComponent component, final Cell cell) {
        if (component instanceof HtmlOutputText) {
            final HtmlOutputText output = (HtmlOutputText) component;
            if (output.getStyle() != null && output.getStyle().contains("left")) {
                cell.setCellStyle(cellStyleLeftAlign);
            }
            if (output.getStyle() != null && output.getStyle().contains("right")) {
                cell.setCellStyle(cellStyleRightAlign);
            }
            if (output.getStyle() != null && output.getStyle().contains("center")) {
                cell.setCellStyle(cellStyleCenterAlign);
            }
        }
    }

    @Override
    public void customFormat(final String facetBackground, final String facetFontSize, final String facetFontColor, final String facetFontStyle,
                final String fontName, final String cellFontSize,
                final String cellFontColor, final String cellFontStyle, final String datasetPadding, final String orientation) {
        if (facetBackground != null) {
            this.facetBackground = Color.decode(facetBackground);
        }
        if (facetFontColor != null) {
            this.facetFontColor = Color.decode(facetFontColor);
        }
        if (fontName != null) {
            this.fontName = fontName;
        }
        if (cellFontColor != null) {
            this.cellFontColor = Color.decode(cellFontColor);
        }

        this.facetFontSize = Short.valueOf(facetFontSize);
        this.facetFontStyle = facetFontStyle;
        this.cellFontSize = Short.valueOf(cellFontSize);
        this.cellFontStyle = cellFontStyle;
        this.datasetPadding = datasetPadding;

    }

    protected void createCustomFonts() {

        final XSSFFont facetFont = wb.createFont();
        final XSSFFont cellFont = wb.createFont();
        final DefaultIndexedColorMap colorMap = new DefaultIndexedColorMap();

        if (cellFontColor != null) {
            final XSSFColor cellColor = new XSSFColor(cellFontColor, colorMap);
            cellFont.setColor(cellColor);
        }
        if (cellFontSize != null) {
            cellFont.setFontHeightInPoints(cellFontSize);
        }

        if ("BOLD".equalsIgnoreCase(cellFontStyle)) {
            cellFont.setBold(true);
        }
        if ("ITALIC".equalsIgnoreCase(cellFontStyle)) {
            cellFont.setItalic(true);
        }

        if ("BOLD".equalsIgnoreCase(facetFontStyle)) {
            facetFont.setBold(true);
        }
        if ("ITALIC".equalsIgnoreCase(facetFontStyle)) {
            facetFont.setItalic(true);
        }

        if (fontName != null) {
            cellFont.setFontName(fontName);
            facetFont.setFontName(fontName);
        }

        if (facetBackground != null) {
            final XSSFColor backgroundColor = new XSSFColor(facetBackground, colorMap);
            ((XSSFCellStyle) facetStyle).setFillForegroundColor(backgroundColor);
            ((XSSFCellStyle) facetStyleLeftAlign).setFillForegroundColor(backgroundColor);
            ((XSSFCellStyle) facetStyleCenterAlign).setFillForegroundColor(backgroundColor);
            ((XSSFCellStyle) facetStyleRightAlign).setFillForegroundColor(backgroundColor);
            facetStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            facetStyleLeftAlign.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            facetStyleCenterAlign.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            facetStyleRightAlign.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        if (facetFontColor != null) {
            final XSSFColor facetColor = new XSSFColor(facetFontColor, colorMap);
            facetFont.setColor(facetColor);

        }
        if (facetFontSize != null) {
            facetFont.setFontHeightInPoints(facetFontSize);
        }

        cellStyle.setFont(cellFont);
        cellStyleLeftAlign.setFont(cellFont);
        cellStyleCenterAlign.setFont(cellFont);
        cellStyleRightAlign.setFont(cellFont);

        facetStyle.setFont(facetFont);
        facetStyleLeftAlign.setFont(facetFont);
        facetStyleCenterAlign.setFont(facetFont);
        facetStyleRightAlign.setFont(facetFont);
    }

    protected static void writeExcelToResponse(final ExternalContext externalContext, final org.apache.poi.ss.usermodel.Workbook generatedExcel,
                final String filename)
                throws IOException {

        externalContext.setResponseContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        externalContext.setResponseHeader("Expires", "0");
        externalContext.setResponseHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
        externalContext.setResponseHeader("Pragma", "public");
        externalContext.setResponseHeader("Content-disposition", "attachment;filename=" + filename + ".xlsx");
        externalContext.addResponseCookie(Constants.DOWNLOAD_COOKIE, "true", Collections.emptyMap());

        final OutputStream out = externalContext.getResponseOutputStream();
        generatedExcel.write(out);
        externalContext.responseFlushBuffer();
    }
}