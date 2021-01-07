package org.freeyourmetadata.ner.services;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.refine.util.ParsingUtilities;
import org.apache.log4j.Logger;

import com.google.refine.RefineServlet;
import com.google.refine.util.JSONUtilities;

/**
 * Manager that reads and stores service configurations in JSON
 *
 * @author Ruben Verborgh
 */
public class NERServiceManager {
    private final static Logger LOGGER = Logger.getLogger(NERServiceManager.class);
    private final static File CACHEFOLDER = new RefineServlet().getCacheDir("ner-extension");

    private final TreeMap<String, NERService> services;
    private final File settingsFile;

    /**
     * Creates a new <tt>NERServiceManager</tt>
     *
     * @param settingsFile JSON file to read and store settings (might not exist yet)
     * @throws IOException            if the settings file cannot be read
     * @throws ClassNotFoundException if a service cannot be instantiated
     */
    public NERServiceManager(final File settingsFile) throws IOException, ClassNotFoundException {
        this.settingsFile = settingsFile;
        services = new TreeMap<>();

        // First load the default settings,
        // so new services are automatically instantiated
        updateFrom(new InputStreamReader(getClass().getResourceAsStream("DefaultServices.json")));
        // Then, load the user's settings from the specified file (if it exists)
        if (settingsFile.exists()) {
            updateFrom(new FileReader(settingsFile));
        }
    }

    /**
     * Creates a new <tt>NERServiceManager</tt> with the default settings file location
     *
     * @throws IOException            if the settings file cannot be read
     * @throws ClassNotFoundException if a service cannot be instantiated
     */
    public NERServiceManager() throws IOException, ClassNotFoundException {
        this(new File(CACHEFOLDER, "services.json"));
    }

    /**
     * Returns whether the manager contains the specified service
     *
     * @param serviceName The name of the service
     * @return <tt>true</tt> if the manager contains the service
     */
    public boolean hasService(final String serviceName) {
        return services.containsKey(serviceName);
    }

    /**
     * Adds the service to the manager
     *
     * @param name    The name of the service
     * @param service The service
     */
    public void addService(final String name, final NERService service) {
        services.put(name, service);
    }

    /**
     * Gets the specified service
     *
     * @param name The name of the service
     * @return The service
     */
    public NERService getService(final String name) {
        if (!services.containsKey(name))
            throw new IllegalArgumentException("No service named " + name + " exists.");
        return services.get(name);
    }

    /**
     * Gets the service if it exists, or creates one otherwise and adds it to the manager
     *
     * @param serviceName The name of the service
     * @param className   The class name of the service to instantiate
     * @return The service
     * @throws ClassNotFoundException if the service cannot be instantiated
     */
    protected NERService getOrCreateService(final String serviceName, final String className) throws ClassNotFoundException {
        final NERService service;
        // Return the service if it exists
        if (hasService(serviceName)) {
            service = getService(serviceName);
        }
        // Create a new service otherwise
        else {
            // Create the service through reflection
            final Class<?> serviceClass = getClass().getClassLoader().loadClass(className);
            try {
                service = (NERService) serviceClass.newInstance();
            }
            // We assume instantiation and access are possible
            catch (InstantiationException | IllegalAccessException error) {
                throw new RuntimeException(error);
            }

            // Add the newly created service
            addService(serviceName, service);
        }
        return service;
    }

    /**
     * Gets the names of all services in the manager
     *
     * @return The services names
     */
    public String[] getServiceNames() {
        return services.keySet().toArray(new String[services.size()]);
    }

    /**
     * Saves the configuration to the settings file
     *
     * @throws IOException if the file cannot be written
     */
    public void save() throws IOException {
        JsonGenerator output = ParsingUtilities.mapper.getFactory().createGenerator(settingsFile, JsonEncoding.UTF8);
        writeTo(output);
        output.close();
    }

    /**
     * Writes the configuration to the specified writer
     *
     * @param output The writer
     */
    public void writeTo(JsonGenerator output) {
        try {
            /* Array of services */
            output.writeStartArray();
            for (final String serviceName : getServiceNames()) {
                final NERService service = getService(serviceName);
                /* Service object */
                output.writeStartObject();
                {
                    output.writeStringField("name", serviceName);
                    output.writeStringField("class", service.getClass().getName());
                    output.writeBooleanField("configured", service.isConfigured());
                    output.writeStringField("documentation", service.getDocumentationUri() != null ? service.getDocumentationUri().toString() : "");

                    /* Service settings object */
                    output.writeFieldName("settings");
                    output.writeStartObject();
                    for (final String settingName : service.getServiceSettings()) {
                        output.writeStringField(settingName, service.getServiceSetting(settingName));
                    }
                    output.writeEndObject();

                    /* Extraction settings object */
                    output.writeFieldName("extractionSettings");
                    output.writeStartObject();
                    for (final String settingName : service.getExtractionSettings()) {
                        output.writeStringField(settingName, service.getExtractionSettingDefault(settingName));
                    }
                    output.writeEndObject();
                }
                output.writeEndObject();
            }
            output.writeEndArray();
        } catch (IOException e) { /* does not happen */ }
    }

    /**
     * Updates the manager's configuration from the JSON array
     *
     * @param serviceValues array of service settings
     */
    @SuppressWarnings("unchecked")
    public void updateFrom(final ArrayNode serviceValues) {
        /* Array of services */
        final Object[] services = JSONUtilities.toArray(serviceValues);
        for (final Object value : services) {
            /* Service object */
            if (!(value instanceof ObjectNode))
                throw new IllegalArgumentException("Value should be an array of JSON objects.");
            final ObjectNode serviceValue = (ObjectNode) value;
            try {
                final NERService service = getOrCreateService(serviceValue.get("name").asText(),
                        serviceValue.get("class").asText());
                /* Service settings object */
                if (serviceValue.has("settings")) {
                    final ObjectNode settings = (ObjectNode) serviceValue.get("settings");
                    final Iterator<String> settingNames = settings.fieldNames();
                    while (settingNames.hasNext()) {
                        final String settingName = settingNames.next();
                        if (service.getServiceSettings().contains(settingName))
                            service.setServiceSetting(settingName, settings.get(settingName).asText());
                    }
                }
            } catch (ClassNotFoundException e) {
                LOGGER.error(String.format("Could not find NER service with class %s.",
                        serviceValue.get("class").asText()));
            }
        }
    }

    /**
     * Updates the manager's configuration from the reader
     *
     * @param serviceValuesReader reader of service settings
     * @throws ClassNotFoundException if a service cannot be instantiated
     */
    public void updateFrom(final Reader serviceValuesReader) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            updateFrom((ArrayNode) objectMapper.readTree(serviceValuesReader));
            serviceValuesReader.close();
        } catch (IOException ignored) {
        }
    }
}
