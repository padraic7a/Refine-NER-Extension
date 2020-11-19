package org.freeyourmetadata.ner.services;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;

import static org.freeyourmetadata.util.UriUtil.createUri;

/**
 * DBpedia spotlight service connector
 * @author Ruben Verborgh
 */
public class StanfordNLP extends NERServiceBase implements NERService {
    private final static URI SERVICEBASEURL = createUri("http://localhost:9000");
    private final static URI DOCUMENTATIONURI = createUri("https://stanfordnlp.github.io/CoreNLP/ner.html");
    private final static String[] SERVICESETTINGS = { "NLP Service URL"};
    private final static String[] EXTRACTIONSETTINGS = { "applyNumericClassifiers", "applyFineGrained" };

    /**
     * Creates a new Stanford NLP service connector
     */
    public StanfordNLP() {
        super(SERVICEBASEURL, null, SERVICESETTINGS, EXTRACTIONSETTINGS);
        setExtractionSettingDefault("applyNumericClassifiers", "false");
        setExtractionSettingDefault("applyFineGrained", "true");
    }

    /** {@inheritDoc} */
    public boolean isConfigured() {
        return getServiceSetting("NLP Service URL").length() > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected HttpUriRequest createExtractionRequest(final String text, final Map<String, String> settings) throws Exception {
        final URI requestUrl = createExtractionRequestUrl(text, settings);
        final HttpEntity body = createExtractionRequestBody(text, settings);
        final HttpPost request = new HttpPost(requestUrl);
        request.setHeader("Accept", "application/json");
        request.setHeader("User-Agent", "Refine NER Extension");
        request.setEntity(body);
        return request;
    }

    /**
     * {@inheritDoc}
     */
    protected HttpEntity createExtractionRequestBody(final String text, final Map<String, String> extractionSettings)
            throws UnsupportedEncodingException {
        return new StringEntity(text);
    }

    /**
     * Parses the named-entity recognition response
     * @param response A response of the named-entity extraction service
     * @return The extracted named entities
     * @throws Exception if the response cannot be parsed
     */
    protected NamedEntity[] parseExtractionResponse(final HttpResponse response) throws Exception {
        final String body = EntityUtils.toString(response.getEntity());

        // An invalid response is recognized by invalid JSON
        final JSONObject bodyJson;
        try { bodyJson = new JSONObject(body); }
        catch (JSONException error) { throw new Exception(body); }

        return parseExtractionResponse(bodyJson);
    }


    /**
     * {@inheritDoc}
     */
    protected URI createExtractionRequestUrl(final String text, final Map<String, String> extractionSettings) {
        try {
            URIBuilder builder = new URIBuilder(getServiceSetting("NLP Service URL"));
            builder.addParameter("properties", "{\"annotators\":\"ner\","
                    + "\"ner.applyNumericClassifiers\":\""
                    + extractionSettings.get("applyNumericClassifiers") + "\","
                    + "\"ner.applyFineGrained\":\"" + extractionSettings.get("applyFineGrained") + "\"}"
            );

            return builder.build();
        } catch (java.net.URISyntaxException e) {
            return createUri(getServiceSetting("NLP Service URL"));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected NamedEntity[] parseExtractionResponse(final JSONObject response) throws JSONException {
        // Empty result if no resources were found
        if (!response.has("sentences"))
            return EMPTY_EXTRACTION_RESULT;
        // Extract resources
        final JSONArray sentences = response.getJSONArray("sentences");
        if (sentences.length() == 0)
            return EMPTY_EXTRACTION_RESULT;

        ArrayList<NamedEntity> results = new ArrayList();

        for (int i = 0; i < sentences.length(); i++) {
            final JSONObject sentence = sentences.getJSONObject(i);
            final JSONArray entityMentions = sentence.getJSONArray("entitymentions");
            for(int j = 0; j < entityMentions.length(); j++) {
                    final JSONObject entityMention = entityMentions.getJSONObject(j);
                    results.add(new NamedEntity(entityMention.getString("text"),
                            createUri("")));
                }
        }

        return results.toArray(new NamedEntity[results.size()]);
    }
}
