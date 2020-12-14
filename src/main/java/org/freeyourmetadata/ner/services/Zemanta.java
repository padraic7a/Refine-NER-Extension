package org.freeyourmetadata.ner.services;

import static org.freeyourmetadata.util.UriUtil.createUri;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.freeyourmetadata.util.ParameterList;

/**
 * Zemanta service connector
 *
 * @author Ruben Verborgh
 */
public class Zemanta extends NERServiceBase {
    private final static URI SERVICEBASEURL = createUri("http://papi.zemanta.com/services/rest/0.0/");
    private final static URI DOCUMENTATIONURI = createUri("http://freeyourmetadata.org/named-entity-extraction/zemanta/");
    private final static String[] SERVICESETTINGS = {"API key"};
    private final static String[] EXTRACTIONSETTINGS = {};

    /**
     * Creates a new Zemanta service connector
     */
    public Zemanta() {
        super(SERVICEBASEURL, DOCUMENTATIONURI, SERVICESETTINGS, EXTRACTIONSETTINGS);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isConfigured() {
        return getServiceSetting("API key").length() > 0;
    }

    /**
     * {@inheritDoc}
     */
    protected HttpEntity createExtractionRequestBody(final String text, final Map<String, String> extractionSettings)
            throws UnsupportedEncodingException {
        final ParameterList parameters = new ParameterList();
        parameters.add("method", "zemanta.suggest_markup");
        parameters.add("format", "json");
        parameters.add("return_rdf_links", "1");
        parameters.add("api_key", getServiceSetting("API key"));
        parameters.add("text", text);
        return parameters.toEntity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected NamedEntity[] parseExtractionResponse(final ObjectNode response) throws IOException {
        // Check response status
        final String status = response.get("status").asText();
        if (!"ok".equals(status))
            throw new RuntimeException(status);

        // Get mark-up results
        final ObjectNode markup = (ObjectNode) response.get("markup");
        final ArrayList<NamedEntity> results = new ArrayList<NamedEntity>();
        // In the mark-up results, find the links
        final ArrayNode links = (ArrayNode) markup.get("links");
        for (int i = 0; i < links.size(); i++) {
            // In each link, find the targets
            final ObjectNode link = (ObjectNode) links.get(i);
            final ArrayNode targets = (ArrayNode) link.get("target");
            final String label = targets.get(0).get("title").asText();
            // Make a disambiguation from each target
            final Disambiguation[] disambiguations = new Disambiguation[targets.size()];
            for (int j = 0; j < targets.size(); j++) {
                final ObjectNode target = (ObjectNode) targets.get(j);
                disambiguations[j] = new Disambiguation(target.get("title").asText(), createUri(target.get("url").asText()));
            }
            results.add(new NamedEntity(label, disambiguations));
        }
        return results.toArray(new NamedEntity[results.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Exception parseErrorResponse(final String response) throws Exception {
        return new Exception(response);
    }
}
