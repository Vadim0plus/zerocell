package com.creditdatamw.labs.zerocell.handler;

import com.creditdatamw.labs.zerocell.ZeroCellException;
import com.creditdatamw.labs.zerocell.annotation.Column;
import com.creditdatamw.labs.zerocell.column.ColumnInfo;
import com.creditdatamw.labs.zerocell.converter.Converter;
import org.apache.poi.hssf.util.CellReference;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.util.SAXHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.tools.ant.taskdefs.Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.validation.ValidationException;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class EntityHandler<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityHandler.class);

    private static final String DEFAULT_SHEET = "uploads";

    private final Class<T> type;
    private final EntityExcelSheetHandler<T> entitySheetHandler;
    private final String sheetName;

    public EntityHandler(Class<T> clazz) {
        Objects.requireNonNull(clazz);
        this.type = clazz;
        this.sheetName = DEFAULT_SHEET;
        this.entitySheetHandler = createSheetHandler(clazz);
    }

    @SuppressWarnings("unchecked")
    private EntityExcelSheetHandler<T> createSheetHandler(Class<T> clazz) {
        Field[] fieldArray = clazz.getDeclaredFields();
        Set<ColumnInfo> columns = new HashSet<>(fieldArray.length);

        for (Field field: fieldArray) {
            Column annotation = field.getAnnotation(Column.class);
            if (! Objects.isNull(annotation)) {
                Class<?> converter = annotation.convertorClass();

                // if (converter.getSuperclass() != Converter.class) {
                //    throw new ZeroCellException(String.format("Converter must be subclass of the %s class", Converter.class.getName()));
                //}

                columns.add(new ColumnInfo(annotation.name(),
                                           field.getName(),
                                           annotation.index(),
                                           annotation.dataFormat(),
                                           field.getType(),
                                           converter));
            }
        }

        if (columns.isEmpty()) {
            throw new ZeroCellException(String.format("Class %s does not have @Column annotations", clazz.getName()));
        }
        ColumnInfo[] array = new ColumnInfo[columns.size()];
        columns.forEach(col -> array[col.getIndex()] = col );
        columns.clear();
        return new EntityExcelSheetHandler(array);
    }

    /**
     * Returns the extracted entities as an immutable list.
     * @return an immutable list of the extracted entities
     */
    public List<T> readAsList() {
        List<T> list = Collections.unmodifiableList(this.entitySheetHandler.read());
        return list;
    }

    /**
     * Reads a list of POJOs from the given excel file.
     *
     * @param file Excel file to read from
     * @return list of the eextracted entities
     */
    public void parseExcel(File file) {
        try (FileInputStream fis = new FileInputStream(file);
             OPCPackage opcPackage = OPCPackage.open(fis)) {

            DataFormatter dataFormatter = new DataFormatter();
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(opcPackage);
            XSSFReader xssfReader = new XSSFReader(opcPackage);
            StylesTable stylesTable = xssfReader.getStylesTable();
            InputStream sheetInputStream = null;
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            while(sheets.hasNext()) {
                sheetInputStream = sheets.next();
                if (sheets.getSheetName().equalsIgnoreCase(sheetName)) {
                    break;
                } else {
                    sheetInputStream = null;
                }
            }

            if (Objects.isNull(sheetInputStream)) {
                throw new ZeroCellException("Failed to find 'uploads' sheet in Excel Workbook");
            }

            XMLReader xmlReader = SAXHelper.newXMLReader();
            xmlReader.setContentHandler(new XSSFSheetXMLHandler(stylesTable,strings, entitySheetHandler, dataFormatter, false));
            xmlReader.parse(new InputSource(sheetInputStream));
            sheetInputStream.close();
            xmlReader = null;
            sheetInputStream = null;
            stylesTable = null;
            strings = null;
            xssfReader = null;
        } catch (Exception e) {
            throw new ZeroCellException("Failed to process file", e);
        }
    }

    private final class EntityExcelSheetHandler<T> implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final Logger LOGGER = LoggerFactory.getLogger(EntityExcelSheetHandler.class);

        private final ColumnInfo[] columns;
        private final List<T> entities;

        private boolean validateHeaders = true;
        private boolean isHeaderRow = false;
        private int currentRow = -1;
        private int currentCol = -1;
        private T cur;

        EntityExcelSheetHandler(ColumnInfo[] columns) {
            this.columns = columns;
            this.entities = new ArrayList<>();
        }

        List<T> read() {
            return Collections.unmodifiableList(this.entities);
        }

        void clear() {
            this.currentRow = -1;
            this.currentCol = -1;
            this.cur = null;
            this.entities.clear();
        }

        @Override
        @SuppressWarnings("unchecked")
        public void startRow(int i) {
            currentRow = i;
            // skip the header row
            if (currentRow == 0) {
                isHeaderRow = true;
                return;
            }
            isHeaderRow = false;
            try {
                cur = (T) type.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new ZeroCellException("Failed to create and instance of " + type.getName());
            }
            // cur.setRowNumber(currentRow);
        }

        @Override
        public void endRow(int i) {
            if (! Objects.isNull(cur)) {
                this.entities.add(cur);
                cur = null;
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment xssfComment) {
            if (Objects.isNull(cur)) return;

            // gracefully handle missing CellRef here in a similar way as XSSFCell does
            if(cellReference == null) {
                cellReference = new CellAddress(currentRow, currentCol).formatAsString();
            }

            int column = new CellReference(cellReference).getCol();
            currentCol = column;

            if (column > columns.length) {
                throw new ZeroCellException("Invalid Column index found: " + column);
            }

            ColumnInfo currentColumnInfo = columns[column];

            writeColumnField(cur, formattedValue, currentColumnInfo, currentRow);
        }

        /**
         * Write the value read from the excel cell to a field
         *
         * @param object the object to write to
         * @param formattedValue the value read from the current excel column/row
         * @param currentColumnInfo Column metadata
         * @param rowNum the row number
         */
        private void writeColumnField(T object, String formattedValue, ColumnInfo currentColumnInfo, int rowNum) {
            assertColumnName(currentColumnInfo.getName(), formattedValue);
            String fieldName = currentColumnInfo.getFieldName();
            try {
                Converter converter = (Converter) currentColumnInfo.getConverterClass().newInstance();

                BeanInfo infos = Introspector.getBeanInfo(object.getClass());
                PropertyDescriptor[] props = infos.getPropertyDescriptors();

                for (PropertyDescriptor prop : props) {
                    if (Objects.equals("class", prop.getName())) {
                        continue;
                    }

                    // Skip fields we are not interested in
                    if (! Objects.equals(fieldName, prop.getName())) {
                        continue;
                    }

                    Object value = converter.convert(formattedValue);
                    if (prop.getPropertyType() == LocalDateTime.class || prop.getPropertyType() == LocalDate.class) {
                        value = parseAsLocalDate(currentColumnInfo.getName(), rowNum, formattedValue);
                    } else if (prop.getPropertyType() == Timestamp.class) {
                        value = Timestamp.valueOf(formattedValue);
                    } else if (prop.getPropertyType() == Integer.class || prop.getPropertyType() == Long.class){
                        value = Timestamp.valueOf(formattedValue).toInstant().toEpochMilli();
                    }
                    Method writeMethod = prop.getWriteMethod();
                    if (writeMethod != null) {
                        writeMethod.invoke(object, value);
                    }
                }

            } catch (InstantiationException e) {
                LOGGER.error("Failed to set field", e);
            } catch (IntrospectionException | InvocationTargetException | IllegalAccessException e) {
                LOGGER.error("Failed to set field: {}", fieldName);
            }
        }

        private void assertColumnName(String columnName, String value) {
            if (validateHeaders && isHeaderRow) {
                if (! columnName.equalsIgnoreCase(value)){
                    throw new ValidationException(String.format("Expected Column '%s' but found '%s'", columnName, value));
                }
            }
        }

        private LocalDate parseAsLocalDate(String columnName, int rowNum, String value) {
            LocalDate date = HandlerUtils.parseAsLocalDate(columnName, rowNum, value);
            if (Objects.isNull(date)) {
                LOGGER.error("Failed to parse {} value={} from row={}", columnName, value, rowNum);
            }
            return date;
        }

        @Override
        public void headerFooter(String text, boolean b, String tagName) {
            // Skip, no headers or footers in CSV
        }
    }
}