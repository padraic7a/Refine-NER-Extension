package org.freeyourmetadata.ner.services;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;


public class NIFServiceTest {

    String exampleText = "Konarka Technologies, 116 John St., Suite 12, Lowell, MA 01852, USA";

    String nifRequest = "@prefix itsrdf: <http://www.w3.org/2005/11/its/rdf#> .\n" +
            "@prefix nif:    <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#> .\n" +
            "@prefix rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" +
            "@prefix xsd:    <http://www.w3.org/2001/XMLSchema#> .\n" +
            "\n" +
            "<http://localhost/document/query>\n" +
            "        rdf:type        nif:Context , nif:String , nif:RFC5147String ;\n" +
            "        nif:beginIndex  \"0\"^^xsd:nonNegativeInteger ;\n" +
            "        nif:endIndex    \"67\"^^xsd:nonNegativeInteger ;\n" +
            "        nif:isString    \"Konarka Technologies, 116 John St., Suite 12, Lowell, MA 01852, USA\" .\n";

    String nifResponse = "" +
            "@prefix itsrdf: <http://www.w3.org/2005/11/its/rdf#> .\n" +
            "@prefix nif: <http://persistence.uni-leipzig.org/nlp2rdf/ontologies/nif-core#> .\n" +
            "@prefix ns1: <http://purl.org/dc/terms/> .\n" +
            "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" +
            "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" +
            "@prefix xml: <http://www.w3.org/XML/1998/namespace> .\n" +
            "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> ." +
            "\n" +
            "<http://localhost/document/query> a nif:Context,\n" +
            "      nif:OffsetBasedString ;\n" +
            "   nif:beginIndex \"0\"^^xsd:nonNegativeInteger ;\n" +
            "   nif:endIndex \"67\"^^xsd:nonNegativeInteger ;\n" +
            "   nif:isString \"Konarka Technologies, 116 John St., Suite 12, Lowell, MA 01852, USA\" ;\n" +
            "   nif:sourceUrl <https://doi.org/10.1002/aenm.201100390> .\n" +
            "\n" +
            "<http://localhost/document/query#offset_54_56> a nif:OffsetBasedString,\n" +
            "      nif:Phrase ;\n" +
            "   nif:anchorOf \"MA\" ;\n" +
            "   nif:beginIndex \"54\"^^xsd:nonNegativeInteger ;\n" +
            "   nif:endIndex \"56\"^^xsd:nonNegativeInteger ;\n" +
            "   nif:referenceContext <http://localhost/document/query> ;\n" +
            "   itsrdf:taIdentRef <http://www.wikidata.org/entity/Q771> .\n" +
            "\n" +
            "<http://localhost/document/query#offset_64_67> a nif:OffsetBasedString,\n" +
            "      nif:Phrase ;\n" +
            "   nif:anchorOf \"USA\" ;\n" +
            "   nif:beginIndex \"64\"^^xsd:nonNegativeInteger ;\n" +
            "   nif:endIndex \"67\"^^xsd:nonNegativeInteger ;\n" +
            "   nif:referenceContext <http://localhost/document/query> ;\n" +
            "   itsrdf:taIdentRef <http://www.wikidata.org/entity/Q30> .\n" +
            "\n" +
            "<http://www.wikidata.org/entity/Q771> rdfs:label \"Massachusetts\"@en.\n";

    NamedEntity[] namedEntities;

    @BeforeClass
    public void setUpEntities() throws URISyntaxException {
        namedEntities = new NamedEntity[]{
                new NamedEntity("MA", new Disambiguation[]{
                        new Disambiguation("Massachusetts", new URI("http://www.wikidata.org/entity/Q771"))
                }),
                new NamedEntity("USA", new Disambiguation[]{
                        new Disambiguation("Q30", new URI("http://www.wikidata.org/entity/Q30"))
                })
        };
    }

    @Test
    public void testGenerateNifRequest() {
        String nif = NIFService.createNIFDocument(exampleText);

        Assert.assertEquals(nif, nifRequest);
    }

    @Test
    public void testParseNifResponse() throws URISyntaxException {
        NamedEntity[] entities = NIFService.parseResponse(exampleText, nifResponse);

        Assert.assertEquals(entities, namedEntities);
    }

    @Test
    public void testEndToEnd() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String url = server.url("/endpoint").toString();
            server.enqueue(new MockResponse().setBody(nifResponse));

            NIFService service = new NIFService(new URI(url));
            NamedEntity[] entities = service.extractNamedEntities(exampleText, Collections.emptyMap());

            Assert.assertEquals(entities, namedEntities);
        }
    }
}
