package org.freeyourmetadata.ner.commands;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.freeyourmetadata.ner.services.NERServiceManager;

/**
 * Servlet that provides read/write access to <tt>NERServiceManager</tt>
 *
 * @author Ruben Verborgh
 */
public class ServicesCommand extends NERCommand {
    private final NERServiceManager serviceManager;

    /**
     * Creates a new <tt>ServicesCommand</tt>
     *
     * @param serviceManager The data source
     */
    public ServicesCommand(final NERServiceManager serviceManager) {
        this.serviceManager = serviceManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void get(final HttpServletRequest request, final JsonGenerator writer) throws Exception {
        serviceManager.writeTo(writer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(final HttpServletRequest request, final ArrayNode body, final JsonGenerator writer) throws Exception {
        serviceManager.updateFrom(body);
        serviceManager.save();
    }
}
