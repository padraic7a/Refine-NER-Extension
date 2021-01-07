package org.freeyourmetadata.ner.operations;

import java.util.Properties;
import java.util.SortedMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.freeyourmetadata.ner.services.NERService;
import com.google.refine.model.Column;
import com.google.refine.model.Project;
import com.google.refine.operations.EngineDependentOperation;
import com.google.refine.process.Process;
import com.google.refine.browsing.EngineConfig;

/**
 * Operation that starts a named-entity recognition process
 *
 * @author Ruben Verborgh
 */
public class NEROperation extends EngineDependentOperation {
    private final Column column;
    private final SortedMap<String, NERService> services;
    private final Map<String, Map<String, String>> settings;

    /**
     * Creates a new <tt>NEROperation</tt>
     *
     * @param column       The column on which named-entity recognition is performed
     * @param services     The services that will be used for named-entity recognition
     * @param settings     The settings of the individual services
     * @param engineConfig The faceted browsing engine configuration
     */
    public NEROperation(@JsonProperty("column") Column column, @JsonProperty("services") final SortedMap<String, NERService> services,
                        @JsonProperty("settings") final Map<String, Map<String, String>> settings, @JsonProperty("engineConfig") EngineConfig engineConfig) {
        super(engineConfig);
        this.column = column;
        this.services = services;
        this.settings = settings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getBriefDescription(final Project project) {
        if (project != null) {
            return String.format("Recognize named entities in column %s", column.getName());
        }
        return "Save preferences";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Process createProcess(final Project project, final Properties options) throws Exception {
        return new NERProcess(project, column, services, settings, this, getBriefDescription(project), getEngineConfig());
    }
}
