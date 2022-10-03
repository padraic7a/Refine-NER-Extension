package org.freeyourmetadata.ner.services;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.system.ErrorHandlerFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.freeyourmetadata.util.UriUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.*;

public class NIFService implements NERService {

    public static final String RDF_PREFIX = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String RDFS_PREFIX = "http://www.w3.org/2000/01/rdf-schema#";
    public static final String NIF_PREFIX = "http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#";
    public static final String ITSRDF_PREFIX = "http://www.w3.org/2005/11/its/rdf#";

    public static final String NIF_RFC5147STRING = NIF_PREFIX + "RFC5147String";
    public static final String NIF_STRING = NIF_PREFIX + "String";
    public static final String NIF_CONTEXT = NIF_PREFIX + "Context";

    public static final Property NIF_ANCHOR_OF = ResourceFactory.createProperty(NIF_PREFIX, "anchorOf");
    public static final Property NIF_BEGIN_INDEX = ResourceFactory.createProperty(NIF_PREFIX, "beginIndex");
    public static final Property NIF_END_INDEX = ResourceFactory.createProperty(NIF_PREFIX, "endIndex");
    public static final Property NIF_IS_STRING = ResourceFactory.createProperty(NIF_PREFIX, "isString");
    public static final Property ITSRDF_TA_IDENTREF = ResourceFactory.createProperty(ITSRDF_PREFIX, "taIdentRef");
    public static final Property ITSRDF_TA_CONFIDENCE = ResourceFactory.createProperty(ITSRDF_PREFIX, "taConfidence");


    private static final String documentURI = "http://localhost/document/query";

    protected final HttpClient httpClient = HttpClientBuilder.create().build();

    protected Map<String, String> settings;

    public NIFService() {
        this.settings = new HashMap<>();
        settings.put("endpoint", "");
    }

    public NIFService(URI endpoint) {
        this();
        settings.put("endpoint", endpoint.toString());
    }

    @Override
    public NamedEntity[] extractNamedEntities(String text, Map<String, String> annotationSettings) throws Exception {
        // Construct NIF document corresponding to the text
        String nifDocument = createNIFDocument(text);

        // Prepare the query
        URI endpoint = new URI(settings.get("endpoint"));
        HttpPost request = new HttpPost(endpoint);
        request.setHeader("Accept", "application/turtle");
        request.setHeader("Content-Type", "application/turtle");
        request.setHeader("User-Agent", "Refine NER Extension");
        HttpEntity body = new StringEntity(nifDocument);
        request.setEntity(body);
        // Execute the request
        HttpResponse response = httpClient.execute(request);

        if (response.getStatusLine().getStatusCode() >= 300) {
            throw new IOException(response.getStatusLine().getReasonPhrase());
        }

        // Read the response
        String responseString = EntityUtils.toString(response.getEntity());
        return parseResponse(text, responseString);
    }

    /**
     * Creates a NIF document, to be submitted to the NIF service via HTTP, for annotation.
     *
     * @param text the text to annotate
     * @return the Turtle encoding of the request
     */
    protected static String createNIFDocument(String text) {
        Model model = ModelFactory.createDefaultModel();
        Map<String, String> prefixMap = new HashMap<>();
        prefixMap.put("nif", NIF_PREFIX);
        prefixMap.put("itsrdf", ITSRDF_PREFIX);
        prefixMap.put("rdf", RDF_PREFIX);
        prefixMap.put("xsd", XSD.getURI());
        model.setNsPrefixes(prefixMap);

        Resource document = model.createResource(documentURI);
        document.addProperty(RDF.type, model.createResource(NIF_RFC5147STRING));
        document.addProperty(RDF.type, model.createResource(NIF_STRING));
        document.addProperty(RDF.type, model.createResource(NIF_CONTEXT));
        document.addProperty(NIF_BEGIN_INDEX,
                ResourceFactory.createTypedLiteral(Integer.toString(0), XSDDatatype.XSDnonNegativeInteger));
        document.addProperty(NIF_END_INDEX,
                ResourceFactory.createTypedLiteral(Integer.toString(text.length()), XSDDatatype.XSDnonNegativeInteger));
        document.addProperty(NIF_IS_STRING, text);
        StringWriter stringWriter = new StringWriter();
        RDFDataMgr.write(stringWriter, model, Lang.TURTLE);
        return stringWriter.toString();
    }

    /**
     * Parses the Turtle response of the NIF service, to extract the list of named entities.
     *
     * @param originalText the text to annotate
     * @param turtle the turtle response from the service
     * @return the list of named entities
     */
    protected static NamedEntity[] parseResponse(String originalText, String turtle) {
        InputStream inputStream = IOUtils.toInputStream(turtle, Charset.defaultCharset());
        Dataset dataset = RDFParser.create()
                .source(inputStream)
                .lang(RDFLanguages.TURTLE)
                .errorHandler(ErrorHandlerFactory.errorHandlerNoWarnings)
                .toDataset();
        // TODO not sure how to detect parsing errors of the turtle response?
        Model model = dataset.getDefaultModel();

        // iterate over annotations
        ResIterator iter = model.listSubjectsWithProperty(ITSRDF_TA_IDENTREF);
        List<NamedEntity> namedEntities = new ArrayList<>();
        Map<Phrase, List<Disambiguation>> disambiguations = new HashMap<>();
        while(iter.hasNext()) {
            Resource resource = iter.nextResource();
            double score = 1.0;
            URI uri = UriUtil.EMPTYURI;

            // Find location
            int beginIndex = resource.getProperty(NIF_BEGIN_INDEX).getInt();
            int endIndex = resource.getProperty(NIF_END_INDEX).getInt();

            // Find extracted text
            String extractedText = "";
            if (resource.hasProperty(NIF_ANCHOR_OF)) {
                extractedText = resource.getProperty(NIF_ANCHOR_OF).getString();
            } else if (resource.hasProperty(NIF_BEGIN_INDEX) && resource.hasProperty(NIF_END_INDEX)) {
                extractedText = originalText.substring(beginIndex, endIndex);
            }

            // Find URI and label
            String entityLabel = "";
            try {
                RDFNode object = resource.getProperty(ITSRDF_TA_IDENTREF).getObject();
                if (object.isResource()) {
                    uri = new URI(object.asResource().getURI());
                    Statement statement = model.getProperty(object.asResource(), RDFS.label);
                    if (statement != null && statement.getObject().isLiteral()) {
                        entityLabel = statement.getObject().asLiteral().getString();
                    } else {
                        // fallback strategy to create labels for resources which do not have labels provided
                        String[] parts = object.asResource().getURI().split("/");
                        entityLabel = parts[parts.length-1];
                    }
                } else {
                    continue;
                }
            } catch (URISyntaxException e) {
                // cannot happen since we know from Jena that this is a URI
            }

            // Find score
            if (resource.hasProperty(ITSRDF_TA_CONFIDENCE)) {
                score = resource.getProperty(ITSRDF_TA_CONFIDENCE).getDouble();
            }

            // compute key for aggregation of disambiguations:
            // all disambiguations covering the same text will be grouped together
            // so we compute an integer which represents both the start and end position
            Phrase phrase = new Phrase(beginIndex, endIndex, extractedText);
            List<Disambiguation> currentList = disambiguations.getOrDefault(phrase, new ArrayList<>());
            currentList.add(new Disambiguation(entityLabel, uri, score));
            disambiguations.put(phrase, currentList);
        }

        NamedEntity[] entities = new NamedEntity[disambiguations.size()];
        int index = 0;
        for (Map.Entry<Phrase, List<Disambiguation>> entry : disambiguations.entrySet()) {
            List<Disambiguation> localDisambiguations = entry.getValue();
            // sort disambiguations by decreasing score
            localDisambiguations.sort(new Comparator<Disambiguation>() {
                @Override
                public int compare(Disambiguation a, Disambiguation b) {
                    return Double.compare(b.getScore(), a.getScore());
                }
            });
            Disambiguation[] disambiguationArray = localDisambiguations.toArray(new Disambiguation[localDisambiguations.size()]);
            entities[index] = new NamedEntity(entry.getKey().extractedText, disambiguationArray);
            index++;
        }
        return entities;
    }

    @Override
    public Set<String> getServiceSettings() {
        return settings.keySet();
    }

    @Override
    public String getServiceSetting(String name) {
        return settings.get(name);
    }

    @Override
    public void setServiceSetting(String name, String value) {
        if (!settings.containsKey(name))
            throw new IllegalArgumentException("The service setting " + name
                    + " is invalid for " + getClass().getName() + ".");
        settings.put(name, value == null ? "" : value);settings.put(name, value);
    }

    @Override
    public Set<String> getExtractionSettings() {
        return Collections.emptySet();
    }

    @Override
    public String getExtractionSettingDefault(String name) {
        return null;
    }

    @Override
    public void setExtractionSettingDefault(String name, String value) {

    }

    @Override
    public boolean isConfigured() {
        return !settings.get("endpoint").isEmpty();
    }

    @Override
    public URI getDocumentationUri() {
        return null;
    }

    protected static class Phrase {
        final int x;
        final int y;
        final String extractedText;

        Phrase(int x, int y, String text) {
            this.x = x;
            this.y = y;
            this.extractedText = text;
        }

        @Override
        public String toString() {
            return "Phrase{" +
                    "x=" + x +
                    ", y=" + y +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Phrase phrase = (Phrase) o;
            return x == phrase.x &&
                    y == phrase.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }
}
