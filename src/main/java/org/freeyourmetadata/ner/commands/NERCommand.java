package org.freeyourmetadata.ner.commands;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.refine.commands.Command;
import com.google.refine.util.ParsingUtilities;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;

/**
 * Base class for JSON-based commands
 *
 * @author Ruben Verborgh
 */
public abstract class NERCommand extends Command {
    /**
     * {@inheritDoc}
     */
    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        final JsonGenerator writer = createResponseWriter(response);
        try {
            get(request, writer);
        } catch (Exception error) {
            error.printStackTrace();
            throw new ServletException(error);
        } finally {
            writer.flush();
            writer.close();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doPut(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        final JsonGenerator writer = createResponseWriter(response);
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            put(request, (ArrayNode) objectMapper.readTree(request.getReader()), writer);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Creates a JSON response writer and sets the content-type accordingly
     *
     * @param response The response
     * @return The response writer
     * @throws IOException
     */
    protected JsonGenerator createResponseWriter(final HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        Writer w = response.getWriter();
        return ParsingUtilities.mapper.getFactory().createGenerator(w);
    }

    /**
     * Handles a <tt>GET</tt> request
     *
     * @param request The request
     * @param writer  The response writer
     * @throws Exception if something goes wrong
     */
    public void get(final HttpServletRequest request, final JsonGenerator writer) throws Exception {
    }

    /**
     * Handles a <tt>PUT</tt> request
     *
     * @param request The request
     * @param json    The parsed JSON object from the request body
     * @param writer  The response writer
     * @throws Exception if something goes wrong
     */
    public void put(final HttpServletRequest request, final ArrayNode json, final JsonGenerator writer) throws Exception {
    }
}
