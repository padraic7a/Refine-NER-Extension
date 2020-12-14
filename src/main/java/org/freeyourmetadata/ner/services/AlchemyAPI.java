package org.freeyourmetadata.ner.services;

import static org.freeyourmetadata.util.UriUtil.createUri;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.freeyourmetadata.util.ParameterList;

/**
 * Alchemy service connector
 * @author Ruben Verborgh
 */
public class AlchemyAPI extends NERServiceBase {
    private final static URI SERVICEBASEURL = createUri("http://access.alchemyapi.com/calls/text/TextGetRankedNamedEntities?outputMode=json");
    private final static URI DOCUMENTATIONURI = createUri("http://freeyourmetadata.org/named-entity-extraction/alchemyapi/");
    private final static String[] SERVICESETTINGS = { "API key" };
    private final static String[] EXTRACTIONSETTINGS = { };
    private final static HashSet<String> NONURIFIELDS = new HashSet<String>(
            Arrays.asList("subType", "name", "website"));
    
    /**
     * Creates a new Alchemy service connector
     */
    public AlchemyAPI() {
        super(SERVICEBASEURL, DOCUMENTATIONURI, SERVICESETTINGS, EXTRACTIONSETTINGS);
    }
    
    /** {@inheritDoc} */
    public boolean isConfigured() {
        return getServiceSetting("API key").length() > 0;
    }
    
    /** {@inheritDoc} */
    protected HttpEntity createExtractionRequestBody(final String text, final Map<String, String> extractionSettings)
    throws UnsupportedEncodingException {
        final ParameterList parameters = new ParameterList();
        parameters.add("apikey", getServiceSetting("API key"));
        parameters.add("text", text);
        return parameters.toEntity();
    }
    
    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("unchecked")
    protected NamedEntity[] parseExtractionResponse(final ObjectNode response) throws IOException {
        // Check response status
        if (!"OK".equals(response.get("status").asText()))
            throw new RuntimeException(response.get("statusInfo").asText("Extraction failed"));
        
        // Find all entities
        final ArrayNode entities = (ArrayNode) response.get("entities");
        final NamedEntity[] results = new NamedEntity[entities.size()];
        for (int i = 0; i < results.length; i++) {
            final ObjectNode entity = (ObjectNode) entities.get(i);
            final String entityText = entity.get("text").asText();
            
            // Find all disambiguations
            final ArrayList<Disambiguation> disambiguations = new ArrayList<Disambiguation>();
            if (entity.has("disambiguated")) {
                final ObjectNode disambiguated = (ObjectNode) entity.get("disambiguated");
                final String label = disambiguated.get("name").asText();
                final Iterator<String> keyIterator = disambiguated.fieldNames();
                while (keyIterator.hasNext()) {
                    final String key = keyIterator.next();
                    if (!NONURIFIELDS.contains(key))
                        disambiguations.add(new Disambiguation(label, createUri(disambiguated.get(key).asText())));
                }
            }
            // Create new named entity for the result
            results[i] = new NamedEntity(entityText, disambiguations);
        }
        return results;
    }
}
