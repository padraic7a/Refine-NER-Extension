package org.freeyourmetadata.ner.operations;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.refine.util.ParsingUtilities;

import org.apache.commons.lang3.ArrayUtils;
import org.freeyourmetadata.ner.services.ExtractionResult;
import org.freeyourmetadata.ner.services.NamedEntity;

import com.google.refine.history.Change;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.google.refine.model.changes.CellAtRow;
import com.google.refine.model.changes.ColumnAdditionChange;
import com.google.refine.model.changes.ColumnRemovalChange;
import com.google.refine.util.JSONUtilities;
import com.google.refine.util.Pool;

/**
 * A change resulting from named-entity recognition
 *
 * @author Ruben Verborgh
 */
public class NERChange implements Change {
    @JsonProperty("columnIndex")
    private final int columnIndex;
    @JsonProperty("serviceNames")
    private final String[] serviceNames;
    @JsonProperty("extractionResults")
    private final ExtractionResult[][] extractionResults;
    private final List<Integer> addedRowIds;

    /**
     * Creates a new <tt>NERChange</tt>
     *
     * @param columnIndex       The index of the column used for named-entity recognition
     * @param serviceNames      The names of the used services
     * @param extractionResults The results of named-entity extraction per row and service
     */
    @JsonCreator
    public NERChange(final int columnIndex, final String[] serviceNames,
                     final ExtractionResult[][] extractionResults) {
        this.columnIndex = columnIndex;
        this.serviceNames = serviceNames;
        this.extractionResults = extractionResults;
        this.addedRowIds = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void apply(final Project project) {
        synchronized (project) {
            final int[] cellIndexes = createColumns(project);
            insertValues(project, cellIndexes);
            project.update();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revert(final Project project) {
        synchronized (project) {
            deleteRows(project);
            deleteColumns(project);
            project.update();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void save(final Writer writer, final Properties options) throws IOException {
        final JsonGenerator json = ParsingUtilities.mapper.getFactory().createGenerator(writer);
        try {
            /* Change object */
            json.writeStartObject();
            json.writeNumberField("column", columnIndex);
            json.writeArrayFieldStart("services");
            for (String serviceName : serviceNames) {
                json.writeString(serviceName);
            }
            json.writeEndArray();

            /* Nested array of extraction results */
            {
                /* Array of results per row */
                json.writeArrayFieldStart("entities");
                for (final ExtractionResult[] rowResults : extractionResults) {
                    /* Array of results per service on this row */
                    json.writeStartArray();
                    /* Array of results for this service */
                    for (final ExtractionResult extractionResult : rowResults) {
                        /* Array of entities */
                        if (!extractionResult.hasError()) {
                            json.writeStartArray();
                            for (final NamedEntity entity : extractionResult.getNamedEntities())
                                entity.writeTo(json);
                            json.writeEndArray();
                        }
                        /* Error object */
                        else {
                            json.writeStartObject();
                            json.writeStringField("error", extractionResult.getExtractionError().message);
                            json.writeEndObject();
                        }
                    }
                    json.writeEndArray();
                }
                json.writeEndArray();
            }
            /* Added row numbers array */
            {
                json.writeArrayFieldStart("addedRows");
                for (Integer addedRowId : addedRowIds)
                    json.writeNumber(addedRowId);
                json.writeEndArray();
            }
            json.writeEndObject();
            json.close();
        } catch (IOException error) {
            throw new IOException(error);
        }
    }

    /**
     * Create a <tt>NERChange</tt> from a configuration reader
     *
     * @param reader The reader
     * @param pool   (unused but required, since this method is called through reflection)
     * @return A new <tt>NERChange</tt>
     * @throws Exception If the configuration is in an unexpected format
     */
    static public Change load(LineNumberReader reader, Pool pool) throws Exception {
        /* Parse JSON line */
        ObjectMapper objectMapper = new ObjectMapper();
        final ObjectNode changeJson = (ObjectNode) objectMapper.readTree(reader.readLine());

        /* Simple properties */
        final int columnIndex = changeJson.get("column").asInt();
        final String[] serviceNames = JSONUtilities.getStringArray(changeJson, "services");

        /* Nested array of extraction results */
        final ArrayNode namedEntitiesJson = (ArrayNode) changeJson.get("entities");
        final ExtractionResult[][] extractionResults = new ExtractionResult[namedEntitiesJson.size()][];
        /* Array of results per row */
        for (int i = 0; i < extractionResults.length; i++) {
            /* Array of results per service on this row */
            final ArrayNode rowResultsJson = (ArrayNode) namedEntitiesJson.get(i);
            final ExtractionResult[] rowResults = new ExtractionResult[rowResultsJson.size()];
            for (int j = 0; j < rowResults.length; j++) {
                final JsonNode error = rowResultsJson.get(j);
                if (error instanceof ArrayNode) {
                    /* Array of entities */
                    final ArrayNode entitiesJson = (ArrayNode) rowResultsJson.get(j);
                    final NamedEntity[] entities = new NamedEntity[rowResultsJson.size()];
                    for (int k = 0; k < entities.length; k++) {
                        try {
                            entities[k] = new NamedEntity((ObjectNode) entitiesJson.get(k));
                        }
                        // entitiesJson.get(k) == null will trigger a entitiesJson.get(k)
                        catch (NullPointerException | IOException e) {
                            entities[k] = new NamedEntity("");
                        }
                    }
                    rowResults[j] = new ExtractionResult(entities);
                } else {
                    /* Error object */
                    rowResults[j] = new ExtractionResult(new Exception(error.get("error").asText()));
                }
            }
            extractionResults[i] = rowResults;
        }

        /* Reconstruct change object */
        final NERChange change = new NERChange(columnIndex, serviceNames, extractionResults);
        for (final int addedRowId : JSONUtilities.getIntArray(changeJson, "addedRows"))
            change.addedRowIds.add(addedRowId);
        return change;
    }

    /**
     * Create the columns where the named entities will be stored
     *
     * @param project The project
     * @return The cell indexes of the created columns
     */
    protected int[] createColumns(final Project project) {
        // Create empty cells that will populate each row
        final int rowCount = project.rows.size();
        final ArrayList<CellAtRow> emptyCells = new ArrayList<CellAtRow>(rowCount);
        for (int r = 0; r < rowCount; r++)
            emptyCells.add(new CellAtRow(r, null));

        // Create rows
        final int[] cellIndexes = new int[serviceNames.length];
        for (int c = 0; c < serviceNames.length; c++) {
            final CustomColumnAdditionChange change
                    = new CustomColumnAdditionChange(serviceNames[c], columnIndex + c, emptyCells);
            change.apply(project);
            cellIndexes[c] = change.getCellIndex();
        }
        // Return cell indexes of created rows
        return cellIndexes;
    }

    /**
     * Delete the columns where the named entities have been stored
     *
     * @param project The project
     */
    protected void deleteColumns(final Project project) {
        for (int i = 0; i < serviceNames.length; i++)
            new ColumnRemovalChange(columnIndex).apply(project);
    }

    /**
     * Insert the extracted named entities into rows with the specified cell indexes
     *
     * @param project     The project
     * @param cellIndexes The cell indexes of the rows that will contain the named entities
     */
    protected void insertValues(final Project project, final int[] cellIndexes) {
        final List<Row> rows = project.rows;
        // Make sure there are rows
        if (rows.isEmpty())
            return;

        // Make sure all rows have enough cells, creating new ones as necessary
        final int maxCellIndex = Collections.max(Arrays.asList(ArrayUtils.toObject(cellIndexes)));
        final int minRowSize = maxCellIndex + 1;
        for (final Row row : rows)
            while (row.cells.size() < minRowSize)
                row.cells.add(null);

        // Add the extracted named entities to all rows, creating new ones as necessary
        int rowNumber = 0;
        addedRowIds.clear();
        for (final ExtractionResult[] rowResults : extractionResults) {
            // Determine the maximum number of named entities per service
            int maxEntities = 0;
            for (int col = 0; col < rowResults.length; col++) {
                int neededCells = rowResults[col].hasError() ? 1 : rowResults[col].getNamedEntities().length;
                maxEntities = Math.max(maxEntities, neededCells);
            }
            if (maxEntities > 0) {
                // Create new blank rows if the results don't fit on a single line
                for (int i = 1; i < maxEntities; i++) {
                    final Row entityRow = new Row(minRowSize);
                    final int entityRowId = rowNumber + i;
                    for (int j = 0; j < minRowSize; j++)
                        entityRow.cells.add(null);
                    rows.add(entityRowId, entityRow);
                    addedRowIds.add(entityRowId);
                }
                // Place all results
                for (int col = 0; col < rowResults.length; col++) {
                    // Place each found entity on a row
                    if (!rowResults[col].hasError()) {
                        final NamedEntity[] entities = rowResults[col].getNamedEntities();
                        for (int r = 0; r < entities.length; r++)
                            rows.get(rowNumber + r).cells.set(cellIndexes[col], entities[r].toCell());
                    }
                    // Place an error only on the first row
                    else {
                        final Cell errorCell = new Cell(rowResults[col].getExtractionError(), null);
                        rows.get(rowNumber).cells.set(cellIndexes[col], errorCell);
                    }
                }
            }
            // Advance to the next original row
            rowNumber += Math.max(1, maxEntities);
        }
    }

    /**
     * Delete rows that were added to contain extracted named entities
     *
     * @param project The project
     */
    protected void deleteRows(final Project project) {
        final List<Row> rows = project.rows;
        // Traverse rows IDs in reverse, from high to low,
        // to avoid index shifts as rows get deleted.
        for (int i = addedRowIds.size() - 1; i >= 0; i--) {
            final int addedRowId = addedRowIds.get(i);
            if (addedRowId >= rows.size())
                throw new IndexOutOfBoundsException(String.format("Needed to remove row %d, "
                        + "but only %d rows were available.", addedRowId, rows.size()));
            rows.remove(addedRowId);
        }
        addedRowIds.clear();
    }

    /**
     * Subclass of <tt>ColumnAdditionChange</tt>
     * that provides access to the cell index of the created column
     */
    protected static class CustomColumnAdditionChange extends ColumnAdditionChange {
        /**
         * Create a new <tt>CustomColumnAdditionChange</tt>
         *
         * @param columnName  The column name
         * @param columnIndex The column index
         * @param newCells    The new cells
         */
        public CustomColumnAdditionChange(final String columnName, final int columnIndex,
                                          final List<CellAtRow> newCells) {
            super(columnName, columnIndex, newCells);
        }

        /**
         * Gets the cell index of the created column
         *
         * @return The cell index
         */
        public int getCellIndex() {
            if (_newCellIndex < 0)
                throw new IllegalStateException("The cell index has not yet been set.");
            return _newCellIndex;
        }
    }
}
