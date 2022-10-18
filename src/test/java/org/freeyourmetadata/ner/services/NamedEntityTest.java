package org.freeyourmetadata.ner.services;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.net.URI;
import java.net.URISyntaxException;

import org.testng.annotations.Test;

import com.google.refine.model.Cell;
import com.google.refine.model.Recon.Judgment;

public class NamedEntityTest {
    
    @Test
    public void testToCell() throws URISyntaxException {
        Disambiguation[] disambiguations = new Disambiguation[] {
            new Disambiguation("a matching element", new URI("http://foo.com/id1234")),
            new Disambiguation("another one", new URI("http://foo.com/id5678"))
        };
        NamedEntity SUT = new NamedEntity("some text", disambiguations);
        
        Cell cell = SUT.toCell();
        
        assertEquals(cell.value, "some text");
        assertEquals(cell.recon.judgment, Judgment.Matched);
        assertEquals(cell.recon.match.id, "http://foo.com/id1234");
        assertEquals(cell.recon.candidates.get(0).id, "http://foo.com/id1234");
        assertEquals(cell.recon.candidates.get(1).id, "http://foo.com/id5678");
    }
    
    @Test
    public void testToCellNotMatched() throws URISyntaxException {
        Disambiguation[] disambiguations = new Disambiguation[] {
            new Disambiguation("a matching element", new URI("http://foo.com/id1234")),
            new Disambiguation("another one", new URI("http://foo.com/id5678"))
        };
        NamedEntity SUT = new NamedEntity("some text", disambiguations, false);
        
        Cell cell = SUT.toCell();
        
        assertEquals(cell.value, "some text");
        assertEquals(cell.recon.judgment, Judgment.None);
        assertNull(cell.recon.match);
        assertEquals(cell.recon.candidates.get(0).id, "http://foo.com/id1234");
        assertEquals(cell.recon.candidates.get(1).id, "http://foo.com/id5678");
    }

}
