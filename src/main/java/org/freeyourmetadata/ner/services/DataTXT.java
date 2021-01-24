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
 * Dandelion Entity Extraction API service connector
 *
 * @author Stefano Parmesan
 * @author Giuliano Tortoreto
 */
public class DataTXT extends NERServiceBase {
    private final static URI SERVICEBASEURL = createUri("https://api.dandelion.eu/datatxt/nex/v1");
    private final static URI DOCUMENTATIONURI = createUri("https://dandelion.eu/docs/api/datatxt/nex/v1/");
    private final static String[] SERVICESETTINGS = {"Token"};
    private final static String[] EXTRACTIONSETTINGS = {"Language", "Confidence", "Min length"};

    /**
     * Creates a new dataTXT service connector
     */
    public DataTXT() {
        super(SERVICEBASEURL, DOCUMENTATIONURI, SERVICESETTINGS, EXTRACTIONSETTINGS);
        setExtractionSettingDefault("Language", "auto");
        setExtractionSettingDefault("Confidence", "0.6");
        setExtractionSettingDefault("Min length", "2");
    }

    /**
     * {@inheritDoc}
     */
    public boolean isConfigured() {
        return getServiceSetting("Token").length() > 0;
    }

    /**
     * {@inheritDoc}
     */
    protected HttpEntity createExtractionRequestBody(final String text, final Map<String, String> extractionSettings)
            throws UnsupportedEncodingException {
        final ParameterList parameters = new ParameterList();
        parameters.add("lang", extractionSettings.get("Language"));
        parameters.add("text", text);
        parameters.add("min_confidence", extractionSettings.get("Confidence"));
        parameters.add("min_length", extractionSettings.get("Min length"));
        parameters.add("token", getServiceSetting("Token"));
        return parameters.toEntity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected NamedEntity[] parseExtractionResponse(final ObjectNode response) throws Exception {
        // Find all annotations
        final ArrayNode annotations = (ArrayNode) response.get("annotations");
        final NamedEntity[] results = new NamedEntity[annotations.size()];
        for (int i = 0; i < results.length; i++) {
            final ObjectNode annotation = (ObjectNode) annotations.get(i);
            final String label = annotation.get("title").asText();
            final double score = annotation.get("confidence").asDouble();
            final ArrayList<Disambiguation> disambiguations = new ArrayList<>();

            disambiguations.add(new Disambiguation(label, createUri(annotation.get("uri").asText()), score));
            results[i] = new NamedEntity(annotation.get("spot").asText(), disambiguations);
        }

        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Exception parseErrorResponse(final ObjectNode response) throws IOException {
        return response.has("message") ? new Exception(response.get("message").asText()) : null;
    }
}
