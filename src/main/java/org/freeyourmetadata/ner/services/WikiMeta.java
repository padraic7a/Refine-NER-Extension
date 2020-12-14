package org.freeyourmetadata.ner.services;

import static org.freeyourmetadata.util.UriUtil.createUri;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.freeyourmetadata.util.ParameterList;

/**
 * WikiMeta service connector
 * <p>
 * Parameters:
 * <ul>
 * 	<li>span=[0-n]: the amount of word context used for disambiguation</li>
 * 	<li>lng=[fr|en|es]: the language model (French, English or Spanish)</li>
 * 	<li>semtag=[0|1]: activates semantic labeling</li>
 * </ul>
 *
 * @author Kevin Van Raepenbusch
 */
public class WikiMeta extends NERServiceBase {
    private final static URI SERVICEBASEURL = createUri("http://www.wikimeta.com/wapi/service");
    private final static URI DOCUMENTATIONURI = createUri("http://www.wikimeta.com/api.html");
    private final static String[] SERVICESETTINGS = {"API key"};
    private final static String[] EXTRACTIONSETTINGS = {"Language", "Span", "Treshold"};

    /**
     * Creates a new WikiMeta service connector
     */
    public WikiMeta() {
        super(SERVICEBASEURL, DOCUMENTATIONURI, SERVICESETTINGS, EXTRACTIONSETTINGS);
        setExtractionSettingDefault("Treshold", "10");
        setExtractionSettingDefault("Span", "100");
        setExtractionSettingDefault("Language", "en");
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
        parameters.add("api", getServiceSetting("API key"));
        parameters.add("contenu", text);
        parameters.add("treshold", extractionSettings.get("Treshold"));
        parameters.add("span", extractionSettings.get("Span"));
        parameters.add("lng", extractionSettings.get("Language").toUpperCase());
        parameters.add("semtag", "1");
        return parameters.toEntity();
    }

    /**
     * Parses the named-entity recognition response
     *
     * @param response A response of the named-entity extraction service
     * @return The extracted named entities
     * @throws IOException if the response cannot be parsed
     */
    protected NamedEntity[] parseExtractionResponse(final HttpResponse response) throws IOException {
        final String body = EntityUtils.toString(response.getEntity());

        // An invalid response is recognized by invalid JSON

        ObjectMapper mapper = new ObjectMapper();
        final ObjectNode bodyJson;
        try {
            bodyJson = (ObjectNode) mapper.readTree(body);
        } catch (IOException error) {
            throw new IOException(body);
        }

        return parseExtractionResponse(bodyJson);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected NamedEntity[] parseExtractionResponse(final ObjectNode response) throws IOException {
        final ArrayNode document = (ArrayNode) response.get("document");

        // Find all entities
        final ArrayNode entities = (ArrayNode) document.get(2).get("Named Entities");
        final NamedEntity[] results = new NamedEntity[entities.size()];
        for (int i = 0; i < entities.size(); i++) {
            final ObjectNode entity = (ObjectNode) entities.get(i);
            final String entityText = entity.get("EN").asText();
            final String scoreString = entity.get("confidenceScore").asText();
            final double score = scoreString.length() == 0 ? 1.0 : Double.parseDouble(scoreString);
            // First try the "Linked Data" URI, otherwise just the URI
            final String linkedDataUri = entity.get("LINKEDDATA").asText();
            final String uri = !linkedDataUri.equals("null") ? linkedDataUri : entity.get("URI").asText();
            results[i] = new NamedEntity(entityText, createUri(uri.equals("NORDF") ? "" : uri), score);
        }
        return results;
    }
}